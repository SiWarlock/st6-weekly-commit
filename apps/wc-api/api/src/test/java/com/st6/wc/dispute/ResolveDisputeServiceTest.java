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
import com.st6.wc.auth.AuthorizationDeniedException;
import com.st6.wc.auth.DomainAuthorizationService;
import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.dispute.mapper.DisputeMapper;
import com.st6.wc.dispute.repo.AlignmentDisputeRepository;
import com.st6.wc.enums.DisputeStatus;
import com.st6.wc.enums.FlagType;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.RoleType;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.rcdo.RcdoReadService;
import com.st6.wc.review.ManagerReview;
import com.st6.wc.review.ReviewStatusDeriver;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.web.IllegalStateTransitionException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link DisputeService#resolve} unit proof (task 5.5, §3 {@code OPEN|IC_RESPONDED→RESOLVED} / §5
 * E19 / §6 manager-capability authz / §15 audit). Authorize-first chokepoint ({@code
 * authorizeDisputeResolution} — manager-of-owner-only, the IC owner is denied), the already-{@code
 * RESOLVED} state guard, the {@code resolvedAt} stamp from the injected {@code Clock}, the
 * <strong>guarded</strong> review re-derivation (re-derive only an already-reviewed review — a
 * {@code NOT_REVIEWED} review is left untouched, the correctness crux), and the note-body-free
 * {@code DISPUTE_RESOLVED} audit. DB shapes are proven in {@code ResolveDisputeEndpointTest}.
 */
class ResolveDisputeServiceTest {

  private final DomainAuthorizationService authz = mock(DomainAuthorizationService.class);
  private final AlignmentDisputeRepository disputes = mock(AlignmentDisputeRepository.class);
  private final WeeklyCommitmentRepository commitments = mock(WeeklyCommitmentRepository.class);
  private final WeeklyPlanRepository plans = mock(WeeklyPlanRepository.class);
  private final ManagerReviewRepository reviews = mock(ManagerReviewRepository.class);
  private final ReviewStatusDeriver deriver = mock(ReviewStatusDeriver.class);
  private final DisputeMapper disputeMapper = mock(DisputeMapper.class);
  private final AuditService auditService = mock(AuditService.class);
  private final RcdoReadService rcdoReadService = mock(RcdoReadService.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-06-03T15:00:00Z"), ZoneOffset.UTC);

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
          clock);

  private static final UUID DISPUTE_ID = UUID.fromString("e0000000-0000-0000-0000-000000000001");
  private static final UUID COMMITMENT_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
  private static final UUID PLAN_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
  private static final UUID MANAGER_ID = UUID.fromString("a0000000-0000-0000-0000-0000000000aa");
  private static final UUID IC_ID = UUID.fromString("a0000000-0000-0000-0000-000000000011");

  private UserPrincipal manager() {
    return new UserPrincipal(MANAGER_ID, RoleType.MANAGER, true);
  }

  private AlignmentDispute dispute(DisputeStatus status) {
    AlignmentDispute d = new AlignmentDispute();
    d.setId(DISPUTE_ID);
    d.setCommitmentId(COMMITMENT_ID);
    d.setManagerEmployeeId(MANAGER_ID);
    d.setStatus(status);
    d.setFlagType(FlagType.MISALIGNED);
    d.setManagerNote("SENSITIVE-MGR-NOTE");
    d.setIcResponse("SENSITIVE-IC-RESPONSE");
    return d;
  }

  private WeeklyCommitment commitment() {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(COMMITMENT_ID);
    c.setWeeklyPlanId(PLAN_ID);
    return c;
  }

  private ManagerReview review(ReviewStatus status) {
    ManagerReview r = new ManagerReview();
    r.setId(UUID.fromString("c0000000-0000-0000-0000-0000000000bb"));
    r.setWeeklyPlanId(PLAN_ID);
    r.setManagerEmployeeId(MANAGER_ID);
    r.setStatus(status);
    r.setReviewDueAt(Instant.parse("2026-06-04T22:00:00Z"));
    return r;
  }

