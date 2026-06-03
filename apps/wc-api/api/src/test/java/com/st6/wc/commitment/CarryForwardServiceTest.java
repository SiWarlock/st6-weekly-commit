package com.st6.wc.commitment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.st6.wc.audit.AuditService;
import com.st6.wc.auth.DomainAuthorizationService;
import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.commitment.mapper.CommitmentMapper;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.common.OrgTimeConfig;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.ReconciliationOutcome;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.WorkType;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.ProjectionService;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.ManagerReview;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.web.IllegalStateTransitionException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit proof of {@link CarryForwardService#carryForward} (task 4.4, E12 — §3 single-outcome / §5 /
 * §6 rule #3 / §8 / §9 / REQ-D-006 / REQ-F-027/028 / REQ-E-005). Carry-forward: authorize the
 * <strong>commitment</strong> owner-only (chokepoint), require the parent plan {@code RECONCILING},
 * set the source {@code reconciliation_outcome=CARRIED_FORWARD} (overwrites any prior completion
 * outcome — the only path that sets it), create a linked successor in the next Mon–Sun DRAFT plan
 * (create the shell only if absent), idempotent per source (re-invoke returns the existing
 * successor with no re-touch / no second shell), recompute the <strong>source</strong> plan's
 * projection in the txn (§9 lockstep; the carried-IN count materializes at the next-week lock — no
 * count change here), and emit an IC audit. Repos/authz/mapper/projection mocked; the two-week
 * chain + REQ-E-005 baseline-unmutated are proven in {@code CarryForwardEndpointTest}.
 */
class CarryForwardServiceTest {

  private final DomainAuthorizationService authz = mock(DomainAuthorizationService.class);
  private final WeeklyPlanRepository plans = mock(WeeklyPlanRepository.class);
  private final WeeklyCommitmentRepository commitments = mock(WeeklyCommitmentRepository.class);
  private final ManagerRelationshipRepository relationships =
      mock(ManagerRelationshipRepository.class);
  private final ManagerReviewRepository reviews = mock(ManagerReviewRepository.class);
  private final ProjectionService projectionService = mock(ProjectionService.class);
  private final AuditService auditService = mock(AuditService.class);
  private final CommitmentMapper commitmentMapper = mock(CommitmentMapper.class);
  private final OrgTimeConfig orgTimeConfig = new OrgTimeConfig();

  private final CarryForwardService service =
      new CarryForwardService(
          authz,
          plans,
          commitments,
          relationships,
          reviews,
          projectionService,
          auditService,
          commitmentMapper,
          orgTimeConfig);

  private static final UUID IC = UUID.randomUUID();
  private static final UUID MGR = UUID.randomUUID();
  private static final UUID SRC_ID = UUID.randomUUID();
  private static final UUID SRC_PLAN_ID = UUID.randomUUID();
  private static final UUID SO_ID = UUID.randomUUID();
  private static final LocalDate MONDAY = LocalDate.of(2026, 6, 1); // a Monday
  private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 6, 8);

  private UserPrincipal actor() {
    return new UserPrincipal(IC, RoleType.IC, false);
  }

  private static WeeklyPlan sourcePlan(PlanState state) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(SRC_PLAN_ID);
    p.setEmployeeId(IC);
    p.setWeekStartDate(MONDAY);
    p.setWeekEndDate(MONDAY.plusDays(6));
    p.setState(state);
    return p;
  }

  private static WeeklyCommitment source(ReconciliationOutcome outcome) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(SRC_ID);
    c.setWeeklyPlanId(SRC_PLAN_ID);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("Draft the activation-onboarding runbook");
    c.setDescription("the runbook");
    c.setSupportingOutcomeId(SO_ID);
    c.setPriority(Priority.P1);
    c.setWorkType(WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(AlignmentStatus.ALIGNED);
    c.setReconciliationOutcome(outcome);
    return c;
  }

  /** Load source + RECONCILING plan; no existing successor; no next-week shell; manager+review. */
  private void stubFreshCarry(ReconciliationOutcome sourceOutcome) {
    when(commitments.findById(SRC_ID)).thenReturn(Optional.of(source(sourceOutcome)));
    when(plans.findById(SRC_PLAN_ID)).thenReturn(Optional.of(sourcePlan(PlanState.RECONCILING)));
    when(commitments.findByCarryForwardSourceCommitmentId(SRC_ID)).thenReturn(Optional.empty());
    when(plans.findByEmployeeIdAndWeekStartDate(IC, NEXT_MONDAY)).thenReturn(Optional.empty());
    ManagerRelationship rel = new ManagerRelationship();
    rel.setManagerEmployeeId(MGR);
    rel.setDirectReportEmployeeId(IC);
    rel.setActive(true);
    when(relationships.findByDirectReportEmployeeIdAndActiveTrue(IC)).thenReturn(Optional.of(rel));
    ManagerReview review = new ManagerReview();
    review.setId(UUID.randomUUID());
    review.setWeeklyPlanId(SRC_PLAN_ID);
    review.setManagerEmployeeId(MGR);
    when(reviews.findByWeeklyPlanId(SRC_PLAN_ID)).thenReturn(Optional.of(review));
    when(commitments.findByWeeklyPlanIdOrderByIdAsc(SRC_PLAN_ID))
        .thenReturn(List.of(source(sourceOutcome)));
  }

  // --- happy: sets source CARRIED_FORWARD, creates a linked PLANNED successor in next-week shell
  // --
  @Test
  void carryForward_inReconciling_setsOutcomeAndCreatesLinkedSuccessor() {
    stubFreshCarry(null);

    service.carryForward(actor(), SRC_ID);

    verify(authz).authorizeCommitmentMutation(actor(), SRC_ID); // owner-only chokepoint
    ArgumentCaptor<WeeklyCommitment> saved = ArgumentCaptor.forClass(WeeklyCommitment.class);
    verify(commitments, times(2)).save(saved.capture()); // source then successor
    WeeklyCommitment savedSource = saved.getAllValues().get(0);
    WeeklyCommitment successor = saved.getAllValues().get(1);
    assertThat(savedSource.getReconciliationOutcome())
        .isEqualTo(ReconciliationOutcome.CARRIED_FORWARD);
    assertThat(successor.getCarryForwardSourceCommitmentId()).isEqualTo(SRC_ID);
    assertThat(successor.getCommitmentKind()).isEqualTo(CommitmentKind.PLANNED);
    assertThat(successor.getSupportingOutcomeId()).isNull(); // starts unlinked (R5)
    assertThat(successor.getReconciliationOutcome()).isNull();
    assertThat(successor.getTitle()).isEqualTo("Draft the activation-onboarding runbook"); // copied
    verify(projectionService).recompute(any(), eq(MGR), any(), any()); // §9 source-plan lockstep
    verify(auditService)
        .record(
            eq("COMMITMENT_CARRIED_FORWARD"),
            eq("WeeklyCommitment"),
            eq(SRC_ID),
            eq(IC),
            any(),
            any());
  }

  // --- next-week shell absent → a DRAFT shell is created for (ic, nextMonday), Mon–Sun stamped
  // ----
  @Test
  void carryForward_nextWeekShellAbsent_createsDraftShell() {
    stubFreshCarry(null);

    service.carryForward(actor(), SRC_ID);

    ArgumentCaptor<WeeklyPlan> savedPlan = ArgumentCaptor.forClass(WeeklyPlan.class);
    verify(plans).save(savedPlan.capture());
    assertThat(savedPlan.getValue().getState()).isEqualTo(PlanState.DRAFT);
    assertThat(savedPlan.getValue().getWeekStartDate()).isEqualTo(NEXT_MONDAY);
    assertThat(savedPlan.getValue().getWeekEndDate()).isEqualTo(NEXT_MONDAY.plusDays(6));
    assertThat(savedPlan.getValue().getEmployeeId()).isEqualTo(IC);
  }

  // --- next-week shell present → reused, no duplicate plan created ----
  @Test
  void carryForward_nextWeekShellPresent_reusesShell() {
    when(commitments.findById(SRC_ID)).thenReturn(Optional.of(source(null)));
    when(plans.findById(SRC_PLAN_ID)).thenReturn(Optional.of(sourcePlan(PlanState.RECONCILING)));
    when(commitments.findByCarryForwardSourceCommitmentId(SRC_ID)).thenReturn(Optional.empty());
    WeeklyPlan existingNext = new WeeklyPlan();
    existingNext.setId(UUID.randomUUID());
    existingNext.setEmployeeId(IC);
    existingNext.setWeekStartDate(NEXT_MONDAY);
    existingNext.setState(PlanState.DRAFT);
    when(plans.findByEmployeeIdAndWeekStartDate(IC, NEXT_MONDAY))
        .thenReturn(Optional.of(existingNext));
    when(relationships.findByDirectReportEmployeeIdAndActiveTrue(IC)).thenReturn(Optional.empty());

    service.carryForward(actor(), SRC_ID);

    verify(plans, never()).save(any()); // no second shell
    ArgumentCaptor<WeeklyCommitment> saved = ArgumentCaptor.forClass(WeeklyCommitment.class);
    verify(commitments, times(2)).save(saved.capture());
    assertThat(saved.getAllValues().get(1).getWeeklyPlanId()).isEqualTo(existingNext.getId());
  }

  // --- idempotent per source: re-invoke returns the existing successor; no re-touch, no shell ----
  @Test
  void carryForward_idempotentPerSource_returnsExistingSuccessor() {
    when(commitments.findById(SRC_ID))
        .thenReturn(Optional.of(source(ReconciliationOutcome.CARRIED_FORWARD)));
    when(plans.findById(SRC_PLAN_ID)).thenReturn(Optional.of(sourcePlan(PlanState.RECONCILING)));
    WeeklyCommitment existingSuccessor = new WeeklyCommitment();
    existingSuccessor.setId(UUID.randomUUID());
    existingSuccessor.setCarryForwardSourceCommitmentId(SRC_ID);
    when(commitments.findByCarryForwardSourceCommitmentId(SRC_ID))
        .thenReturn(Optional.of(existingSuccessor));

    service.carryForward(actor(), SRC_ID);

    verify(commitments, never()).save(any()); // source not re-touched, no successor created
    verify(plans, never()).save(any()); // no second shell
    verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
    verify(projectionService, never()).recompute(any(), any(), any(), any());
    verify(commitmentMapper).toDto(existingSuccessor); // returns the existing successor
  }

  // --- single-outcome: carry-forward OVERWRITES a prior completion outcome to CARRIED_FORWARD ----
  @Test
  void carryForward_overwritesPriorCompletionOutcome() {
    stubFreshCarry(ReconciliationOutcome.COMPLETED);

    service.carryForward(actor(), SRC_ID);

    ArgumentCaptor<WeeklyCommitment> saved = ArgumentCaptor.forClass(WeeklyCommitment.class);
    verify(commitments, times(2)).save(saved.capture());
    assertThat(saved.getAllValues().get(0).getReconciliationOutcome())
        .isEqualTo(ReconciliationOutcome.CARRIED_FORWARD); // overwrote COMPLETED
  }

  // --- week boundary: a year-end Monday source resolves the correct next Monday (calendar-exact)
  // --
  @Test
  void carryForward_weekBoundary_resolvesNextMonday() {
    WeeklyPlan yearEnd = sourcePlan(PlanState.RECONCILING);
    yearEnd.setWeekStartDate(LocalDate.of(2025, 12, 29)); // a Monday; next Monday = 2026-01-05
    yearEnd.setWeekEndDate(LocalDate.of(2026, 1, 4));
    when(commitments.findById(SRC_ID)).thenReturn(Optional.of(source(null)));
    when(plans.findById(SRC_PLAN_ID)).thenReturn(Optional.of(yearEnd));
    when(commitments.findByCarryForwardSourceCommitmentId(SRC_ID)).thenReturn(Optional.empty());
    when(plans.findByEmployeeIdAndWeekStartDate(IC, LocalDate.of(2026, 1, 5)))
        .thenReturn(Optional.empty());
    when(relationships.findByDirectReportEmployeeIdAndActiveTrue(IC)).thenReturn(Optional.empty());

    service.carryForward(actor(), SRC_ID);

    ArgumentCaptor<WeeklyPlan> savedPlan = ArgumentCaptor.forClass(WeeklyPlan.class);
    verify(plans).save(savedPlan.capture());
    assertThat(savedPlan.getValue().getWeekStartDate()).isEqualTo(LocalDate.of(2026, 1, 5));
  }

  // --- state guard: parent plan not RECONCILING → 409 ILLEGAL_STATE_TRANSITION; nothing written
  // ---
  @Test
  void carryForward_onNonReconciling_throwsIllegalStateTransition() {
    for (PlanState state : List.of(PlanState.DRAFT, PlanState.LOCKED, PlanState.RECONCILED)) {
      when(commitments.findById(SRC_ID)).thenReturn(Optional.of(source(null)));
      when(plans.findById(SRC_PLAN_ID)).thenReturn(Optional.of(sourcePlan(state)));
      assertThatThrownBy(() -> service.carryForward(actor(), SRC_ID))
          .as("carry-forward on %s must be rejected", state)
          .isInstanceOf(IllegalStateTransitionException.class);
    }
    verify(commitments, never()).save(any());
    verify(plans, never()).save(any());
  }

  // --- rule #3: a denied owner-only authorize is the chokepoint — nothing loaded or written ----
  @Test
  void carryForward_deniedAuthorizer_neverLoadsOrSaves() {
    doThrow(new ResourceNotFoundOrUnauthorizedException())
        .when(authz)
        .authorizeCommitmentMutation(any(), eq(SRC_ID));

    assertThatThrownBy(() -> service.carryForward(actor(), SRC_ID))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verify(commitments, never()).findById(SRC_ID);
    verify(commitments, never()).save(any());
    verify(plans, never()).save(any());
  }

  // --- @Version guard: a stale source-version save conflict propagates (handler → 409) ----
  @Test
  void carryForward_staleSourceVersion_propagates() {
    stubFreshCarry(null);
    when(commitments.save(any()))
        .thenThrow(
            new org.springframework.orm.ObjectOptimisticLockingFailureException(
                "stale", new RuntimeException()));

    assertThatThrownBy(() -> service.carryForward(actor(), SRC_ID))
        .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
  }

  // --- no active manager → successor + audit still happen, projection skipped (mirrors 4.1/4.2)
  // ---
  @Test
  void carryForward_noManager_skipsProjection() {
    when(commitments.findById(SRC_ID)).thenReturn(Optional.of(source(null)));
    when(plans.findById(SRC_PLAN_ID)).thenReturn(Optional.of(sourcePlan(PlanState.RECONCILING)));
    when(commitments.findByCarryForwardSourceCommitmentId(SRC_ID)).thenReturn(Optional.empty());
    when(plans.findByEmployeeIdAndWeekStartDate(IC, NEXT_MONDAY)).thenReturn(Optional.empty());
    when(relationships.findByDirectReportEmployeeIdAndActiveTrue(IC)).thenReturn(Optional.empty());

    service.carryForward(actor(), SRC_ID);

    verify(commitments, times(2)).save(any()); // source + successor
    verify(auditService)
        .record(eq("COMMITMENT_CARRIED_FORWARD"), any(), any(), any(), any(), any());
    verify(projectionService, never()).recompute(any(), any(), any(), any());
  }

  // --- successor work-type: an UNPLANNED source becomes a PLANNED successor with a planned type
  // ----
  @Test
  void carryForward_unplannedSource_successorWorkTypeDefaultsToStrategic() {
    WeeklyCommitment unplannedSrc = source(null);
    unplannedSrc.setCommitmentKind(CommitmentKind.UNPLANNED);
    unplannedSrc.setWorkType(WorkType.UNPLANNED);
    when(commitments.findById(SRC_ID)).thenReturn(Optional.of(unplannedSrc));
    when(plans.findById(SRC_PLAN_ID)).thenReturn(Optional.of(sourcePlan(PlanState.RECONCILING)));
    when(commitments.findByCarryForwardSourceCommitmentId(SRC_ID)).thenReturn(Optional.empty());
    when(plans.findByEmployeeIdAndWeekStartDate(IC, NEXT_MONDAY)).thenReturn(Optional.empty());
    when(relationships.findByDirectReportEmployeeIdAndActiveTrue(IC)).thenReturn(Optional.empty());

    service.carryForward(actor(), SRC_ID);

    ArgumentCaptor<WeeklyCommitment> saved = ArgumentCaptor.forClass(WeeklyCommitment.class);
    verify(commitments, times(2)).save(saved.capture());
    WeeklyCommitment successor = saved.getAllValues().get(1);
    assertThat(successor.getCommitmentKind()).isEqualTo(CommitmentKind.PLANNED); // always PLANNED
    assertThat(successor.getWorkType())
        .isEqualTo(WorkType.STRATEGIC); // UNPLANNED → planned default
  }
}
