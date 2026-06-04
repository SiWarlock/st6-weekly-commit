package com.st6.wc.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.st6.wc.audit.repo.AuditEventRepository;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.dispute.DisputeService;
import com.st6.wc.dispute.dto.AlignmentDisputeDto;
import com.st6.wc.dispute.dto.OpenDisputeRequest;
import com.st6.wc.dispute.dto.RespondDisputeRequest;
import com.st6.wc.dispute.repo.AlignmentDisputeRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.FlagType;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.WorkType;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.PlanLifecycleService;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.repo.ManagerHeatmapCellRepository;
import com.st6.wc.projection.repo.ManagerPlanSummaryRepository;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.ReviewService;
import com.st6.wc.review.dto.MarkReviewedRequest;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.sns.LifecycleSnsGateway;
import com.st6.wc.support.AbstractAppBootTest;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * 6.3b end-to-end proof for the dispute/review §9 projection triggers against real PG16 + the V4
 * RCDO seed ({@link AbstractAppBootTest}) — driven through the real {@link PlanLifecycleService} /
 * {@link DisputeService} / {@link ReviewService} (each its own {@code @Version} txn). Two proofs:
 * (1) <strong>stale heatmap-cell deletion on a dispute-respond SO revision</strong> — the rule-#2
 * exception remaps the commitment's Defining Objective, so the old DO cell must be DELETED (no
 * orphan) and the new DO cell created; (2) the <strong>§17 lock→mark-reviewed→open→resolve
 * sequence</strong> — the review status + the projection counts track every transition and {@code
 * is_review_overdue} stays {@code false} through the {@code REVIEWED_WITH_DISPUTES} window.
 *
 * <p>Context signature (annotations + {@code @MockBean LifecycleSnsGateway}) intentionally matches
 * {@code PlanLockEndpointTest} so Spring reuses that cached context (no new context — LESSONS §29);
 * the no-op gateway lets the lock's post-commit pointer publish succeed without real AWS.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class ProjectionTriggerIntegrationTest extends AbstractAppBootTest {

  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);
  // V4 seed: SO-1.1 → DO-1; SO-2.1 → DO-2 (two SOs under two different Defining Objectives).
  private static final UUID SO_1_1 = UUID.fromString("c0000000-0000-0000-0000-000000000001");
  private static final UUID DO_1 = UUID.fromString("b0000000-0000-0000-0000-000000000001");
  private static final UUID SO_2_1 = UUID.fromString("c0000000-0000-0000-0000-000000000004");
  private static final UUID DO_2 = UUID.fromString("b0000000-0000-0000-0000-000000000002");

  @Autowired private EmployeeRepository employees;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private WeeklyCommitmentRepository commitments;
  @Autowired private ManagerRelationshipRepository relationships;
  @Autowired private ManagerReviewRepository reviews;
  @Autowired private AlignmentDisputeRepository disputes;
  @Autowired private ManagerPlanSummaryRepository summaries;
  @Autowired private ManagerHeatmapCellRepository heatmapCells;
  @Autowired private OutlookCalendarSyncRecordRepository syncRecords;
  @Autowired private AuditEventRepository auditEvents;

  @Autowired private PlanLifecycleService planLifecycleService;
  @Autowired private DisputeService disputeService;
  @Autowired private ReviewService reviewService;

  @MockBean private LifecycleSnsGateway snsGateway;

  @AfterEach
  void cleanup() {
    auditEvents.deleteAll();
    summaries.deleteAll();
    heatmapCells.deleteAll();
    syncRecords.deleteAll();
    disputes.deleteAll();
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

  private WeeklyPlan saveDraftPlan(UUID ownerId) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(ownerId);
    p.setWeekStartDate(WEEK);
    p.setWeekEndDate(WEEK.plusDays(6));
    p.setState(PlanState.DRAFT);
    return plans.saveAndFlush(p);
  }

  private WeeklyCommitment savePlannedCommitment(UUID planId, UUID soId) {
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

  private ManagerPlanSummary summary(UUID managerId, UUID icId) {
    return summaries
        .findByManagerEmployeeIdAndEmployeeIdAndWeekStartDate(managerId, icId, WEEK)
        .orElseThrow();
  }

  private List<ManagerHeatmapCell> cells(UUID managerId, UUID icId) {
    return heatmapCells.findByManagerEmployeeIdAndEmployeeIdAndWeekStartDate(managerId, icId, WEEK);
  }

  // --- 6.3b respond SO-revision remaps the Defining Objective → the old DO cell is DELETED ----
  @Test
  void respond_soRevision_remapsHeatmapCell() {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = saveDraftPlan(ic.getId());
    WeeklyCommitment commitment = savePlannedCommitment(plan.getId(), SO_1_1); // DO-1
    UserPrincipal icp = new UserPrincipal(ic.getId(), RoleType.IC, false);
    UserPrincipal mgrp = new UserPrincipal(mgr.getId(), RoleType.MANAGER, true);

    planLifecycleService.lock(icp, plan.getId());
    // after lock the projection has exactly the DO-1 cell (the commitment's SO → DO-1)
    assertThat(cells(mgr.getId(), ic.getId()))
        .singleElement()
        .satisfies(c -> assertThat(c.getDefiningObjectiveId()).isEqualTo(DO_1));

    // a manager opens a dispute (so the IC may respond), then the IC re-links to an SO under DO-2
    AlignmentDisputeDto dispute =
        disputeService.open(
            mgrp, commitment.getId(), new OpenDisputeRequest(FlagType.NEEDS_REVISION, "re-scope"));
    disputeService.respond(icp, dispute.id(), new RespondDisputeRequest(null, SO_2_1)); // DO-1→DO-2

    List<ManagerHeatmapCell> after = cells(mgr.getId(), ic.getId());
    // the DO-1 cell is GONE (stale-cell deletion), and a single DO-2 cell now holds the commitment
    assertThat(after).noneMatch(c -> c.getDefiningObjectiveId().equals(DO_1));
    assertThat(after)
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.getDefiningObjectiveId()).isEqualTo(DO_2);
              assertThat(c.getCommitmentCount()).isEqualTo(1);
            });
  }

  // --- §17: lock → mark-reviewed → open(MISALIGNED) → resolve; review status + counts track each
  // transition, is_review_overdue=false through the REVIEWED_WITH_DISPUTES window ----
  @Test
  void fullLifecycle_reviewStatusAndCountsTrackEachTrigger() {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = saveDraftPlan(ic.getId());
    WeeklyCommitment commitment = savePlannedCommitment(plan.getId(), SO_1_1);
    UserPrincipal icp = new UserPrincipal(ic.getId(), RoleType.IC, false);
    UserPrincipal mgrp = new UserPrincipal(mgr.getId(), RoleType.MANAGER, true);

    // 1. lock → projection created, review NOT_REVIEWED, not overdue
    planLifecycleService.lock(icp, plan.getId());
    ManagerPlanSummary afterLock = summary(mgr.getId(), ic.getId());
    assertThat(afterLock.getReviewStatus()).isEqualTo(ReviewStatus.NOT_REVIEWED);
    assertThat(afterLock.isReviewOverdue()).isFalse();
    assertThat(afterLock.getUnresolvedDisputeCount()).isZero();

    // 2. mark-reviewed → REVIEWED (0 unresolved), still not overdue
    UUID reviewId = reviews.findByWeeklyPlanId(plan.getId()).orElseThrow().getId();
    reviewService.markReviewed(mgrp, reviewId, new MarkReviewedRequest("looks good"));
    ManagerPlanSummary afterReviewed = summary(mgr.getId(), ic.getId());
    assertThat(afterReviewed.getReviewStatus()).isEqualTo(ReviewStatus.REVIEWED);
    assertThat(afterReviewed.isReviewOverdue()).isFalse();

    // 3. open a MISALIGNED dispute → REVIEWED→REVIEWED_WITH_DISPUTES, unresolved=1, misaligned=1,
    //    is_review_overdue stays false (not NOT_REVIEWED)
    AlignmentDisputeDto dispute =
        disputeService.open(
            mgrp, commitment.getId(), new OpenDisputeRequest(FlagType.MISALIGNED, "off-strategy"));
    ManagerPlanSummary afterOpen = summary(mgr.getId(), ic.getId());
    assertThat(afterOpen.getReviewStatus()).isEqualTo(ReviewStatus.REVIEWED_WITH_DISPUTES);
    assertThat(afterOpen.isReviewOverdue()).isFalse();
    assertThat(afterOpen.getUnresolvedDisputeCount()).isEqualTo(1);
    assertThat(afterOpen.getMisalignedCount()).isEqualTo(1); // §9 dispute-union

    // 4. resolve → REVIEWED_WITH_DISPUTES→REVIEWED, unresolved back to 0, misaligned back to 0
    disputeService.resolve(mgrp, dispute.id());
    ManagerPlanSummary afterResolve = summary(mgr.getId(), ic.getId());
    assertThat(afterResolve.getReviewStatus()).isEqualTo(ReviewStatus.REVIEWED);
    assertThat(afterResolve.getUnresolvedDisputeCount()).isZero();
    assertThat(afterResolve.getMisalignedCount()).isZero();
  }
}