  // --- the direct manager resolves an OPEN dispute → RESOLVED + resolvedAt + DISPUTE_RESOLVED ----
  @Test
  void resolve_byDirectManager_openDispute_resolves() {
    when(disputes.findById(DISPUTE_ID)).thenReturn(Optional.of(dispute(DisputeStatus.OPEN)));
    when(commitments.findById(COMMITMENT_ID)).thenReturn(Optional.of(commitment()));
    when(reviews.findByWeeklyPlanId(PLAN_ID))
        .thenReturn(Optional.empty()); // isolate the transition

    service.resolve(manager(), DISPUTE_ID);

    verify(authz).authorizeDisputeResolution(manager(), DISPUTE_ID); // chokepoint ran
    ArgumentCaptor<AlignmentDispute> saved = ArgumentCaptor.forClass(AlignmentDispute.class);
    verify(disputes).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(DisputeStatus.RESOLVED);
    assertThat(saved.getValue().getResolvedAt()).isEqualTo(Instant.parse("2026-06-03T15:00:00Z"));
    verify(auditService)
        .record(
            eq("DISPUTE_RESOLVED"), eq("AlignmentDispute"), any(), eq(MANAGER_ID), any(), any());
  }

  // --- an IC_RESPONDED dispute is also resolvable (both unresolved pre-states) ----
  @Test
  void resolve_byDirectManager_icRespondedDispute_resolves() {
    when(disputes.findById(DISPUTE_ID))
        .thenReturn(Optional.of(dispute(DisputeStatus.IC_RESPONDED)));
    when(commitments.findById(COMMITMENT_ID)).thenReturn(Optional.of(commitment()));
    when(reviews.findByWeeklyPlanId(PLAN_ID)).thenReturn(Optional.empty());

    service.resolve(manager(), DISPUTE_ID);

    ArgumentCaptor<AlignmentDispute> saved = ArgumentCaptor.forClass(AlignmentDispute.class);
    verify(disputes).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(DisputeStatus.RESOLVED);
  }

  // --- re-derivation: resolving the LAST unresolved dispute flips REVIEWED_WITH_DISPUTES→REVIEWED
  // -
  @Test
  void resolve_lastUnresolved_reDerivesReviewedWithDisputesToReviewed() {
    when(disputes.findById(DISPUTE_ID)).thenReturn(Optional.of(dispute(DisputeStatus.OPEN)));
    when(commitments.findById(COMMITMENT_ID)).thenReturn(Optional.of(commitment()));
    when(reviews.findByWeeklyPlanId(PLAN_ID))
        .thenReturn(Optional.of(review(ReviewStatus.REVIEWED_WITH_DISPUTES)));
    when(deriver.derive(PLAN_ID)).thenReturn(ReviewStatus.REVIEWED); // count==0 after this resolve

    service.resolve(manager(), DISPUTE_ID);

    ArgumentCaptor<ManagerReview> savedReview = ArgumentCaptor.forClass(ManagerReview.class);
    verify(reviews).save(savedReview.capture());
    assertThat(savedReview.getValue().getStatus()).isEqualTo(ReviewStatus.REVIEWED);
  }

  // --- re-derivation: with another unresolved dispute remaining, the review stays WITH_DISPUTES
  // ---
  @Test
  void resolve_nonLastUnresolved_staysReviewedWithDisputes() {
    when(disputes.findById(DISPUTE_ID)).thenReturn(Optional.of(dispute(DisputeStatus.OPEN)));
    when(commitments.findById(COMMITMENT_ID)).thenReturn(Optional.of(commitment()));
    when(reviews.findByWeeklyPlanId(PLAN_ID))
        .thenReturn(Optional.of(review(ReviewStatus.REVIEWED_WITH_DISPUTES)));
    when(deriver.derive(PLAN_ID))
        .thenReturn(ReviewStatus.REVIEWED_WITH_DISPUTES); // another unresolved remains

    service.resolve(manager(), DISPUTE_ID);

    ArgumentCaptor<ManagerReview> savedReview = ArgumentCaptor.forClass(ManagerReview.class);
    verify(reviews).save(savedReview.capture());
    assertThat(savedReview.getValue().getStatus()).isEqualTo(ReviewStatus.REVIEWED_WITH_DISPUTES);
  }

