package com.st6.wc.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.sns.LifecycleSnsGateway;
import com.st6.wc.support.AbstractAppBootTest;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code POST /api/plans/{id}/lock} (E8) end-to-end through the demo-mode 2.6 chain (task 3.5, THE
 * safety culmination — rules #1/#2/#4) against real PG16 + the V4 RCDO seed ({@link
 * AbstractAppBootTest}). Proves the LOCK-INVARIANT checklist: rule #1 (empty → {@code
 * EMPTY_PLAN_LOCK}; unlinked → {@code UNLINKED_PLANNED_COMMITMENT}+fieldErrors; valid → LOCKED with
 * the manager review + projections + audit + IC_PLANNING + MANAGER_REVIEW_BLOCK side-effects); rule
 * #2 (post-lock baseline edit → 409 via the 3.4b gate firing on the real locked state); rule #4
 * (SNS publish failure → plan STAYS LOCKED + record retained, NEVER rolls back); authz
 * (non-owner→404, manager→403); double-lock → 409.
 *
 * <p>{@link LifecycleSnsGateway} is a {@code @MockBean}: by default its publish is a no-op (success
 * → the post-commit hook transitions the record to {@code QUEUED}); the rule-#4 test stubs it to
 * throw.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class PlanLockEndpointTest extends AbstractAppBootTest {

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
    return plans.saveAndFlush(p);
  }

  private WeeklyCommitment saveCommitment(UUID planId, UUID soId) {
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
    return commitments.saveAndFlush(c);
  }

  private void saveActiveRelationship(UUID managerId, UUID reportId) {
    ManagerRelationship r = new ManagerRelationship();
    r.setId(UUID.randomUUID());
    r.setManagerEmployeeId(managerId);
    r.setDirectReportEmployeeId(reportId);
    r.setActive(true);
    relationships.saveAndFlush(r);
  }

  // ===== rule #1 — required Supporting Outcome at lock (REQ-E-001) =====

  // --- #1 happy: a fully-linked DRAFT plan locks + creates ALL atomic side-effects ----
  @Test
  void lock_validPlan_locksAndCreatesSideEffects() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);
    saveCommitment(plan.getId(), SEED_SO_1_1);

    mvc.perform(post("/api/plans/" + plan.getId() + "/lock").header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("LOCKED"))
        .andExpect(jsonPath("$.lockedAt").exists())
        .andExpect(jsonPath("$.managerReview.status").value("NOT_REVIEWED"))
        .andExpect(jsonPath("$.managerReview.isOverdue").value(false))
        .andExpect(jsonPath("$.managerReview.reviewDueAt").exists())
        // post-lock the LOCK affordance is gone; a LOCKED plan offers START_RECONCILIATION (4.2)
        // + ADD_UNPLANNED (4.3 enforces unplanned-create in LOCKED, affordance emitted 4.5)
        .andExpect(
            jsonPath("$.allowedActions")
                .value(org.hamcrest.Matchers.contains("START_RECONCILIATION", "ADD_UNPLANNED")));

    assertThat(plans.findById(plan.getId()).orElseThrow().getState()).isEqualTo(PlanState.LOCKED);
    // manager_review row (NOT_REVIEWED)
    assertThat(reviews.findAll()).hasSize(1);
    assertThat(reviews.findAll().get(0).getStatus()).isEqualTo(ReviewStatus.NOT_REVIEWED);
    // synchronous projections
    assertThat(summaries.findAll()).hasSize(1);
    assertThat(summaries.findAll().get(0).getPlannedCount()).isEqualTo(1);
    assertThat(heatmapCells.findAll()).isNotEmpty(); // ≥1 DO cell
    // audit PLAN_LOCKED
    assertThat(auditEvents.findAll().stream().anyMatch(a -> a.getAction().equals("PLAN_LOCKED")))
        .isTrue();
    // IC_PLANNING sync record — published (QUEUED) by the post-commit hook (no-op gateway succeeds)
    var icPlanning =
        syncRecords.findByOwnerEmployeeIdAndWeekStartDateAndEventKind(
            ic.getId(), WEEK, EventKind.IC_PLANNING);
    assertThat(icPlanning).isPresent();
    assertThat(icPlanning.orElseThrow().getStatus()).isEqualTo(SyncStatus.QUEUED);
    // per-manager/week MANAGER_REVIEW_BLOCK
    assertThat(
            syncRecords.findByOwnerEmployeeIdAndWeekStartDateAndEventKind(
                mgr.getId(), WEEK, EventKind.MANAGER_REVIEW_BLOCK))
        .isPresent();
  }

  // --- #2 empty plan → 409 EMPTY_PLAN_LOCK; plan stays DRAFT ----
  @Test
  void lock_emptyPlan_409EmptyPlanLock() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);

    mvc.perform(post("/api/plans/" + plan.getId() + "/lock").header(HEADER, ic.getId().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMPTY_PLAN_LOCK"));
    assertThat(plans.findById(plan.getId()).orElseThrow().getState()).isEqualTo(PlanState.DRAFT);
  }

  // --- #3 an unlinked planned commitment → 409 UNLINKED_PLANNED_COMMITMENT + fieldErrors ----
  @Test
  void lock_unlinkedPlanned_409WithFieldErrors() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);
    saveCommitment(plan.getId(), SEED_SO_1_1); // linked
    WeeklyCommitment unlinked = saveCommitment(plan.getId(), null); // unlinked

    mvc.perform(post("/api/plans/" + plan.getId() + "/lock").header(HEADER, ic.getId().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("UNLINKED_PLANNED_COMMITMENT"))
        .andExpect(jsonPath("$.fieldErrors").exists())
        .andExpect(jsonPath("$.fieldErrors['" + unlinked.getId() + "']").exists());
    assertThat(plans.findById(plan.getId()).orElseThrow().getState()).isEqualTo(PlanState.DRAFT);
  }

  // ===== rule #2 — locked-baseline immutability fires on the REAL locked state (3.4b gate) =====

  // --- #4 after a real lock, a baseline edit → 409 LOCKED_BASELINE_EDIT; alignmentStatus → 409 ISE
  @Test
  void lock_thenBaselineEdit_rejected() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);
    WeeklyCommitment c = saveCommitment(plan.getId(), SEED_SO_1_1);

    mvc.perform(post("/api/plans/" + plan.getId() + "/lock").header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk());

    // a frozen baseline field → LOCKED_BASELINE_EDIT (rule #2)
    mvc.perform(
            patch("/api/commitments/" + c.getId())
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"changed\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LOCKED_BASELINE_EDIT"));

    // alignmentStatus → ILLEGAL_STATE_TRANSITION (Appendix E rule 2, distinct code)
    mvc.perform(
            patch("/api/commitments/" + c.getId())
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"alignmentStatus\":\"MISALIGNED\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));
  }

  // ===== rule #4 — Outlook sync NEVER blocks the lock (the load-bearing non-blocking proof) =====

  // --- #5 SNS gateway throws → plan STAYS LOCKED, IC_PLANNING record retained (PENDING_PUBLISH)
  // ---
  @Test
  void lock_snsPublishFailure_planStaysLocked_recordRetained() throws Exception {
    doThrow(new RuntimeException("SNS unavailable")).when(snsGateway).publish(any());
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);
    saveCommitment(plan.getId(), SEED_SO_1_1);

    mvc.perform(post("/api/plans/" + plan.getId() + "/lock").header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk()) // the publish failure NEVER fails the lock
        .andExpect(jsonPath("$.state").value("LOCKED"));

    assertThat(plans.findById(plan.getId()).orElseThrow().getState()).isEqualTo(PlanState.LOCKED);
    // the sync record is retained as retryable (PENDING_PUBLISH), NOT rolled back
    var icPlanning =
        syncRecords.findByOwnerEmployeeIdAndWeekStartDateAndEventKind(
            ic.getId(), WEEK, EventKind.IC_PLANNING);
    assertThat(icPlanning).isPresent();
    assertThat(icPlanning.orElseThrow().getStatus()).isEqualTo(SyncStatus.PENDING_PUBLISH);
  }

  // ===== authz + concurrency =====

  // --- #6 a non-owner IC cannot lock → codeless 404 + audit; plan stays DRAFT ----
  @Test
  void lock_nonOwnerIc_404_andAudit() throws Exception {
    Employee owner = saveEmployee("owner@x.test", RoleType.IC);
    Employee other = saveEmployee("other@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(owner.getId(), PlanState.DRAFT);
    saveCommitment(plan.getId(), SEED_SO_1_1);

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/lock").header(HEADER, other.getId().toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").doesNotExist());
    assertThat(auditEvents.findAll()).hasSize(1);
    assertThat(plans.findById(plan.getId()).orElseThrow().getState()).isEqualTo(PlanState.DRAFT);
  }

  // --- #7 a manager-direct-report can READ but NOT lock → 403 PLAN_OWNER_REQUIRED + audit ----
  @Test
  void lock_managerDirectReport_403() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);
    saveCommitment(plan.getId(), SEED_SO_1_1);

    mvc.perform(post("/api/plans/" + plan.getId() + "/lock").header(HEADER, mgr.getId().toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PLAN_OWNER_REQUIRED"));
    assertThat(plans.findById(plan.getId()).orElseThrow().getState()).isEqualTo(PlanState.DRAFT);
  }

  // --- #8 double-lock → 409 ILLEGAL_STATE_TRANSITION ----
  @Test
  void lock_alreadyLocked_409() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);
    saveCommitment(plan.getId(), SEED_SO_1_1);

    mvc.perform(post("/api/plans/" + plan.getId() + "/lock").header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk());
    mvc.perform(post("/api/plans/" + plan.getId() + "/lock").header(HEADER, ic.getId().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));
  }

  // --- #9 unauthenticated → 401 ----
  @Test
  void lock_unauthenticated_401() throws Exception {
    mvc.perform(post("/api/plans/" + UUID.randomUUID() + "/lock"))
        .andExpect(status().isUnauthorized());
  }
}
