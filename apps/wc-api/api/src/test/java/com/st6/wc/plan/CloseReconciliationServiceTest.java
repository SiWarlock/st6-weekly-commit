package com.st6.wc.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.st6.wc.audit.AuditService;
import com.st6.wc.auth.DomainAuthorizationService;
import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.ReconciliationOutcome;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.RoleType;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.mapper.PlanMapper;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.ProjectionRefresher;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.ManagerReview;
import com.st6.wc.review.ReviewSlaService;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.sns.SnsLifecyclePublisher;
import com.st6.wc.sync.SyncRecordService;
import com.st6.wc.web.IllegalStateTransitionException;
import com.st6.wc.web.UnplannedMissingLinkAtCloseException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@code PlanLifecycleService.closeReconciliation} unit proof (task 4.5, E10, §3 {@code
 * RECONCILING→RECONCILED} / §5 / §9) — the symmetric counterpart to {@code startReconciliation}
 * (LESSONS §28/§30): authorize-first chokepoint, forward-only state guard ({@code
 * RECONCILING}-only, else {@code ILLEGAL_STATE_TRANSITION}), the <strong>completeness
 * precondition</strong> (every PLANNED has an outcome AND every UNPLANNED has outcome + SO-link,
 * else {@code 422 UNPLANNED_MISSING_LINK_AT_CLOSE} + fieldErrors — a {@code CARRIED_FORWARD} counts
 * as an outcome, §30), the transition + projection refresh + {@code PLAN_RECONCILED} audit, and the
 * no-manager branch. <strong>No sync record</strong> (§10 has no close trigger — unlike start). DB
 * shapes are proven in {@code CloseReconciliationEndpointTest}.
 */
class CloseReconciliationServiceTest {

  private final DomainAuthorizationService authz = mock(DomainAuthorizationService.class);
  private final WeeklyPlanRepository plans = mock(WeeklyPlanRepository.class);
  private final WeeklyCommitmentRepository commitments = mock(WeeklyCommitmentRepository.class);
  private final EmployeeRepository employees = mock(EmployeeRepository.class);
  private final ManagerRelationshipRepository relationships =
      mock(ManagerRelationshipRepository.class);
  private final AllowedActionResolver allowedActionResolver = mock(AllowedActionResolver.class);
  private final ReviewSlaService reviewSlaService = mock(ReviewSlaService.class);
  private final ManagerReviewRepository reviews = mock(ManagerReviewRepository.class);
  private final ProjectionRefresher projectionRefresher = mock(ProjectionRefresher.class);
  private final SyncRecordService syncRecordService = mock(SyncRecordService.class);
  private final SnsLifecyclePublisher snsPublisher = mock(SnsLifecyclePublisher.class);
  private final AuditService auditService = mock(AuditService.class);
  private final PlanMapper planMapper = mock(PlanMapper.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-06-08T15:00:00Z"), ZoneOffset.UTC);

  private final PlanLifecycleService service =
      new PlanLifecycleService(
          authz,
          plans,
          commitments,
          employees,
          relationships,
          allowedActionResolver,
          reviewSlaService,
          reviews,
          projectionRefresher,
          syncRecordService,
          snsPublisher,
          auditService,
          planMapper,
          clock);

  private static final UUID IC = UUID.randomUUID();
  private static final UUID MGR = UUID.randomUUID();
  private static final UUID PLAN_ID = UUID.randomUUID();
  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);

  private UserPrincipal actor() {
    return new UserPrincipal(IC, RoleType.IC, false);
  }

