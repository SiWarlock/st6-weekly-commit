package com.st6.wc.dispute;

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
import com.st6.wc.dispute.dto.OpenDisputeRequest;
import com.st6.wc.dispute.mapper.DisputeMapper;
import com.st6.wc.dispute.repo.AlignmentDisputeRepository;
import com.st6.wc.enums.DisputeStatus;
import com.st6.wc.enums.FlagType;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.RoleType;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.review.ManagerReview;
import com.st6.wc.review.ReviewStatusDeriver;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.web.IllegalStateTransitionException;
import com.st6.wc.web.SecondOpenDisputeException;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * {@link DisputeService#open} unit proof (task 5.3, §3 dispute lifecycle / §5 E17 / §6 manager
 * authz / rule #6 single-unresolved). Authorize-first chokepoint ({@code authorizeDisputeCreation}
 * — manager-of-owner-only), a {@code DRAFT}-plan guard, the single-unresolved invariant (service
 * pre-check + the DB-backstop {@code DataIntegrityViolationException}→{@code SecondOpenDispute}
 * mapping — the decomposed §11 race proof), the review re-derivation (only an already-REVIEWED
 * review flips to {@code REVIEWED_WITH_DISPUTES}; a {@code NOT_REVIEWED} review is NOT marked), and
 * the {@code DISPUTE_OPENED} audit (no note body). DB shapes are proven in {@code
 * OpenDisputeEndpointTest}.
 */
class OpenDisputeServiceTest {

  private static final String UNRESOLVED_CONSTRAINT = "uq_one_unresolved_dispute_per_commitment";

  private final DomainAuthorizationService authz = mock(DomainAuthorizationService.class);
  private final AlignmentDisputeRepository disputes = mock(AlignmentDisputeRepository.class);
  private final WeeklyCommitmentRepository commitments = mock(WeeklyCommitmentRepository.class);
  private final WeeklyPlanRepository plans = mock(WeeklyPlanRepository.class);
  private final ManagerReviewRepository reviews = mock(ManagerReviewRepository.class);
  private final ReviewStatusDeriver deriver = mock(ReviewStatusDeriver.class);
  private final DisputeMapper disputeMapper = mock(DisputeMapper.class);
  private final AuditService auditService = mock(AuditService.class);
  private final com.st6.wc.rcdo.RcdoReadService rcdoReadService =
      mock(com.st6.wc.rcdo.RcdoReadService.class);

  private final DisputeService service =
      new DisputeService(
          authz,
          disputes,
          commitments,
          plans,
          reviews,
          deriver,
          disputeMapper,
          auditService,
          rcdoReadService,
          Clock.systemUTC());

  private static final UUID COMMITMENT_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
  private static final UUID PLAN_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
  private static final UUID MANAGER_ID = UUID.fromString("c0000000-0000-0000-0000-0000000000aa");

  private UserPrincipal manager() {
    return new UserPrincipal(MANAGER_ID, RoleType.MANAGER, true);
  }

  private OpenDisputeRequest request() {
    return new OpenDisputeRequest(FlagType.NEEDS_REVISION, "please re-scope this to the SO");
  }

  private WeeklyCommitment commitment() {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(COMMITMENT_ID);
    c.setWeeklyPlanId(PLAN_ID);
    return c;
  }

