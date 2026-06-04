package com.st6.wc.review;

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
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.RoleType;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.ProjectionRefresher;
import com.st6.wc.review.dto.MarkReviewedRequest;
import com.st6.wc.review.mapper.ReviewMapper;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.web.IllegalStateTransitionException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * {@link ReviewService#markReviewed} unit proof (task 5.2, §3 review lifecycle / §5 E16 / §6
 * manager-mutation authz / §15 audit). Authorize-first chokepoint ({@code authorizeReviewMutation}
 * — manager-of-owner-only, NOT the IC), a {@code DRAFT}-plan guard ({@code
 * ILLEGAL_STATE_TRANSITION}), the server-derived status ({@code REVIEWED} vs {@code
 * REVIEWED_WITH_DISPUTES} from the deriver's unresolved count — never client-set), {@code
 * reviewedAt} from the injected {@code Clock}, the optional {@code summaryNote}, the {@code
 * REVIEW_MARKED} audit (no note body), and {@code @Version} conflict propagation. The DB shapes are
 * proven in {@code MarkReviewedEndpointTest}.
 */
class MarkReviewedServiceTest {

  private final DomainAuthorizationService authz = mock(DomainAuthorizationService.class);
  private final ManagerReviewRepository reviews = mock(ManagerReviewRepository.class);
  private final WeeklyPlanRepository plans = mock(WeeklyPlanRepository.class);
  private final ReviewStatusDeriver deriver = mock(ReviewStatusDeriver.class);
  private final ReviewMapper reviewMapper = mock(ReviewMapper.class);
  private final AuditService auditService = mock(AuditService.class);
  private final ProjectionRefresher projectionRefresher = mock(ProjectionRefresher.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-06-03T15:00:00Z"), ZoneOffset.UTC);

  private final ReviewService service =
      new ReviewService(
          authz, reviews, plans, deriver, reviewMapper, auditService, projectionRefresher, clock);

  private static final UUID REVIEW_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
  private static final UUID PLAN_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
  private static final UUID MANAGER_ID = UUID.fromString("c0000000-0000-0000-0000-0000000000aa");

  private UserPrincipal manager() {
    return new UserPrincipal(MANAGER_ID, RoleType.MANAGER, true);
  }

  private ManagerReview review() {
    ManagerReview r = new ManagerReview();
    r.setId(REVIEW_ID);
    r.setWeeklyPlanId(PLAN_ID);
    r.setManagerEmployeeId(MANAGER_ID);
    r.setStatus(ReviewStatus.NOT_REVIEWED);
    r.setReviewDueAt(Instant.parse("2026-06-04T22:00:00Z"));
    return r;
  }

  private WeeklyPlan plan(PlanState state) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(PLAN_ID);
    p.setEmployeeId(UUID.randomUUID());
    p.setState(state);
    return p;
  }

