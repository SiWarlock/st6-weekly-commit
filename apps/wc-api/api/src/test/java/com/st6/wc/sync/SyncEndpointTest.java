package com.st6.wc.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.st6.wc.audit.repo.AuditEventRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.EventKind;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.SyncRelatedType;
import com.st6.wc.enums.SyncStatus;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.support.AbstractAppBootTest;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * {@code GET /api/outlook-sync?planId=} (E22) + {@code POST /api/outlook-sync/{id}/retry} (E23)
 * end-to-end through the demo-mode 2.6 chain (§5/§10 / §6 rule #3 / rules #4/#7) against real PG16
 * ({@link AbstractAppBootTest}). The FIRST production caller of {@code authorizeSyncRecordAccess}.
 * Proves: the E22 bare array (not paginated), the {@code RETRY_SYNC} affordance iff {@code FAILED},
 * no entity leak; the E23 retry transitions {@code FAILED → RETRY_REQUESTED} (response) and the
 * reused afterCommit publisher advances the DB to {@code QUEUED}; non-{@code FAILED} → {@code 409
 * SYNC_NOT_RETRYABLE}; the IDOR matrix (cross-owner list 404, manager-of-owner retry allowed,
 * stranger retry → 404 + denial audit); both mappings reachable.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class SyncEndpointTest extends AbstractAppBootTest {

  private static final String HEADER = "X-Demo-Employee-Id";
  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);

  @Autowired private MockMvc mvc;
  @Autowired private EmployeeRepository employees;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private OutlookCalendarSyncRecordRepository syncRecords;
  @Autowired private AuditEventRepository auditEvents;
  @Autowired private ManagerRelationshipRepository relationships;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  @AfterEach
  void cleanup() {
    syncRecords.deleteAll();
    auditEvents.deleteAll();
    relationships.deleteAll();
    plans.deleteAll();
    employees.deleteAll();
  }

  private Employee saveIc(String email) {
    return saveEmployee(email, RoleType.IC);
  }

  private Employee saveManager(String email) {
    return saveEmployee(email, RoleType.MANAGER);
  }

  private Employee saveEmployee(String email, RoleType role) {
    Employee e = new Employee();
    e.setId(UUID.randomUUID());
    e.setEmail(email);
    e.setDisplayName("Name " + email);
    e.setRole(role);
    e.setActive(true);
    return employees.saveAndFlush(e);
  }

  private WeeklyPlan savePlan(UUID ownerId, PlanState state) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(ownerId);
    p.setWeekStartDate(WEEK);
    p.setWeekEndDate(WEEK.plusDays(6));
    p.setState(state);
    return plans.saveAndFlush(p);
  }

  private void saveActiveRelationship(UUID managerId, UUID reportId) {
    ManagerRelationship r = new ManagerRelationship();
    r.setId(UUID.randomUUID());
    r.setManagerEmployeeId(managerId);
    r.setDirectReportEmployeeId(reportId);
    r.setActive(true);
    relationships.saveAndFlush(r);
  }

  private OutlookCalendarSyncRecord saveSyncRecord(
      UUID ownerId, UUID planId, EventKind kind, SyncStatus status) {
    OutlookCalendarSyncRecord r = new OutlookCalendarSyncRecord();
    r.setId(UUID.randomUUID());
    r.setOwnerEmployeeId(ownerId);
    r.setRelatedType(SyncRelatedType.WEEKLY_PLAN);
    r.setRelatedId(planId);
    r.setEventKind(kind);
    r.setStatus(status);
    r.setRetryCount(status == SyncStatus.FAILED ? 1 : 0);
    if (status == SyncStatus.FAILED) {
      r.setFailureCode("GRAPH_FORBIDDEN");
      r.setSafeMessage("Calendar sync failed; you can retry.");
    }
    r.setWeekStartDate(WEEK);
    r.setTraceId("trace-1");
    r.setQueuedAt(Instant.parse("2026-06-04T01:00:00Z")); // a leak-test internal
    r.setLastAttemptAt(Instant.parse("2026-06-04T02:00:00Z"));
    return syncRecords.saveAndFlush(r);
  }

  // ===== E22 list =====

  // --- #1: E22 returns a BARE ARRAY of the plan's sync records, eventKind-ordered ----------------
  @Test
  void list_returnsPlanSyncRecords_asBareArray() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    saveSyncRecord(ic.getId(), plan.getId(), EventKind.IC_RECONCILIATION, SyncStatus.QUEUED);
    saveSyncRecord(ic.getId(), plan.getId(), EventKind.IC_PLANNING, SyncStatus.SYNCED);

    mvc.perform(
            get("/api/outlook-sync")
                .header(HEADER, ic.getId().toString())
                .param("planId", plan.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].eventKind").value("IC_PLANNING")) // eventKind ASC
        .andExpect(jsonPath("$[1].eventKind").value("IC_RECONCILIATION"))
        .andExpect(jsonPath("$[0].relatedType").value("WEEKLY_PLAN"))
        .andExpect(jsonPath("$[0].relatedId").value(plan.getId().toString()));
  }

  // --- #2: RETRY_SYNC affordance emitted iff status == FAILED ------------------------------------
  @Test
  void list_emitsRetrySyncAffordance_onlyOnFailed() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    saveSyncRecord(ic.getId(), plan.getId(), EventKind.IC_PLANNING, SyncStatus.FAILED);
    saveSyncRecord(ic.getId(), plan.getId(), EventKind.IC_RECONCILIATION, SyncStatus.SYNCED);

    mvc.perform(
            get("/api/outlook-sync")
                .header(HEADER, ic.getId().toString())
                .param("planId", plan.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("FAILED"))
        .andExpect(jsonPath("$[0].allowedActions.length()").value(1))
        .andExpect(jsonPath("$[0].allowedActions[0]").value("RETRY_SYNC"))
        .andExpect(jsonPath("$[1].status").value("SYNCED"))
        .andExpect(jsonPath("$[1].allowedActions").isEmpty());
  }

  // --- #3: the DTO never leaks the entity's internal timestamps / audit fields ------------------
  @Test
  void syncRecordDto_noEntityLeak() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    saveSyncRecord(ic.getId(), plan.getId(), EventKind.IC_PLANNING, SyncStatus.FAILED);

    mvc.perform(
            get("/api/outlook-sync")
                .header(HEADER, ic.getId().toString())
                .param("planId", plan.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").exists())
        .andExpect(jsonPath("$[0].queuedAt").doesNotExist())
        .andExpect(jsonPath("$[0].processedAt").doesNotExist())
        .andExpect(jsonPath("$[0].lastAttemptAt").doesNotExist())
        .andExpect(jsonPath("$[0].createdBy").doesNotExist())
        .andExpect(jsonPath("$[0].createdAt").doesNotExist());
  }

  // --- #4: E22 on a plan the principal can't access → codeless 404 (chokepoint) ------------------
  @Test
  void list_crossOwnerPlan_404() throws Exception {
    Employee ada = saveIc("ada@x.test");
    Employee eve = saveIc("eve@x.test");
    WeeklyPlan evePlan = savePlan(eve.getId(), PlanState.LOCKED);

    mvc.perform(
            get("/api/outlook-sync")
                .header(HEADER, ada.getId().toString())
                .param("planId", evePlan.getId().toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").doesNotExist());
  }

  // --- E22 is OWNER-ONLY: even an active direct manager of the owner → codeless 404 + denial audit
  // (the sync list is the IC's private view — catalog "IC own", unlike the manager-inclusive E23).
  @Test
  void list_managerOfOwnerPlan_404() throws Exception {
    Employee ic = saveIc("ada@x.test");
    Employee mgr = saveManager("boss@x.test");
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    saveSyncRecord(ic.getId(), plan.getId(), EventKind.IC_PLANNING, SyncStatus.FAILED);

    mvc.perform(
            get("/api/outlook-sync")
                .header(HEADER, mgr.getId().toString())
                .param("planId", plan.getId().toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").doesNotExist());

    assertThat(auditEvents.findAll()).anyMatch(a -> a.getAction().equals("AUTHORIZATION_DENIED"));
  }

  // ===== E23 retry =====

  // --- retry happy path: FAILED → 200 RETRY_REQUESTED (response); the publish advances DB → QUEUED
  @Test
  void retry_failedRecord_200_responseRetryRequested_dbAdvancesToQueued() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    OutlookCalendarSyncRecord rec =
        saveSyncRecord(ic.getId(), plan.getId(), EventKind.IC_PLANNING, SyncStatus.FAILED);

    mvc.perform(
            post("/api/outlook-sync/" + rec.getId() + "/retry")
                .header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(rec.getId().toString()))
        .andExpect(jsonPath("$.status").value("RETRY_REQUESTED")); // the core-txn snapshot

    // the reused afterCommit publisher republished through the stub gateway → DB advanced to QUEUED
    assertThat(syncRecords.findById(rec.getId()).orElseThrow().getStatus())
        .isEqualTo(SyncStatus.QUEUED);
  }

  // --- #7: retry on a non-FAILED record → 409 SYNC_NOT_RETRYABLE, no state change ----------------
  @Test
  void retry_nonFailedRecord_409_syncNotRetryable() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    OutlookCalendarSyncRecord rec =
        saveSyncRecord(ic.getId(), plan.getId(), EventKind.IC_PLANNING, SyncStatus.SYNCED);

    mvc.perform(
            post("/api/outlook-sync/" + rec.getId() + "/retry")
                .header(HEADER, ic.getId().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SYNC_NOT_RETRYABLE"));

    assertThat(syncRecords.findById(rec.getId()).orElseThrow().getStatus())
        .isEqualTo(SyncStatus.SYNCED); // unchanged
  }

  // --- #10: an active direct manager of the owner may retry (E23 admits manager-of-owner) -------
  @Test
  void retry_managerOfOwner_allowed() throws Exception {
    Employee ic = saveIc("ada@x.test");
    Employee mgr = saveManager("boss@x.test");
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    OutlookCalendarSyncRecord rec =
        saveSyncRecord(ic.getId(), plan.getId(), EventKind.IC_PLANNING, SyncStatus.FAILED);

    mvc.perform(
            post("/api/outlook-sync/" + rec.getId() + "/retry")
                .header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RETRY_REQUESTED"));
  }

  // --- #11: an unrelated principal → codeless 404 + a denial audit ------------------------------
  @Test
  void retry_stranger_404_auditedDenial() throws Exception {
    Employee ic = saveIc("ada@x.test");
    Employee stranger = saveIc("eve@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    OutlookCalendarSyncRecord rec =
        saveSyncRecord(ic.getId(), plan.getId(), EventKind.IC_PLANNING, SyncStatus.FAILED);

    mvc.perform(
            post("/api/outlook-sync/" + rec.getId() + "/retry")
                .header(HEADER, stranger.getId().toString()))
        .andExpect(status().isNotFound());

    assertThat(auditEvents.findAll()).anyMatch(a -> a.getAction().equals("AUTHORIZATION_DENIED"));
    assertThat(syncRecords.findById(rec.getId()).orElseThrow().getStatus())
        .isEqualTo(SyncStatus.FAILED); // unchanged
  }

  // --- #12: both /api/outlook-sync mappings are request-reachable --------------------------------
  @Test
  void syncEndpoints_requestReachable() {
    boolean getMapped =
        handlerMapping.getHandlerMethods().keySet().stream()
            .anyMatch(
                info ->
                    info.getPathPatternsCondition() != null
                        && info.getPathPatternsCondition()
                            .getPatternValues()
                            .contains("/api/outlook-sync")
                        && info.getMethodsCondition().getMethods().contains(RequestMethod.GET));
    boolean postMapped =
        handlerMapping.getHandlerMethods().keySet().stream()
            .anyMatch(
                info ->
                    info.getPathPatternsCondition() != null
                        && info.getPathPatternsCondition()
                            .getPatternValues()
                            .contains("/api/outlook-sync/{syncRecordId}/retry")
                        && info.getMethodsCondition().getMethods().contains(RequestMethod.POST));
    assertThat(getMapped).as("GET /api/outlook-sync mapped").isTrue();
    assertThat(postMapped).as("POST /api/outlook-sync/{syncRecordId}/retry mapped").isTrue();
  }
}
