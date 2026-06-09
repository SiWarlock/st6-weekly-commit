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
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import com.st6.wc.sync.SyncRecordService;
import com.st6.wc.web.IllegalStateTransitionException;
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
 * {@code PlanLifecycleService.startReconciliation} unit proof (task 4.2, §3 {@code
 * LOCKED→RECONCILING} / §5 E9 / §6 / §10) — mirrors the 3.5 lock shape (LESSONS §28):
 * authorize-first chokepoint, a forward-only state guard ({@code LOCKED}-source-only, else {@code
 * ILLEGAL_STATE_TRANSITION}), the transition + side-effect orchestration (projection refresh,
 * {@code RECONCILIATION_STARTED} audit, {@code IC_RECONCILIATION} sync record), and the no-manager
 * branch (transition + audit + sync still happen; projection skipped). Side-effect DB shapes are
 * proven in {@code StartReconciliationEndpointTest}.
 */
class StartReconciliationServiceTest {

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
  private final Clock clock = Clock.fixed(Instant.parse("2026-06-03T15:00:00Z"), ZoneOffset.UTC);

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

  private void stubLoad(PlanState state) {
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(state)));
    when(commitments.findByWeeklyPlanIdOrderByIdAsc(PLAN_ID)).thenReturn(List.of(committed()));
    Employee e = new Employee();
    e.setId(IC);
    e.setDisplayName("Ada");
    when(employees.findById(IC)).thenReturn(Optional.of(e));
    OutlookCalendarSyncRecord rec = new OutlookCalendarSyncRecord();
    rec.setId(UUID.randomUUID());
    when(syncRecordService.createIcReconciliationRecord(any(), any())).thenReturn(Optional.of(rec));
  }

  private static WeeklyCommitment committed() {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(PLAN_ID);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setSupportingOutcomeId(UUID.randomUUID());
    return c;
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
    review.setReviewDueAt(Instant.parse("2026-06-04T22:00:00Z"));
    when(reviews.findByWeeklyPlanId(PLAN_ID)).thenReturn(Optional.of(review));
  }

  // --- LOCKED → RECONCILING + reconciliationStartedAt + side-effect orchestration ----
  @Test
  void start_lockedPlan_transitionsAndOrchestrates() {
    stubLoad(PlanState.LOCKED);
    withManagerAndReview();

    service.startReconciliation(actor(), PLAN_ID);

    verify(authz).authorizePlanMutation(actor(), PLAN_ID); // chokepoint
    ArgumentCaptor<WeeklyPlan> saved = ArgumentCaptor.forClass(WeeklyPlan.class);
    verify(plans).save(saved.capture());
    assertThat(saved.getValue().getState()).isEqualTo(PlanState.RECONCILING);
    assertThat(saved.getValue().getReconciliationStartedAt()).isEqualTo(clock.instant());

    verify(projectionRefresher).recomputeForPlan(any()); // §9 plan_state refresh
    verify(auditService)
        .record(eq("RECONCILIATION_STARTED"), eq("WeeklyPlan"), eq(PLAN_ID), eq(IC), any(), any());
    verify(syncRecordService).createIcReconciliationRecord(any(), any());
    verify(snsPublisher).publish(any()); // post-commit publish scheduled
  }

  // --- rule #3: a denied authorize is the chokepoint — nothing loaded or mutated ----
  @Test
  void start_deniedAuthorizer_neverLoadsOrMutates() {
    doThrow(new ResourceNotFoundOrUnauthorizedException())
        .when(authz)
        .authorizePlanMutation(any(), eq(PLAN_ID));

    assertThatThrownBy(() -> service.startReconciliation(actor(), PLAN_ID))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verify(plans, never()).findById(PLAN_ID);
    verify(plans, never()).save(any());
  }

  // --- forward-only: any non-LOCKED source → 409 ILLEGAL_STATE_TRANSITION; nothing mutated ----
  @Test
  void start_nonLockedStates_throwIllegalStateTransition() {
    for (PlanState state : List.of(PlanState.DRAFT, PlanState.RECONCILING, PlanState.RECONCILED)) {
      when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(state)));
      assertThatThrownBy(() -> service.startReconciliation(actor(), PLAN_ID))
          .isInstanceOf(IllegalStateTransitionException.class);
    }
    verify(plans, never()).save(any());
    verify(syncRecordService, never()).createIcReconciliationRecord(any(), any());
  }

  // --- no active manager → transition + audit + IC_RECONCILIATION; the projection refresh is now
  //     called unconditionally (the refresher no-ops when there's no manager) ----
  @Test
  void start_noManager_transitionsAndRefreshes() {
    stubLoad(PlanState.LOCKED);
    when(relationships.findByDirectReportEmployeeIdAndActiveTrue(IC)).thenReturn(Optional.empty());

    service.startReconciliation(actor(), PLAN_ID);

    ArgumentCaptor<WeeklyPlan> saved = ArgumentCaptor.forClass(WeeklyPlan.class);
    verify(plans).save(saved.capture());
    assertThat(saved.getValue().getState()).isEqualTo(PlanState.RECONCILING);
    verify(syncRecordService).createIcReconciliationRecord(any(), any());
    verify(auditService).record(eq("RECONCILIATION_STARTED"), any(), any(), any(), any(), any());
    verify(projectionRefresher).recomputeForPlan(any());
  }

  // --- concurrent double-start: an optimistic-lock conflict propagates (handler maps → 409) ----
  @Test
  void start_optimisticLockConflict_propagates() {
    stubLoad(PlanState.LOCKED);
    withManagerAndReview();
    when(plans.save(any()))
        .thenThrow(
            new org.springframework.orm.ObjectOptimisticLockingFailureException(
                "stale", new RuntimeException()));

    assertThatThrownBy(() -> service.startReconciliation(actor(), PLAN_ID))
        .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
  }
}
