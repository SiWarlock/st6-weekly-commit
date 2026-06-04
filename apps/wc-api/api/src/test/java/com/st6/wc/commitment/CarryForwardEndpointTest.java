package com.st6.wc.commitment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.st6.wc.audit.repo.AuditEventRepository;
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
import com.st6.wc.plan.WeeklyPlan;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code POST /api/commitments/{id}/carry-forward} (E12) end-to-end through the demo-mode 2.6 chain
 * (task 4.4, §3 / §5 / §8 / §9 / REQ-D-006 / REQ-F-028 / REQ-E-005) against real PG16 ({@link
 * AbstractAppBootTest}). Proves: the carry-forward sets source {@code CARRIED_FORWARD} + creates a
 * linked successor in a next-week DRAFT shell (create-if-absent); idempotency per source (re-invoke
 * returns the same successor, zero dup, single shell); the state guard + owner-only authz; the
 * REQ-E-005 chain (prior-week planned baseline byte-identical, successor in a distinct plan); and
 * the Q1 projection reading (source-week {@code carry_forward_count} stays 0 — the carried-IN count
 * materializes at the next-week lock; the next-week DRAFT shell has no projection).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class CarryForwardEndpointTest extends AbstractAppBootTest {

  private static final String HEADER = "X-Demo-Employee-Id";
  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1); // a Monday
  private static final LocalDate NEXT_WEEK = LocalDate.of(2026, 6, 8);
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
    // batch (single DELETE) so the carry_forward_source_commitment_id self-FK is checked at
    // statement-end (NO ACTION), not per-row — per-row deleteAll() can delete a source before its
    // successor → FK violation → cleanup aborts → cross-test row leak.
    commitments.deleteAllInBatch();
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

  private WeeklyPlan savePlan(UUID ownerId, PlanState state, LocalDate weekStart) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(ownerId);
    p.setWeekStartDate(weekStart);
    p.setWeekEndDate(weekStart.plusDays(6));
    p.setState(state);
    return plans.saveAndFlush(p);
  }

  private WeeklyCommitment savePlanned(UUID planId, UUID soId) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(planId);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("Draft the activation-onboarding runbook");
    c.setDescription("the runbook");
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

  private void saveReview(UUID planId, UUID managerId) {
    ManagerReview r = new ManagerReview();
    r.setId(UUID.randomUUID());
    r.setWeeklyPlanId(planId);
    r.setManagerEmployeeId(managerId);
    r.setStatus(ReviewStatus.NOT_REVIEWED);
    r.setReviewDueAt(Instant.parse("2026-06-02T22:00:00Z"));
    reviews.saveAndFlush(r);
  }

  // --- #1 happy + REQ-E-005 chain: source CARRIED_FORWARD; linked successor in next-week DRAFT;
  // prior-week planned baseline byte-identical; source-week carry_forward_count stays 0 (Q1) ----
  @Test
  void carryForward_setsOutcome_createsLinkedSuccessor_baselineUnmutated() throws Exception {
    Employee ic = saveEmployee("grace@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.RECONCILING, WEEK);
    WeeklyCommitment src = savePlanned(plan.getId(), SEED_SO_1_1);
    saveReview(plan.getId(), mgr.getId());

    mvc.perform(
            post("/api/commitments/" + src.getId() + "/carry-forward")
                .header(HEADER, ic.getId().toString()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.commitmentKind").value("PLANNED"))
        .andExpect(jsonPath("$.carryForwardSourceCommitmentId").value(src.getId().toString()))
        .andExpect(jsonPath("$.supportingOutcomeId").doesNotExist()); // starts unlinked

    // source: only reconciliation_outcome changed; baseline byte-identical
    WeeklyCommitment after = commitments.findById(src.getId()).orElseThrow();
    assertThat(after.getReconciliationOutcome()).isEqualTo(ReconciliationOutcome.CARRIED_FORWARD);
    assertThat(after.getTitle()).isEqualTo("Draft the activation-onboarding runbook");
    assertThat(after.getSupportingOutcomeId()).isEqualTo(SEED_SO_1_1);
    assertThat(after.getPriority()).isEqualTo(Priority.P1);
    assertThat(after.getWorkType()).isEqualTo(WorkType.STRATEGIC);
    assertThat(after.getCommitmentKind()).isEqualTo(CommitmentKind.PLANNED);
    assertThat(after.getCarryForwardSourceCommitmentId()).isNull(); // the source is not a successor

    // a next-week DRAFT plan was created holding the linked successor
    WeeklyPlan nextPlan =
        plans.findByEmployeeIdAndWeekStartDate(ic.getId(), NEXT_WEEK).orElseThrow();
    assertThat(nextPlan.getState()).isEqualTo(PlanState.DRAFT);
    List<WeeklyCommitment> successors =
        commitments.findByWeeklyPlanIdOrderByIdAsc(nextPlan.getId());
    assertThat(successors).hasSize(1);
    assertThat(successors.get(0).getCarryForwardSourceCommitmentId()).isEqualTo(src.getId());

    // Q1: source-week carry_forward_count stays 0 (carried-IN reading; materializes at next lock)
    assertThat(
            summaries.findAll().stream()
                .filter(s -> s.getWeekStartDate().equals(WEEK))
                .findFirst()
                .orElseThrow()
                .getCarryForwardCount())
        .isZero();
    // next-week DRAFT shell has no projection (no review until lock)
    assertThat(summaries.findAll().stream().anyMatch(s -> s.getWeekStartDate().equals(NEXT_WEEK)))
        .isFalse();
    assertThat(
            auditEvents.findAll().stream()
                .anyMatch(a -> a.getAction().equals("COMMITMENT_CARRIED_FORWARD")))
        .isTrue();
  }

  // --- #2 idempotent per source: re-invoke returns the same successor; zero dup, single shell ----
  @Test
  void carryForward_idempotentPerSource() throws Exception {
    Employee ic = saveEmployee("grace@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.RECONCILING, WEEK);
    WeeklyCommitment src = savePlanned(plan.getId(), SEED_SO_1_1);

    String first =
        mvc.perform(
                post("/api/commitments/" + src.getId() + "/carry-forward")
                    .header(HEADER, ic.getId().toString()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String second =
        mvc.perform(
                post("/api/commitments/" + src.getId() + "/carry-forward")
                    .header(HEADER, ic.getId().toString()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // same successor id both times; exactly one successor + one next-week shell
    WeeklyPlan nextPlan =
        plans.findByEmployeeIdAndWeekStartDate(ic.getId(), NEXT_WEEK).orElseThrow();
    assertThat(commitments.findByWeeklyPlanIdOrderByIdAsc(nextPlan.getId())).hasSize(1);
    assertThat(plans.findAll().stream().filter(p -> p.getWeekStartDate().equals(NEXT_WEEK)).count())
        .isEqualTo(1);
    assertThat(first).isEqualTo(second); // identical response (same successor)
  }

  // --- #3 shell reuse: a pre-existing next-week plan is reused, not duplicated ----
  @Test
  void carryForward_reusesExistingNextWeekShell() throws Exception {
    Employee ic = saveEmployee("grace@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.RECONCILING, WEEK);
    WeeklyCommitment src = savePlanned(plan.getId(), SEED_SO_1_1);
    WeeklyPlan preExistingNext = savePlan(ic.getId(), PlanState.DRAFT, NEXT_WEEK);

    mvc.perform(
            post("/api/commitments/" + src.getId() + "/carry-forward")
                .header(HEADER, ic.getId().toString()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.weeklyPlanId").value(preExistingNext.getId().toString()));

    assertThat(plans.findAll().stream().filter(p -> p.getWeekStartDate().equals(NEXT_WEEK)).count())
        .isEqualTo(1); // reused, not duplicated
  }

  // --- #4 state guard: parent plan not RECONCILING → 409 ILLEGAL_STATE_TRANSITION ----
  @Test
  void carryForward_onNonReconciling_409() throws Exception {
    Employee ic = saveEmployee("grace@x.test", RoleType.IC);
    for (PlanState state :
        new PlanState[] {PlanState.DRAFT, PlanState.LOCKED, PlanState.RECONCILED}) {
      WeeklyPlan plan = savePlan(ic.getId(), state, WEEK.plusDays(7 * state.ordinal()));
      WeeklyCommitment src = savePlanned(plan.getId(), SEED_SO_1_1);
      mvc.perform(
              post("/api/commitments/" + src.getId() + "/carry-forward")
                  .header(HEADER, ic.getId().toString()))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));
      assertThat(commitments.findById(src.getId()).orElseThrow().getReconciliationOutcome())
          .isNull();
    }
  }

  // --- #5 authz: a non-owner IC → codeless 404 (IDOR-safe); source untouched ----
  @Test
  void carryForward_nonOwner_404() throws Exception {
    Employee owner = saveEmployee("owner@x.test", RoleType.IC);
    Employee other = saveEmployee("other@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(owner.getId(), PlanState.RECONCILING, WEEK);
    WeeklyCommitment src = savePlanned(plan.getId(), SEED_SO_1_1);

    mvc.perform(
            post("/api/commitments/" + src.getId() + "/carry-forward")
                .header(HEADER, other.getId().toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").doesNotExist());
    assertThat(commitments.findById(src.getId()).orElseThrow().getReconciliationOutcome()).isNull();
    assertThat(plans.findByEmployeeIdAndWeekStartDate(owner.getId(), NEXT_WEEK))
        .isEmpty(); // no shell
  }

  // --- #6 a manager-direct-report cannot carry forward a report's commitment → 403 ----
  @Test
  void carryForward_managerDirectReport_403() throws Exception {
    Employee ic = saveEmployee("report@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.RECONCILING, WEEK);
    WeeklyCommitment src = savePlanned(plan.getId(), SEED_SO_1_1);

    mvc.perform(
            post("/api/commitments/" + src.getId() + "/carry-forward")
                .header(HEADER, mgr.getId().toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COMMITMENT_OWNER_REQUIRED"));
    assertThat(commitments.findById(src.getId()).orElseThrow().getReconciliationOutcome()).isNull();
  }

  // --- #7 unauthenticated → 401 ----
  @Test
  void carryForward_unauthenticated_401() throws Exception {
    mvc.perform(post("/api/commitments/" + UUID.randomUUID() + "/carry-forward"))
        .andExpect(status().isUnauthorized());
  }

  // --- #8 §10: carry-forward creates ZERO Outlook sync records (no carry-forward trigger) — the
  // negative pin distinguishing E12 from E9 start-reconciliation (which DOES create one) ----
  @Test
  void carryForward_createsNoOutlookSyncRecord() throws Exception {
    Employee ic = saveEmployee("grace@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.RECONCILING, WEEK);
    WeeklyCommitment src = savePlanned(plan.getId(), SEED_SO_1_1);

    long before = syncRecords.count();
    mvc.perform(
            post("/api/commitments/" + src.getId() + "/carry-forward")
                .header(HEADER, ic.getId().toString()))
        .andExpect(status().isCreated());

    assertThat(syncRecords.count()).isEqualTo(before); // §10 has no carry-forward trigger
    assertThat(syncRecords.findAll()).isEmpty();
  }
}
