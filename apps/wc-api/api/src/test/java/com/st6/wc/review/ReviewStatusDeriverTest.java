package com.st6.wc.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.dispute.AlignmentDispute;
import com.st6.wc.dispute.repo.AlignmentDisputeRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.DisputeStatus;
import com.st6.wc.enums.FlagType;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.WorkType;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.support.AbstractAppBootTest;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@link ReviewStatusDeriver} over real PG16 ({@link AbstractAppBootTest}) — the server-derived
 * review status (task 5.2, §3 / §9 / REQ-F-011/013). {@code derive(planId)} → {@code
 * REVIEWED_WITH_DISPUTES} iff the plan's commitments carry ≥1 dispute in the unresolved bucket
 * {@code {OPEN, IC_RESPONDED}}, else {@code REVIEWED}; the matching count drives the DTO's {@code
 * unresolvedDisputeCount}. Disputes are <strong>direct-seeded</strong> (the create-endpoint is 5.3)
 * to exercise both branches.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
class ReviewStatusDeriverTest extends AbstractAppBootTest {

  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);

  @Autowired private ReviewStatusDeriver deriver;
  @Autowired private EmployeeRepository employees;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private WeeklyCommitmentRepository commitments;
  @Autowired private AlignmentDisputeRepository disputes;

  @AfterEach
  void cleanup() {
    disputes.deleteAll();
    commitments.deleteAll();
    plans.deleteAll();
    employees.deleteAll();
  }

  private Employee saveEmployee() {
    Employee e = new Employee();
    UUID id = UUID.randomUUID();
    e.setId(id);
    e.setEmail("e-" + id + "@x.test");
    e.setDisplayName("Name");
    e.setRole(RoleType.IC);
    e.setActive(true);
    return employees.saveAndFlush(e);
  }

  private WeeklyPlan savePlan() {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(saveEmployee().getId()); // FK: weekly_plan.employee_id → employee.id
    p.setWeekStartDate(WEEK);
    p.setWeekEndDate(WEEK.plusDays(6));
    p.setState(PlanState.LOCKED);
    return plans.saveAndFlush(p);
  }

  private WeeklyCommitment saveCommitment(UUID planId) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(planId);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("Ship it");
    c.setPriority(Priority.P1);
    c.setWorkType(WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(AlignmentStatus.ALIGNED);
    return commitments.saveAndFlush(c);
  }

  private void saveDispute(UUID commitmentId, DisputeStatus status) {
    AlignmentDispute d = new AlignmentDispute();
    d.setId(UUID.randomUUID());
    d.setCommitmentId(commitmentId);
    d.setManagerEmployeeId(saveEmployee().getId()); // FK: alignment_dispute.manager_employee_id
    d.setStatus(status);
    d.setFlagType(FlagType.MISALIGNED);
    d.setManagerNote("please revisit the alignment");
    disputes.saveAndFlush(d);
  }

  // --- #1 a plan with commitments but no disputes → REVIEWED, count 0 ----
  @Test
  void derive_noUnresolvedDisputes_returnsReviewed() {
    WeeklyPlan plan = savePlan();
    saveCommitment(plan.getId());

    assertThat(deriver.unresolvedDisputeCount(plan.getId())).isZero();
    assertThat(deriver.derive(plan.getId())).isEqualTo(ReviewStatus.REVIEWED);
  }

  // --- #2 an OPEN dispute on a plan commitment → REVIEWED_WITH_DISPUTES, count 1 ----
  @Test
  void derive_withOpenDispute_returnsReviewedWithDisputes() {
    WeeklyPlan plan = savePlan();
    WeeklyCommitment c = saveCommitment(plan.getId());
    saveDispute(c.getId(), DisputeStatus.OPEN);

    assertThat(deriver.unresolvedDisputeCount(plan.getId())).isEqualTo(1);
    assertThat(deriver.derive(plan.getId())).isEqualTo(ReviewStatus.REVIEWED_WITH_DISPUTES);
  }

  // --- #3 an IC_RESPONDED dispute is still unresolved → REVIEWED_WITH_DISPUTES ----
  @Test
  void derive_withIcRespondedDispute_returnsReviewedWithDisputes() {
    WeeklyPlan plan = savePlan();
    WeeklyCommitment c = saveCommitment(plan.getId());
    saveDispute(c.getId(), DisputeStatus.IC_RESPONDED);

    assertThat(deriver.unresolvedDisputeCount(plan.getId())).isEqualTo(1);
    assertThat(deriver.derive(plan.getId())).isEqualTo(ReviewStatus.REVIEWED_WITH_DISPUTES);
  }

  // --- #4 only historical RESOLVED disputes don't count → REVIEWED, count 0 ----
  @Test
  void derive_onlyResolvedDisputes_returnsReviewed() {
    WeeklyPlan plan = savePlan();
    WeeklyCommitment c = saveCommitment(plan.getId());
    saveDispute(c.getId(), DisputeStatus.RESOLVED);

    assertThat(deriver.unresolvedDisputeCount(plan.getId())).isZero();
    assertThat(deriver.derive(plan.getId())).isEqualTo(ReviewStatus.REVIEWED);
  }

  // --- #5 a plan with no commitments → REVIEWED, count 0 (the empty-id-set guard) ----
  @Test
  void derive_noCommitments_returnsReviewed() {
    WeeklyPlan plan = savePlan();

    assertThat(deriver.unresolvedDisputeCount(plan.getId())).isZero();
    assertThat(deriver.derive(plan.getId())).isEqualTo(ReviewStatus.REVIEWED);
  }

  // --- #6 a dispute on ANOTHER plan's commitment is not counted (plan-scoped) ----
  @Test
  void derive_disputeOnAnotherPlansCommitment_notCounted() {
    WeeklyPlan plan = savePlan();
    saveCommitment(plan.getId()); // this plan, no dispute
    WeeklyPlan otherPlan = savePlan();
    WeeklyCommitment otherCommitment = saveCommitment(otherPlan.getId());
    saveDispute(otherCommitment.getId(), DisputeStatus.OPEN); // dispute belongs to the other plan

    assertThat(deriver.unresolvedDisputeCount(plan.getId())).isZero();
    assertThat(deriver.derive(plan.getId())).isEqualTo(ReviewStatus.REVIEWED);
  }
}
