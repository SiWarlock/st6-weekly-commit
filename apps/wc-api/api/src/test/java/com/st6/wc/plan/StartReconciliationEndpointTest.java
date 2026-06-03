package com.st6.wc.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.st6.wc.audit.repo.AuditEventRepository;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.EventKind;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.SyncStatus;
import com.st6.wc.enums.WorkType;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.repo.ManagerHeatmapCellRepository;
import com.st6.wc.projection.repo.ManagerPlanSummaryRepository;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.ManagerReview;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.sns.LifecycleSnsGateway;
import com.st6.wc.support.AbstractAppBootTest;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code POST /api/plans/{id}/start-reconciliation} (E9) end-to-end through the demo-mode 2.6 chain
 * (task 4.2, §3 {@code LOCKED→RECONCILING} / §5 / §6 / §9 / §10) against real PG16 ({@link
 * AbstractAppBootTest}). Proves: the forward-only transition (LOCKED→RECONCILING + {@code
 * reconciliationStartedAt} + projection refresh + {@code RECONCILIATION_STARTED} audit + {@code
 * IC_RECONCILIATION} sync record); the state guard (non-LOCKED → 409); the reused IC-owner-only
 * authz (non-owner→404, manager→403); the reused non-blocking publish (rule #4 — failure leaves
 * {@code PENDING_PUBLISH}); and the {@code START_RECONCILIATION} affordance on a LOCKED plan (no
 * affordance-without-enforcement for the not-yet-built 4.3/4.4/4.5 actions).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class StartReconciliationEndpointTest extends AbstractAppBootTest {

  private static final String HEADER = "X-Demo-Employee-Id";
  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);
  private static final UUID SEED_SO_1_1 = UUID.fromString("c0000000-0000-0000-0000-000000000001");

  @Autowired private MockMvc mvc;
  @Autowired private EmployeeRepository employees;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private WeeklyCommitmentRepository commitments;
  @Autowired private ManagerRelationshipRepository relationships;
  @Autowired private ManagerReviewRepository reviews;
  @Autowired private ManagerPlanSummaryRepository summaries;
  @Autowired private ManagerHeatmapCellRepository heatmapCells;
  @Autowired private OutlookCalendarSyncRecordRepository syncRecords;
  @Autowired private AuditEventRepository auditEvents;

  @MockBean private LifecycleSnsGateway snsGateway;

  @AfterEach
  void cleanup() {
    auditEvents.deleteAll();
    summaries.deleteAll();
    heatmapCells.deleteAll();
    syncRecords.deleteAll();
    reviews.deleteAll();
    commitments.deleteAll();
    relationships.deleteAll();
    plans.deleteAll();
    employees.deleteAll();
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
    if (state != PlanState.DRAFT) {
      p.setLockedAt(Instant.parse("2026-06-01T17:00:00Z"));
    }
    return plans.saveAndFlush(p);
  }

  private void saveCommitment(UUID planId, UUID soId) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(planId);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("Ship it");
    c.setSupportingOutcomeId(soId);
    c.setPriority(Priority.P1);
    c.setWorkType(WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(AlignmentStatus.ALIGNED);
    commitments.saveAndFlush(c);
  }

  private void saveReview(UUID planId, UUID managerId) {
    ManagerReview r = new ManagerReview();
    r.setId(UUID.randomUUID());
    r.setWeeklyPlanId(planId);
    r.setManagerEmployeeId(managerId);
    r.setStatus(ReviewStatus.NOT_REVIEWED);
    r.setReviewDueAt(Instant.parse("2026-06-02T22:00:00Z"));
    reviews.saveAndFlush(r);
  }

  private void saveActiveRelationship(UUID managerId, UUID reportId) {
    ManagerRelationship r = new ManagerRelationship();
    r.setId(UUID.randomUUID());
    r.setManagerEmployeeId(managerId);
    r.setDirectReportEmployeeId(reportId);
    r.setActive(true);
    relationships.saveAndFlush(r);
  }

  /** Seed a LOCKED plan with a linked commitment + a manager + an active review. */
  private WeeklyPlan lockedPlanWithManager(Employee ic, Employee mgr) {
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    saveCommitment(plan.getId(), SEED_SO_1_1);
    saveReview(plan.getId(), mgr.getId());
    return plan;
  }

  // --- #1 happy: LOCKED → RECONCILING + all atomic side-effects ----
  @Test
  void start_lockedPlan_transitionsToReconciling() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    WeeklyPlan plan = lockedPlanWithManager(ic, mgr);

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/start-reconciliation")
                .header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("RECONCILING"))
        .andExpect(jsonPath("$.reconciliationStartedAt").exists())
        // RECONCILING does NOT yet offer the 4.3/4.4/4.5 actions (no affordance without
        // enforcement)
        .andExpect(jsonPath("$.allowedActions").isEmpty());

    assertThat(plans.findById(plan.getId()).orElseThrow().getState())
        .isEqualTo(PlanState.RECONCILING);
    // projection plan_state refreshed
    assertThat(summaries.findAll().get(0).getPlanState()).isEqualTo(PlanState.RECONCILING);
    // RECONCILIATION_STARTED audit
    assertThat(
            auditEvents.findAll().stream()
                .anyMatch(a -> a.getAction().equals("RECONCILIATION_STARTED")))
        .isTrue();
    // IC_RECONCILIATION sync record published (QUEUED), coexists with no IC_PLANNING here
    var icReconciliation =
        syncRecords.findByOwnerEmployeeIdAndWeekStartDateAndEventKind(
            ic.getId(), WEEK, EventKind.IC_RECONCILIATION);
    assertThat(icReconciliation).isPresent();
    assertThat(icReconciliation.orElseThrow().getStatus()).isEqualTo(SyncStatus.QUEUED);
  }

  // --- #2 non-LOCKED source (DRAFT) → 409 ILLEGAL_STATE_TRANSITION; nothing changes ----
  @Test
  void start_draftPlan_409() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/start-reconciliation")
                .header(HEADER, ic.getId().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));
    assertThat(plans.findById(plan.getId()).orElseThrow().getState()).isEqualTo(PlanState.DRAFT);
    assertThat(syncRecords.findAll()).isEmpty();
  }

  // --- #3 rule #4: an SNS publish failure → plan STAYS RECONCILING, record retained ----
  @Test
  void start_snsPublishFailure_staysReconciling_recordRetained() throws Exception {
    doThrow(new RuntimeException("SNS unavailable")).when(snsGateway).publish(any());
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    WeeklyPlan plan = lockedPlanWithManager(ic, mgr);

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/start-reconciliation")
                .header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("RECONCILING"));

    assertThat(plans.findById(plan.getId()).orElseThrow().getState())
        .isEqualTo(PlanState.RECONCILING);
    var rec =
        syncRecords.findByOwnerEmployeeIdAndWeekStartDateAndEventKind(
            ic.getId(), WEEK, EventKind.IC_RECONCILIATION);
    assertThat(rec).isPresent();
    assertThat(rec.orElseThrow().getStatus()).isEqualTo(SyncStatus.PENDING_PUBLISH);
  }

  // --- #4 a non-owner IC → codeless 404; plan stays LOCKED ----
  @Test
  void start_nonOwnerIc_404() throws Exception {
    Employee ic = saveEmployee("owner@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    Employee other = saveEmployee("other@x.test", RoleType.IC);
    WeeklyPlan plan = lockedPlanWithManager(ic, mgr);

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/start-reconciliation")
                .header(HEADER, other.getId().toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").doesNotExist());
    assertThat(plans.findById(plan.getId()).orElseThrow().getState()).isEqualTo(PlanState.LOCKED);
  }

  // --- #5 a manager-direct-report can READ but NOT start → 403 PLAN_OWNER_REQUIRED ----
  @Test
  void start_managerDirectReport_403() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    WeeklyPlan plan = lockedPlanWithManager(ic, mgr);

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/start-reconciliation")
                .header(HEADER, mgr.getId().toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PLAN_OWNER_REQUIRED"));
    assertThat(plans.findById(plan.getId()).orElseThrow().getState()).isEqualTo(PlanState.LOCKED);
  }

  // --- #6 a LOCKED plan's allowedActions[] offers START_RECONCILIATION (the enforced affordance)
  // --
  @Test
  void start_lockedPlanAllowedActions_offersStartReconciliation() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    WeeklyPlan plan = lockedPlanWithManager(ic, mgr);

    mvc.perform(get("/api/plans/" + plan.getId()).header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("LOCKED"))
        .andExpect(
            jsonPath("$.allowedActions")
                .value(org.hamcrest.Matchers.contains("START_RECONCILIATION")));
  }

  // --- #7 unauthenticated → 401 ----
  @Test
  void start_unauthenticated_401() throws Exception {
    mvc.perform(post("/api/plans/" + UUID.randomUUID() + "/start-reconciliation"))
        .andExpect(status().isUnauthorized());
  }
}
