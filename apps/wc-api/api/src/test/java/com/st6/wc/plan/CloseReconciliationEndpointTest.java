package com.st6.wc.plan;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.ReconciliationOutcome;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.WorkType;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.repo.ManagerHeatmapCellRepository;
import com.st6.wc.projection.repo.ManagerPlanSummaryRepository;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.ManagerReview;
import com.st6.wc.review.repo.ManagerReviewRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code POST /api/plans/{id}/close-reconciliation} (E10) end-to-end through the demo-mode 2.6
 * chain (task 4.5, §3 {@code RECONCILING→RECONCILED} / §5 / §9 / REQ-F-026/029) against real PG16
 * ({@link AbstractAppBootTest}). Proves: the close transition (RECONCILED + {@code reconciledAt} +
 * projection plan_state + {@code PLAN_RECONCILED} audit + NO sync record); the completeness 422
 * ({@code UNPLANNED_MISSING_LINK_AT_CLOSE} + fieldErrors); the link→close flow (an unplanned
 * missing its SO → 422 → PATCH the SO via the E6 allow-list extension → close succeeds); close is
 * non-blocking on a NOT_REVIEWED review; the owner-only authz (non-owner→404, manager→403); the
 * CLOSE_RECONCILIATION affordance on a RECONCILING plan read.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class CloseReconciliationEndpointTest extends AbstractAppBootTest {

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

  private WeeklyPlan saveReconcilingPlan(UUID ownerId) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(ownerId);
    p.setWeekStartDate(WEEK);
    p.setWeekEndDate(WEEK.plusDays(6));
    p.setState(PlanState.RECONCILING);
    return plans.saveAndFlush(p);
  }

  private WeeklyCommitment saveCommitment(
      UUID planId, CommitmentKind kind, ReconciliationOutcome outcome, UUID soId) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(planId);
    c.setCommitmentKind(kind);
    c.setTitle(kind == CommitmentKind.UNPLANNED ? "Surfaced work" : "Planned work");
    c.setSupportingOutcomeId(soId);
    c.setPriority(Priority.P1);
    c.setWorkType(kind == CommitmentKind.UNPLANNED ? WorkType.UNPLANNED : WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(AlignmentStatus.ALIGNED);
    c.setReconciliationOutcome(outcome);
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

  private void saveReview(UUID planId, UUID managerId) {
    ManagerReview r = new ManagerReview();
    r.setId(UUID.randomUUID());
    r.setWeeklyPlanId(planId);
    r.setManagerEmployeeId(managerId);
    r.setStatus(ReviewStatus.NOT_REVIEWED);
    r.setReviewDueAt(Instant.parse("2026-06-02T22:00:00Z"));
    reviews.saveAndFlush(r);
  }

  // --- #1 happy: all complete → RECONCILED + reconciledAt + projection + audit + NO sync record
  // ---
  @Test
  void close_allComplete_transitionsToReconciled() throws Exception {
    Employee ic = saveEmployee("grace@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = saveReconcilingPlan(ic.getId());
    saveCommitment(
        plan.getId(), CommitmentKind.PLANNED, ReconciliationOutcome.COMPLETED, SEED_SO_1_1);
    saveCommitment(
        plan.getId(), CommitmentKind.UNPLANNED, ReconciliationOutcome.BLOCKED, SEED_SO_1_1);
    saveReview(plan.getId(), mgr.getId());
    long syncBefore = syncRecords.count();

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/close-reconciliation")
                .header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("RECONCILED"))
        .andExpect(jsonPath("$.reconciledAt").exists());

    assertThat(plans.findById(plan.getId()).orElseThrow().getState())
        .isEqualTo(PlanState.RECONCILED);
    assertThat(summaries.findAll().get(0).getPlanState()).isEqualTo(PlanState.RECONCILED);
    assertThat(
            auditEvents.findAll().stream().anyMatch(a -> a.getAction().equals("PLAN_RECONCILED")))
        .isTrue();
    assertThat(syncRecords.count()).isEqualTo(syncBefore); // §10 — no close sync trigger
  }

  // --- #2 a CARRIED_FORWARD planned outcome counts as "has an outcome" (§30) → close succeeds ----
  @Test
  void close_carriedForwardCountsAsOutcome() throws Exception {
    Employee ic = saveEmployee("grace@x.test", RoleType.IC);
    WeeklyPlan plan = saveReconcilingPlan(ic.getId());
    saveCommitment(
        plan.getId(), CommitmentKind.PLANNED, ReconciliationOutcome.CARRIED_FORWARD, SEED_SO_1_1);

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/close-reconciliation")
                .header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("RECONCILED"));
  }

  // --- #3 a planned commitment with no outcome → 422 UNPLANNED_MISSING_LINK_AT_CLOSE + fieldErrors
  // -
  @Test
  void close_plannedMissingOutcome_422() throws Exception {
    Employee ic = saveEmployee("grace@x.test", RoleType.IC);
    WeeklyPlan plan = saveReconcilingPlan(ic.getId());
    saveCommitment(plan.getId(), CommitmentKind.PLANNED, null, SEED_SO_1_1);

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/close-reconciliation")
                .header(HEADER, ic.getId().toString()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("UNPLANNED_MISSING_LINK_AT_CLOSE"))
        .andExpect(jsonPath("$.fieldErrors").exists());
    assertThat(plans.findById(plan.getId()).orElseThrow().getState())
        .isEqualTo(PlanState.RECONCILING);
  }

  // --- #4 the link→close flow: unplanned missing SO → 422 → PATCH the SO (E6 ext) → close succeeds
  // -
  @Test
  void close_unplannedMissingSo_thenLinkViaPatch_thenClose() throws Exception {
    Employee ic = saveEmployee("grace@x.test", RoleType.IC);
    WeeklyPlan plan = saveReconcilingPlan(ic.getId());
    WeeklyCommitment unplanned =
        saveCommitment(
            plan.getId(), CommitmentKind.UNPLANNED, ReconciliationOutcome.COMPLETED, null);

    // close blocked — the unplanned commitment has an outcome but no SO link
    mvc.perform(
            post("/api/plans/" + plan.getId() + "/close-reconciliation")
                .header(HEADER, ic.getId().toString()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("UNPLANNED_MISSING_LINK_AT_CLOSE"));

    // link the unplanned's SO via the E6 allow-list extension (PATCH supportingOutcomeId)
    mvc.perform(
            patch("/api/commitments/" + unplanned.getId())
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supportingOutcomeId\":\"" + SEED_SO_1_1 + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.supportingOutcomeId").value(SEED_SO_1_1.toString()));

    // now close succeeds
    mvc.perform(
            post("/api/plans/" + plan.getId() + "/close-reconciliation")
                .header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("RECONCILED"));
  }

  // --- #5 the E6 allow-list does NOT widen: a PLANNED commitment's SO stays frozen → 409 ----
  @Test
  void patch_plannedSoEdit_inReconciling_409LockedBaseline() throws Exception {
    Employee ic = saveEmployee("grace@x.test", RoleType.IC);
    WeeklyPlan plan = saveReconcilingPlan(ic.getId());
    WeeklyCommitment planned =
        saveCommitment(
            plan.getId(), CommitmentKind.PLANNED, ReconciliationOutcome.COMPLETED, SEED_SO_1_1);

    mvc.perform(
            patch("/api/commitments/" + planned.getId())
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supportingOutcomeId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LOCKED_BASELINE_EDIT"));
    assertThat(commitments.findById(planned.getId()).orElseThrow().getSupportingOutcomeId())
        .isEqualTo(SEED_SO_1_1); // unchanged
  }

  // --- #6 close is non-blocking on a NOT_REVIEWED manager review (REQ-F-024) ----
  @Test
  void close_withManagerReviewNotReviewed_succeeds() throws Exception {
    Employee ic = saveEmployee("grace@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = saveReconcilingPlan(ic.getId());
    saveCommitment(
        plan.getId(), CommitmentKind.PLANNED, ReconciliationOutcome.COMPLETED, SEED_SO_1_1);
    saveReview(plan.getId(), mgr.getId()); // NOT_REVIEWED

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/close-reconciliation")
                .header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("RECONCILED"));
  }

  // --- #7 state guard: a non-RECONCILING plan (LOCKED) → 409 ILLEGAL_STATE_TRANSITION ----
  @Test
  void close_onLocked_409() throws Exception {
    Employee ic = saveEmployee("grace@x.test", RoleType.IC);
    WeeklyPlan plan = new WeeklyPlan();
    plan.setId(UUID.randomUUID());
    plan.setEmployeeId(ic.getId());
    plan.setWeekStartDate(WEEK);
    plan.setWeekEndDate(WEEK.plusDays(6));
    plan.setState(PlanState.LOCKED);
    plans.saveAndFlush(plan);

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/close-reconciliation")
                .header(HEADER, ic.getId().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));
  }

  // --- #8 authz: non-owner → 404; a manager-direct-report → 403 PLAN_OWNER_REQUIRED ----
  @Test
  void close_nonOwner_404_managerDirectReport_403() throws Exception {
    Employee ic = saveEmployee("grace@x.test", RoleType.IC);
    Employee other = saveEmployee("other@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = saveReconcilingPlan(ic.getId());
    saveCommitment(
        plan.getId(), CommitmentKind.PLANNED, ReconciliationOutcome.COMPLETED, SEED_SO_1_1);

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/close-reconciliation")
                .header(HEADER, other.getId().toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").doesNotExist());

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/close-reconciliation")
                .header(HEADER, mgr.getId().toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PLAN_OWNER_REQUIRED"));
    assertThat(plans.findById(plan.getId()).orElseThrow().getState())
        .isEqualTo(PlanState.RECONCILING);
  }

  // --- #9 the CLOSE_RECONCILIATION affordance is on a RECONCILING owning-IC plan read ----
  @Test
  void close_affordanceOnReconcilingPlanRead() throws Exception {
    Employee ic = saveEmployee("grace@x.test", RoleType.IC);
    WeeklyPlan plan = saveReconcilingPlan(ic.getId());

    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/plans/" + plan.getId())
                .header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allowedActions[?(@ == 'CLOSE_RECONCILIATION')]").exists());
  }

  // --- #10 unauthenticated → 401 ----
  @Test
  void close_unauthenticated_401() throws Exception {
    mvc.perform(post("/api/plans/" + UUID.randomUUID() + "/close-reconciliation"))
        .andExpect(status().isUnauthorized());
  }
}
