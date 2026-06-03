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
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.WorkType;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.mapper.PlanMapper;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.ProjectionService;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.ManagerReview;
import com.st6.wc.review.ReviewSlaService;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.sns.SnsLifecyclePublisher;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import com.st6.wc.sync.SyncRecordService;
import com.st6.wc.web.EmptyPlanLockException;
import com.st6.wc.web.IllegalStateTransitionException;
import com.st6.wc.web.UnlinkedPlannedCommitmentException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code PlanLifecycleService.lock} unit proof (task 3.5, §3/§5/§6 — rules #1/#4). Pins:
 * authorize-first chokepoint (no load/mutate on denial); the lock precondition reuses the REAL
 * {@link AllowedActionResolver#canLock} (single-source, §15) with granular rejection codes (empty →
 * {@code EMPTY_PLAN_LOCK}, unlinked → {@code UNLINKED_PLANNED_COMMITMENT}+fieldErrors, non-DRAFT →
 * {@code ILLEGAL_STATE_TRANSITION}); the LOCKED transition + side-effect orchestration; and the
 * no-manager branch (lock + audit + IC_PLANNING still happen, but review/projection/review-block
 * are skipped). Side-effect DB shapes are proven end-to-end in {@code PlanLockEndpointTest}.
 */
class PlanLifecycleServiceTest {

  private final DomainAuthorizationService authz = mock(DomainAuthorizationService.class);
  private final WeeklyPlanRepository plans = mock(WeeklyPlanRepository.class);
  private final WeeklyCommitmentRepository commitments = mock(WeeklyCommitmentRepository.class);
  private final EmployeeRepository employees = mock(EmployeeRepository.class);
  private final ManagerRelationshipRepository relationships =
      mock(ManagerRelationshipRepository.class);
  private final AllowedActionResolver allowedActionResolver = new AllowedActionResolver(); // REAL
  private final ReviewSlaService reviewSlaService = mock(ReviewSlaService.class);
  private final ManagerReviewRepository reviews = mock(ManagerReviewRepository.class);
  private final ProjectionService projectionService = mock(ProjectionService.class);
  private final SyncRecordService syncRecordService = mock(SyncRecordService.class);
  private final SnsLifecyclePublisher snsPublisher = mock(SnsLifecyclePublisher.class);
  private final AuditService auditService = mock(AuditService.class);
  private final PlanMapper planMapper = mock(PlanMapper.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T15:00:00Z"), ZoneOffset.UTC);

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
          projectionService,
          syncRecordService,
          snsPublisher,
          auditService,
          planMapper,
          clock);

  private static final UUID IC = UUID.randomUUID();
  private static final UUID MGR = UUID.randomUUID();
  private static final UUID PLAN_ID = UUID.randomUUID();
  private static final UUID SO = UUID.randomUUID();
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

  private static WeeklyCommitment planned(UUID soId) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(PLAN_ID);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setSupportingOutcomeId(soId);
    c.setAlignmentStatus(AlignmentStatus.ALIGNED);
    c.setWorkType(WorkType.STRATEGIC);
    c.setPriority(Priority.P1);
    c.setConfidence(Confidence.MEDIUM);
    return c;
  }

  private void stubLoad(PlanState state, List<WeeklyCommitment> cs) {
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(state)));
    when(commitments.findByWeeklyPlanIdOrderByIdAsc(PLAN_ID)).thenReturn(cs);
    Employee e = new Employee();
    e.setId(IC);
    e.setDisplayName("Ada");
    when(employees.findById(IC)).thenReturn(Optional.of(e));
    when(reviewSlaService.reviewDueAt(any())).thenReturn(Instant.parse("2026-06-02T22:00:00Z"));
    OutlookCalendarSyncRecord icRec = new OutlookCalendarSyncRecord();
    icRec.setId(UUID.randomUUID());
    when(syncRecordService.createIcPlanningRecord(any(), any())).thenReturn(icRec);
  }

  private void withManager() {
    ManagerRelationship rel = new ManagerRelationship();
    rel.setManagerEmployeeId(MGR);
    rel.setDirectReportEmployeeId(IC);
    rel.setActive(true);
    when(relationships.findByDirectReportEmployeeIdAndActiveTrue(IC)).thenReturn(Optional.of(rel));
  }

  // --- happy path: LOCKED + lockedAt + the manager-scoped side-effects orchestrated ----
  @Test
  void lock_happyPath_setsLockedAndOrchestratesSideEffects() {
    stubLoad(PlanState.DRAFT, List.of(planned(SO)));
    withManager();

    service.lock(actor(), PLAN_ID);

    verify(authz).authorizePlanMutation(actor(), PLAN_ID); // chokepoint
    org.mockito.ArgumentCaptor<WeeklyPlan> savedPlan =
        org.mockito.ArgumentCaptor.forClass(WeeklyPlan.class);
    verify(plans).save(savedPlan.capture());
    assertThat(savedPlan.getValue().getState()).isEqualTo(PlanState.LOCKED);
    assertThat(savedPlan.getValue().getLockedAt()).isEqualTo(clock.instant());

    org.mockito.ArgumentCaptor<ManagerReview> savedReview =
        org.mockito.ArgumentCaptor.forClass(ManagerReview.class);
    verify(reviews).save(savedReview.capture());
    assertThat(savedReview.getValue().getManagerEmployeeId()).isEqualTo(MGR);
    assertThat(savedReview.getValue().getStatus())
        .isEqualTo(com.st6.wc.enums.ReviewStatus.NOT_REVIEWED);
    assertThat(savedReview.getValue().getReviewDueAt())
        .isEqualTo(Instant.parse("2026-06-02T22:00:00Z"));

    verify(projectionService).recompute(any(), eq(MGR), any(), any());
    verify(syncRecordService).createIcPlanningRecord(any(), any());
    verify(syncRecordService).upsertManagerReviewBlock(eq(MGR), eq(WEEK), any());
    verify(auditService)
        .record(eq("PLAN_LOCKED"), eq("WeeklyPlan"), eq(PLAN_ID), eq(IC), any(), any());
    verify(snsPublisher).publish(any()); // post-commit publish scheduled (no txn → immediate)
  }

  // --- rule #3: a denied authorize is the chokepoint — nothing loaded or mutated ----
  @Test
  void lock_deniedAuthorizer_neverLoadsOrMutates() {
    doThrow(new ResourceNotFoundOrUnauthorizedException())
        .when(authz)
        .authorizePlanMutation(any(), eq(PLAN_ID));

    assertThatThrownBy(() -> service.lock(actor(), PLAN_ID))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verify(plans, never()).findById(PLAN_ID);
    verify(plans, never()).save(any());
  }

  // --- rule #1: an empty DRAFT plan → 409 EMPTY_PLAN_LOCK; nothing locked ----
  @Test
  void lock_emptyPlan_throwsEmptyPlanLock() {
    stubLoad(PlanState.DRAFT, List.of());

    assertThatThrownBy(() -> service.lock(actor(), PLAN_ID))
        .isInstanceOf(EmptyPlanLockException.class);
    verify(plans, never()).save(any());
  }

  // --- rule #1: an unlinked planned commitment → UNLINKED_PLANNED_COMMITMENT naming it ----
  @Test
  void lock_unlinkedPlanned_throwsUnlinkedWithFieldErrors() {
    WeeklyCommitment unlinked = planned(null); // no Supporting Outcome
    stubLoad(PlanState.DRAFT, List.of(planned(SO), unlinked));

    assertThatThrownBy(() -> service.lock(actor(), PLAN_ID))
        .isInstanceOf(UnlinkedPlannedCommitmentException.class)
        .satisfies(
            ex ->
                assertThat(((UnlinkedPlannedCommitmentException) ex).fieldErrors())
                    .containsKey(unlinked.getId().toString()));
    verify(plans, never()).save(any());
  }

  // --- double-lock: a non-DRAFT plan → 409 ILLEGAL_STATE_TRANSITION ----
  @Test
  void lock_nonDraftPlan_throwsIllegalStateTransition() {
    stubLoad(PlanState.LOCKED, List.of(planned(SO)));

    assertThatThrownBy(() -> service.lock(actor(), PLAN_ID))
        .isInstanceOf(IllegalStateTransitionException.class);
    verify(plans, never()).save(any());
  }

  // --- no active manager (e.g. Dana locks her own plan) → lock + audit + IC_PLANNING, but NO
  //     review / projection / review-block ----
  @Test
  void lock_noManager_locksButSkipsReviewProjectionBlock() {
    stubLoad(PlanState.DRAFT, List.of(planned(SO)));
    when(relationships.findByDirectReportEmployeeIdAndActiveTrue(IC)).thenReturn(Optional.empty());

    service.lock(actor(), PLAN_ID);

    org.mockito.ArgumentCaptor<WeeklyPlan> savedPlan =
        org.mockito.ArgumentCaptor.forClass(WeeklyPlan.class);
    verify(plans).save(savedPlan.capture());
    assertThat(savedPlan.getValue().getState()).isEqualTo(PlanState.LOCKED);
    verify(syncRecordService).createIcPlanningRecord(any(), any()); // IC_PLANNING still created
    verify(auditService).record(eq("PLAN_LOCKED"), any(), any(), any(), any(), any());
    // manager-scoped side-effects skipped
    verify(reviews, never()).save(any());
    verify(projectionService, never()).recompute(any(), any(), any(), any());
    verify(syncRecordService, never()).upsertManagerReviewBlock(any(), any(), any());
  }

  // --- concurrent double-lock: an optimistic-lock conflict on save PROPAGATES (not swallowed) →
  //     the handler maps it to 409 (ADD 1). The plan @Version is the conflict source (1.6). ----
  @Test
  void lock_optimisticLockConflict_propagates() {
    stubLoad(PlanState.DRAFT, List.of(planned(SO)));
    withManager();
    when(plans.save(any()))
        .thenThrow(
            new org.springframework.orm.ObjectOptimisticLockingFailureException(
                "stale", new RuntimeException()));

    assertThatThrownBy(() -> service.lock(actor(), PLAN_ID))
        .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
  }

  // --- fail-closed: canLock=false for a reason none of the three diagnostics match → still a 409,
  //     never a silent lock (ADD 2). Uses a STUBBED resolver on an otherwise-lockable plan. ----
  @Test
  void lock_canLockFalseUnknownReason_failsClosed409() {
    AllowedActionResolver stubResolver = mock(AllowedActionResolver.class);
    when(stubResolver.canLock(any(), any(), any())).thenReturn(false); // forced false
    PlanLifecycleService svc =
        new PlanLifecycleService(
            authz,
            plans,
            commitments,
            employees,
            relationships,
            stubResolver,
            reviewSlaService,
            reviews,
            projectionService,
            syncRecordService,
            snsPublisher,
            auditService,
            planMapper,
            clock);
    stubLoad(
        PlanState.DRAFT, List.of(planned(SO))); // DRAFT + ≥1 linked planned → no diagnostic matches

    assertThatThrownBy(() -> svc.lock(actor(), PLAN_ID))
        .isInstanceOf(IllegalStateTransitionException.class);
    verify(plans, never()).save(any());
  }
}