  private WeeklyPlan plan(PlanState state) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(PLAN_ID);
    p.setEmployeeId(UUID.randomUUID());
    p.setState(state);
    return p;
  }

  private ManagerReview review(ReviewStatus status) {
    ManagerReview r = new ManagerReview();
    r.setId(UUID.randomUUID());
    r.setWeeklyPlanId(PLAN_ID);
    r.setStatus(status);
    return r;
  }

  private void stubLockedCommitmentNoExistingDispute() {
    when(commitments.findById(COMMITMENT_ID)).thenReturn(Optional.of(commitment()));
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.LOCKED)));
    when(disputes.findByCommitmentIdAndStatusIn(eq(COMMITMENT_ID), any()))
        .thenReturn(Optional.empty());
  }

  // --- authorize-first chokepoint → create OPEN dispute (manager opener) + audit ----
  @Test
  void open_authorizesThenCreatesOpenDispute() {
    stubLockedCommitmentNoExistingDispute();
    when(reviews.findByWeeklyPlanId(PLAN_ID)).thenReturn(Optional.empty());

    service.open(manager(), COMMITMENT_ID, request());

    verify(authz).authorizeDisputeCreation(manager(), COMMITMENT_ID); // the chokepoint ran
    ArgumentCaptor<AlignmentDispute> saved = ArgumentCaptor.forClass(AlignmentDispute.class);
    verify(disputes).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(DisputeStatus.OPEN);
    assertThat(saved.getValue().getCommitmentId()).isEqualTo(COMMITMENT_ID);
    assertThat(saved.getValue().getManagerEmployeeId())
        .isEqualTo(MANAGER_ID); // opener = the manager
    assertThat(saved.getValue().getFlagType()).isEqualTo(FlagType.NEEDS_REVISION);
    assertThat(saved.getValue().getManagerNote()).isEqualTo("please re-scope this to the SO");
    verify(auditService)
        .record(
            eq("DISPUTE_OPENED"),
            eq("AlignmentDispute"),
            any(),
            eq(MANAGER_ID),
            any(),
            any()); // safe metadata only — no managerNote body asserted in the endpoint test
  }

  // --- rule #3: a denied authorize is the chokepoint — nothing loaded or persisted ----
  @Test
  void open_deniedAuthorizer_neverPersists() {
    doThrow(new ResourceNotFoundOrUnauthorizedException())
        .when(authz)
        .authorizeDisputeCreation(any(), eq(COMMITMENT_ID));

    assertThatThrownBy(() -> service.open(manager(), COMMITMENT_ID, request()))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verify(commitments, never()).findById(COMMITMENT_ID); // no load before authorization
    verify(disputes, never()).saveAndFlush(any());
  }

  // --- rule #6: an existing unresolved dispute → SecondOpenDispute (service pre-check, no save)
  // ----
  @Test
  void open_secondUnresolved_throwsSecondOpenDispute() {
    when(commitments.findById(COMMITMENT_ID)).thenReturn(Optional.of(commitment()));
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.LOCKED)));
    when(disputes.findByCommitmentIdAndStatusIn(eq(COMMITMENT_ID), any()))
        .thenReturn(Optional.of(new AlignmentDispute())); // an OPEN/IC_RESPONDED already exists

    assertThatThrownBy(() -> service.open(manager(), COMMITMENT_ID, request()))
        .isInstanceOf(SecondOpenDisputeException.class);
    verify(disputes, never()).saveAndFlush(any());
  }

  // --- rule #6 DB backstop (decomposed §11): the partial-unique race → 409 SecondOpenDispute ----
  @Test
  void open_dbBackstop_partialUniqueViolation_throwsSecondOpenDispute() {
    stubLockedCommitmentNoExistingDispute(); // pre-check passes, then the race loses at flush
    when(disputes.saveAndFlush(any()))
        .thenThrow(
            new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \""
                    + UNRESOLVED_CONSTRAINT
                    + "\""));

    assertThatThrownBy(() -> service.open(manager(), COMMITMENT_ID, request()))
        .isInstanceOf(SecondOpenDisputeException.class);
  }

  // --- the backstop is scoped: a DIFFERENT constraint violation is NOT masked as SecondOpenDispute
  @Test
  void open_dbBackstop_otherConstraint_rethrowsOriginal() {
    stubLockedCommitmentNoExistingDispute();
    when(disputes.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("some_other_fk_constraint violated"));

    assertThatThrownBy(() -> service.open(manager(), COMMITMENT_ID, request()))
        .isInstanceOf(DataIntegrityViolationException.class); // rethrown, not SecondOpenDispute
  }

  // --- state guard: a DRAFT parent plan → 409 ILLEGAL_STATE_TRANSITION ----
  @Test
  void open_planDraft_throwsIllegalStateTransition() {
    when(commitments.findById(COMMITMENT_ID)).thenReturn(Optional.of(commitment()));
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.DRAFT)));

    assertThatThrownBy(() -> service.open(manager(), COMMITMENT_ID, request()))
        .isInstanceOf(IllegalStateTransitionException.class);
    verify(disputes, never()).saveAndFlush(any());
  }

  // --- review re-derivation: an already-REVIEWED review flips to REVIEWED_WITH_DISPUTES ----
  @Test
  void open_onReviewedPlan_reDerivesReviewedWithDisputes() {
    stubLockedCommitmentNoExistingDispute();
    ManagerReview reviewed = review(ReviewStatus.REVIEWED);
    when(reviews.findByWeeklyPlanId(PLAN_ID)).thenReturn(Optional.of(reviewed));
    when(deriver.derive(PLAN_ID)).thenReturn(ReviewStatus.REVIEWED_WITH_DISPUTES);

    service.open(manager(), COMMITMENT_ID, request());

    ArgumentCaptor<ManagerReview> saved = ArgumentCaptor.forClass(ManagerReview.class);
    verify(reviews).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(ReviewStatus.REVIEWED_WITH_DISPUTES);
  }

  // --- the critical guard: opening a dispute must NOT mark a NOT_REVIEWED review reviewed ----
  @Test
  void open_onNotReviewedPlan_reviewStaysNotReviewed() {
    stubLockedCommitmentNoExistingDispute();
    when(reviews.findByWeeklyPlanId(PLAN_ID))
        .thenReturn(Optional.of(review(ReviewStatus.NOT_REVIEWED)));

    service.open(manager(), COMMITMENT_ID, request());

    verify(reviews, never()).save(any()); // a NOT_REVIEWED review is never re-derived by an open
  }
}