  // --- authorize-first chokepoint → derive status → stamp reviewedAt → persist → audit ----
  @Test
  void markReviewed_authorizesThenDerivesAndPersists() {
    when(reviews.findById(REVIEW_ID)).thenReturn(Optional.of(review()));
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.LOCKED)));
    when(deriver.unresolvedDisputeCount(PLAN_ID)).thenReturn(0);

    service.markReviewed(manager(), REVIEW_ID, new MarkReviewedRequest("looks good"));

    verify(authz).authorizeReviewMutation(manager(), REVIEW_ID); // the chokepoint ran
    ArgumentCaptor<ManagerReview> saved = ArgumentCaptor.forClass(ManagerReview.class);
    verify(reviews).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(ReviewStatus.REVIEWED); // 0 unresolved
    assertThat(saved.getValue().getReviewedAt()).isEqualTo(Instant.parse("2026-06-03T15:00:00Z"));
    assertThat(saved.getValue().getSummaryNote()).isEqualTo("looks good");
    verify(auditService)
        .record(
            eq("REVIEW_MARKED"),
            eq("ManagerReview"),
            eq(REVIEW_ID),
            eq(MANAGER_ID),
            any(),
            eq("{}")); // safe metadata only — no note body (§15)
  }

  // --- 6.3b §9 trigger: mark-reviewed synchronously refreshes the manager projection (same txn) -
  @Test
  void markReviewed_refreshesProjection() {
    when(reviews.findById(REVIEW_ID)).thenReturn(Optional.of(review()));
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.LOCKED)));
    when(deriver.unresolvedDisputeCount(PLAN_ID)).thenReturn(0);

    service.markReviewed(manager(), REVIEW_ID, new MarkReviewedRequest("looks good"));

    ArgumentCaptor<WeeklyPlan> refreshed = ArgumentCaptor.forClass(WeeklyPlan.class);
    verify(projectionRefresher).recomputeForPlan(refreshed.capture());
    assertThat(refreshed.getValue().getId()).isEqualTo(PLAN_ID); // the in-scope, loaded plan
  }

  // --- rule #3: a denied authorize is the chokepoint — nothing loaded or persisted ----
  @Test
  void markReviewed_deniedAuthorizer_neverPersists() {
    doThrow(new ResourceNotFoundOrUnauthorizedException())
        .when(authz)
        .authorizeReviewMutation(any(), eq(REVIEW_ID));

    assertThatThrownBy(() -> service.markReviewed(manager(), REVIEW_ID, null))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verify(reviews, never()).findById(REVIEW_ID); // no load before authorization
    verify(reviews, never()).save(any());
  }

  // --- ≥1 unresolved dispute → server-derives REVIEWED_WITH_DISPUTES (never client-set) ----
  @Test
  void markReviewed_withUnresolvedDisputes_setsReviewedWithDisputes() {
    when(reviews.findById(REVIEW_ID)).thenReturn(Optional.of(review()));
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.LOCKED)));
    when(deriver.unresolvedDisputeCount(PLAN_ID)).thenReturn(2);

    service.markReviewed(manager(), REVIEW_ID, null);

    ArgumentCaptor<ManagerReview> saved = ArgumentCaptor.forClass(ManagerReview.class);
    verify(reviews).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(ReviewStatus.REVIEWED_WITH_DISPUTES);
  }

  // --- a review whose plan is still DRAFT (defensive) → 409 ILLEGAL_STATE_TRANSITION ----
  @Test
  void markReviewed_planDraft_throwsIllegalStateTransition() {
    when(reviews.findById(REVIEW_ID)).thenReturn(Optional.of(review()));
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.DRAFT)));

    assertThatThrownBy(() -> service.markReviewed(manager(), REVIEW_ID, null))
        .isInstanceOf(IllegalStateTransitionException.class);
    verify(reviews, never()).save(any());
    // 5.6 REQ-F-010 (Q1): a DRAFT-state rejection is a workflow 409, NOT an authorization denial —
    // the manager IS authorized; no audit_event is written (§15/§17/§25; denials audit, state-409s
    // do not).
    verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
  }

  // --- null request body (no summaryNote) → status still derived, note untouched ----
  @Test
  void markReviewed_nullRequest_leavesSummaryNoteUntouched() {
    ManagerReview existing = review();
    existing.setSummaryNote("prior note");
    when(reviews.findById(REVIEW_ID)).thenReturn(Optional.of(existing));
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.RECONCILING)));
    when(deriver.unresolvedDisputeCount(PLAN_ID)).thenReturn(0);

    service.markReviewed(manager(), REVIEW_ID, null);

    ArgumentCaptor<ManagerReview> saved = ArgumentCaptor.forClass(ManagerReview.class);
    verify(reviews).save(saved.capture());
    assertThat(saved.getValue().getSummaryNote()).isEqualTo("prior note"); // untouched
    assertThat(saved.getValue().getStatus()).isEqualTo(ReviewStatus.REVIEWED);
  }

  // --- @Version conflict on save propagates (→ 409 at the handler) ----
  @Test
  void markReviewed_staleVersion_propagatesOptimisticLockFailure() {
    when(reviews.findById(REVIEW_ID)).thenReturn(Optional.of(review()));
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.LOCKED)));
    when(deriver.unresolvedDisputeCount(PLAN_ID)).thenReturn(0);
    when(reviews.save(any()))
        .thenThrow(new ObjectOptimisticLockingFailureException(ManagerReview.class, REVIEW_ID));

    assertThatThrownBy(() -> service.markReviewed(manager(), REVIEW_ID, null))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }
}