  // --- CORRECTNESS crux (Q2): a NOT_REVIEWED review STAYS NOT_REVIEWED — the caller guards the
  // unconditional deriver (resolving a dispute is not a review action) ----
  @Test
  void resolve_onNotReviewedPlan_staysNotReviewed() {
    when(disputes.findById(DISPUTE_ID)).thenReturn(Optional.of(dispute(DisputeStatus.OPEN)));
    when(commitments.findById(COMMITMENT_ID)).thenReturn(Optional.of(commitment()));
    when(reviews.findByWeeklyPlanId(PLAN_ID))
        .thenReturn(Optional.of(review(ReviewStatus.NOT_REVIEWED)));

    service.resolve(manager(), DISPUTE_ID);

    verify(reviews, never()).save(any()); // the guard short-circuits before any review write
    verify(deriver, never()).derive(any()); // and before the unconditional deriver is consulted
  }

  // --- rule #3: a denied authorize is the chokepoint — nothing loaded or persisted ----
  @Test
  void resolve_deniedAuthorizer_neverPersists() {
    doThrow(new ResourceNotFoundOrUnauthorizedException())
        .when(authz)
        .authorizeDisputeResolution(any(), eq(DISPUTE_ID));

    assertThatThrownBy(() -> service.resolve(manager(), DISPUTE_ID))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verify(disputes, never()).findById(DISPUTE_ID);
    verify(disputes, never()).saveAndFlush(any());
    verify(reviews, never()).save(any());
  }

  // --- the IC owner is denied at the chokepoint (403 IC_CANNOT_RESOLVE) — nothing persisted ----
  @Test
  void resolve_byIcOwner_deniedAtChokepoint() {
    doThrow(new AuthorizationDeniedException("IC_CANNOT_RESOLVE_DISPUTE"))
        .when(authz)
        .authorizeDisputeResolution(any(), eq(DISPUTE_ID));

    assertThatThrownBy(
            () -> service.resolve(new UserPrincipal(IC_ID, RoleType.IC, false), DISPUTE_ID))
        .isInstanceOf(AuthorizationDeniedException.class);
    verify(disputes, never()).saveAndFlush(any());
  }

  // --- state guard: an already-RESOLVED dispute → 409; no re-resolve, no re-derivation ----
  @Test
  void resolve_alreadyResolved_throwsIllegalStateTransition() {
    when(disputes.findById(DISPUTE_ID)).thenReturn(Optional.of(dispute(DisputeStatus.RESOLVED)));

    assertThatThrownBy(() -> service.resolve(manager(), DISPUTE_ID))
        .isInstanceOf(IllegalStateTransitionException.class);
    verify(disputes, never()).saveAndFlush(any());
    verify(reviews, never()).save(any());
  }

  // --- §15: the DISPUTE_RESOLVED audit metadata carries ids/status only — no note bodies ----
  @Test
  void resolve_auditHasNoNoteBodies() {
    when(disputes.findById(DISPUTE_ID)).thenReturn(Optional.of(dispute(DisputeStatus.OPEN)));
    when(commitments.findById(COMMITMENT_ID)).thenReturn(Optional.of(commitment()));
    when(reviews.findByWeeklyPlanId(PLAN_ID)).thenReturn(Optional.empty());

    service.resolve(manager(), DISPUTE_ID);

    ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> metadata = ArgumentCaptor.forClass(String.class);
    verify(auditService)
        .record(
            eq("DISPUTE_RESOLVED"),
            eq("AlignmentDispute"),
            eq(DISPUTE_ID),
            eq(MANAGER_ID),
            summary.capture(),
            metadata.capture());
    assertThat(metadata.getValue()).contains("RESOLVED"); // safe status only
    assertThat(metadata.getValue()).doesNotContain("SENSITIVE-MGR-NOTE");
    assertThat(metadata.getValue()).doesNotContain("SENSITIVE-IC-RESPONSE");
    assertThat(summary.getValue()).doesNotContain("SENSITIVE-MGR-NOTE");
    assertThat(summary.getValue()).doesNotContain("SENSITIVE-IC-RESPONSE");
  }
}