  private static WeeklyPlan plan(PlanState state) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(PLAN_ID);
    p.setEmployeeId(IC);
    p.setWeekStartDate(WEEK);
    p.setWeekEndDate(WEEK.plusDays(6));
    p.setState(state);
    return p;
  }

  private static WeeklyCommitment commitment(
      CommitmentKind kind, ReconciliationOutcome outcome, UUID soId) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(PLAN_ID);
    c.setCommitmentKind(kind);
    c.setReconciliationOutcome(outcome);
    c.setSupportingOutcomeId(soId);
    return c;
  }

  private void stubLoad(PlanState state, List<WeeklyCommitment> commitmentList) {
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(state)));
    when(commitments.findByWeeklyPlanIdOrderByIdAsc(PLAN_ID)).thenReturn(commitmentList);
    Employee e = new Employee();
    e.setId(IC);
    e.setDisplayName("Grace");
    when(employees.findById(IC)).thenReturn(Optional.of(e));
  }

  private void withManagerAndReview() {
    ManagerRelationship rel = new ManagerRelationship();
    rel.setManagerEmployeeId(MGR);
    rel.setDirectReportEmployeeId(IC);
    rel.setActive(true);
    when(relationships.findByDirectReportEmployeeIdAndActiveTrue(IC)).thenReturn(Optional.of(rel));
    ManagerReview review = new ManagerReview();
    review.setId(UUID.randomUUID());
    review.setWeeklyPlanId(PLAN_ID);
    review.setManagerEmployeeId(MGR);
    review.setStatus(ReviewStatus.NOT_REVIEWED);
    when(reviews.findByWeeklyPlanId(PLAN_ID)).thenReturn(Optional.of(review));
  }

  // --- happy: all complete → RECONCILED + reconciledAt + projection + PLAN_RECONCILED audit ----
  @Test
  void close_allComplete_transitionsToReconciled() {
    stubLoad(
        PlanState.RECONCILING,
        List.of(
            commitment(CommitmentKind.PLANNED, ReconciliationOutcome.COMPLETED, UUID.randomUUID()),
            commitment(
                CommitmentKind.UNPLANNED, ReconciliationOutcome.BLOCKED, UUID.randomUUID())));
    withManagerAndReview();

    service.closeReconciliation(actor(), PLAN_ID);

    verify(authz).authorizePlanMutation(actor(), PLAN_ID); // chokepoint
    ArgumentCaptor<WeeklyPlan> saved = ArgumentCaptor.forClass(WeeklyPlan.class);
    verify(plans).save(saved.capture());
    assertThat(saved.getValue().getState()).isEqualTo(PlanState.RECONCILED);
    assertThat(saved.getValue().getReconciledAt()).isEqualTo(clock.instant());
    verify(projectionRefresher).recomputeForPlan(any()); // §9 plan_state refresh
    verify(auditService)
        .record(eq("PLAN_RECONCILED"), eq("WeeklyPlan"), eq(PLAN_ID), eq(IC), any(), any());
    verify(syncRecordService, never())
        .createIcReconciliationRecord(any(), any()); // §10 — no close trigger
  }

  // --- a CARRIED_FORWARD outcome counts as "has an outcome" at close (§30) → does NOT block ----
  @Test
  void close_carriedForwardCountsAsOutcome() {
    stubLoad(
        PlanState.RECONCILING,
        List.of(
            commitment(
                CommitmentKind.PLANNED, ReconciliationOutcome.CARRIED_FORWARD, UUID.randomUUID())));
    withManagerAndReview();

    service.closeReconciliation(actor(), PLAN_ID);

    verify(plans).save(any());
  }

  // --- a PLANNED commitment with no outcome → 422 UNPLANNED_MISSING_LINK_AT_CLOSE; nothing saved
  // --
  @Test
  void close_plannedMissingOutcome_422() {
    stubLoad(
        PlanState.RECONCILING,
        List.of(commitment(CommitmentKind.PLANNED, null, UUID.randomUUID())));

    assertThatThrownBy(() -> service.closeReconciliation(actor(), PLAN_ID))
        .isInstanceOf(UnplannedMissingLinkAtCloseException.class);
    verify(plans, never()).save(any());
  }

  // --- an UNPLANNED commitment with no outcome → 422; nothing saved ----
  @Test
  void close_unplannedMissingOutcome_422() {
    stubLoad(
        PlanState.RECONCILING,
        List.of(commitment(CommitmentKind.UNPLANNED, null, UUID.randomUUID())));

    assertThatThrownBy(() -> service.closeReconciliation(actor(), PLAN_ID))
        .isInstanceOf(UnplannedMissingLinkAtCloseException.class);
    verify(plans, never()).save(any());
  }

  // --- an UNPLANNED commitment with an outcome but NO Supporting-Outcome link → 422; nothing saved
  // -
  @Test
  void close_unplannedMissingSupportingOutcome_422() {
    stubLoad(
        PlanState.RECONCILING,
        List.of(commitment(CommitmentKind.UNPLANNED, ReconciliationOutcome.COMPLETED, null)));

    assertThatThrownBy(() -> service.closeReconciliation(actor(), PLAN_ID))
        .isInstanceOf(UnplannedMissingLinkAtCloseException.class);
    verify(plans, never()).save(any());
  }

  // --- the 422 fieldErrors enumerate each violation with a per-violation constraint; a single
  // UNPLANNED commitment missing BOTH outcome AND SO yields TWO entries (the field-path key
  // `commitments[<id>].<field>` prevents the collision a bare-<id> key would cause) ----
  @Test
  void close_incompleteFieldErrors_enumerateEachViolationWithConstraint() {
    WeeklyCommitment plannedNoOutcome = commitment(CommitmentKind.PLANNED, null, UUID.randomUUID());
    WeeklyCommitment unplannedMissingBoth = commitment(CommitmentKind.UNPLANNED, null, null);
    stubLoad(PlanState.RECONCILING, List.of(plannedNoOutcome, unplannedMissingBoth));

    assertThatThrownBy(() -> service.closeReconciliation(actor(), PLAN_ID))
        .isInstanceOfSatisfying(
            UnplannedMissingLinkAtCloseException.class,
            ex -> {
              // the one UNPLANNED commitment contributes TWO distinct entries (no key collision)
              assertThat(ex.fieldErrors())
                  .containsEntry(
                      "commitments[" + unplannedMissingBoth.getId() + "].reconciliationOutcome",
                      "unplanned_missing_outcome")
                  .containsEntry(
                      "commitments[" + unplannedMissingBoth.getId() + "].supportingOutcomeId",
                      "unplanned_missing_supporting_outcome")
                  .containsEntry(
                      "commitments[" + plannedNoOutcome.getId() + "].reconciliationOutcome",
                      "planned_missing_outcome");
            });
  }

  // --- forward-only: any non-RECONCILING source → 409 ILLEGAL_STATE_TRANSITION; nothing mutated
  // ----
  @Test
  void close_onNonReconciling_throwsIllegalStateTransition() {
    for (PlanState state : List.of(PlanState.DRAFT, PlanState.LOCKED, PlanState.RECONCILED)) {
      when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(state)));
      assertThatThrownBy(() -> service.closeReconciliation(actor(), PLAN_ID))
          .as("close on %s must be rejected", state)
          .isInstanceOf(IllegalStateTransitionException.class);
    }
    verify(plans, never()).save(any());
  }

  // --- rule #3: a denied authorize is the chokepoint — nothing loaded or mutated ----
  @Test
  void close_deniedAuthorizer_neverLoadsOrMutates() {
    doThrow(new ResourceNotFoundOrUnauthorizedException())
        .when(authz)
        .authorizePlanMutation(any(), eq(PLAN_ID));

    assertThatThrownBy(() -> service.closeReconciliation(actor(), PLAN_ID))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verify(plans, never()).findById(PLAN_ID);
    verify(plans, never()).save(any());
  }

  // --- a concurrent close: an optimistic-lock conflict propagates (handler maps → 409) ----
  @Test
  void close_optimisticLockConflict_propagates() {
    stubLoad(
        PlanState.RECONCILING,
        List.of(
            commitment(
                CommitmentKind.PLANNED, ReconciliationOutcome.COMPLETED, UUID.randomUUID())));
    withManagerAndReview();
    when(plans.save(any()))
        .thenThrow(
            new org.springframework.orm.ObjectOptimisticLockingFailureException(
                "stale", new RuntimeException()));

    assertThatThrownBy(() -> service.closeReconciliation(actor(), PLAN_ID))
        .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
  }

  // --- no active manager → transition + audit; the projection refresh is now called
  //     unconditionally (the refresher no-ops when there's no manager) ----
  @Test
  void close_noManager_transitionsAndRefreshes() {
    stubLoad(
        PlanState.RECONCILING,
        List.of(
            commitment(
                CommitmentKind.PLANNED, ReconciliationOutcome.COMPLETED, UUID.randomUUID())));
    when(relationships.findByDirectReportEmployeeIdAndActiveTrue(IC)).thenReturn(Optional.empty());

    service.closeReconciliation(actor(), PLAN_ID);

    ArgumentCaptor<WeeklyPlan> saved = ArgumentCaptor.forClass(WeeklyPlan.class);
    verify(plans).save(saved.capture());
    assertThat(saved.getValue().getState()).isEqualTo(PlanState.RECONCILED);
    verify(auditService).record(eq("PLAN_RECONCILED"), any(), any(), any(), any(), any());
    verify(projectionRefresher).recomputeForPlan(any());
  }
}
