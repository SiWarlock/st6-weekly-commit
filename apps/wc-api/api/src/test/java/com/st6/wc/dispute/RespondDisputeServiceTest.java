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
import com.st6.wc.dispute.dto.RespondDisputeRequest;
import com.st6.wc.dispute.mapper.DisputeMapper;
import com.st6.wc.dispute.repo.AlignmentDisputeRepository;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.DisputeStatus;
import com.st6.wc.enums.FlagType;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.WorkType;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.rcdo.RcdoReadService;
import com.st6.wc.rcdo.SupportingOutcome;
import com.st6.wc.review.ReviewStatusDeriver;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.web.IllegalStateTransitionException;
import com.st6.wc.web.ValidationException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link DisputeService#respond} unit proof (task 5.4, §3 {@code OPEN→IC_RESPONDED} / §5 E18 / §6
 * IC-owner authz / rule-#2 SO-revision exception). Authorize-first chokepoint ({@code
 * authorizeDisputeResponse} — owning-IC-only), the OPEN-only state guard, the at-least-one-of
 * {@code {icResponse, supportingOutcomeId}} rule, the rule-#2 gated exception (revise ONLY the
 * disputed commitment's {@code supportingOutcomeId}, no other baseline field), and NO
 * review-re-derivation / NO projection change (the dispute stays unresolved). DB shapes are proven
 * in {@code RespondDisputeEndpointTest}.
 */
class RespondDisputeServiceTest {

  private final DomainAuthorizationService authz = mock(DomainAuthorizationService.class);
  private final AlignmentDisputeRepository disputes = mock(AlignmentDisputeRepository.class);
  private final WeeklyCommitmentRepository commitments = mock(WeeklyCommitmentRepository.class);
  private final WeeklyPlanRepository plans = mock(WeeklyPlanRepository.class);
  private final ManagerReviewRepository reviews = mock(ManagerReviewRepository.class);
  private final ReviewStatusDeriver deriver = mock(ReviewStatusDeriver.class);
  private final DisputeMapper disputeMapper = mock(DisputeMapper.class);
  private final AuditService auditService = mock(AuditService.class);
  private final RcdoReadService rcdoReadService = mock(RcdoReadService.class);

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
          rcdoReadService);

  private static final UUID DISPUTE_ID = UUID.fromString("e0000000-0000-0000-0000-000000000001");
  private static final UUID COMMITMENT_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
  private static final UUID IC_ID = UUID.fromString("a0000000-0000-0000-0000-000000000011");
  private static final UUID SO_OLD = UUID.fromString("c0000000-0000-0000-0000-000000000001");
  private static final UUID SO_NEW = UUID.fromString("c0000000-0000-0000-0000-000000000002");

  private UserPrincipal ic() {
    return new UserPrincipal(IC_ID, RoleType.IC, false);
  }

  private AlignmentDispute dispute(DisputeStatus status) {
    AlignmentDispute d = new AlignmentDispute();
    d.setId(DISPUTE_ID);
    d.setCommitmentId(COMMITMENT_ID);
    d.setManagerEmployeeId(UUID.randomUUID());
    d.setStatus(status);
    d.setFlagType(FlagType.MISALIGNED);
    d.setManagerNote("please re-scope");
    return d;
  }

  private WeeklyCommitment commitment() {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(COMMITMENT_ID);
    c.setWeeklyPlanId(UUID.randomUUID());
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("Draft the runbook");
    c.setDescription("the runbook detail");
    c.setSupportingOutcomeId(SO_OLD);
    c.setPriority(Priority.P1);
    c.setWorkType(WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(AlignmentStatus.ALIGNED);
    return c;
  }

  // --- owning IC responds with rationale → OPEN→IC_RESPONDED, icResponse set, audit; no SO touch
  // --
  @Test
  void respond_byOwningIc_withRationale_transitionsToIcResponded() {
    when(disputes.findById(DISPUTE_ID)).thenReturn(Optional.of(dispute(DisputeStatus.OPEN)));

    service.respond(ic(), DISPUTE_ID, new RespondDisputeRequest("here is my rationale", null));

    verify(authz).authorizeDisputeResponse(ic(), DISPUTE_ID); // chokepoint ran
    ArgumentCaptor<AlignmentDispute> saved = ArgumentCaptor.forClass(AlignmentDispute.class);
    verify(disputes).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(DisputeStatus.IC_RESPONDED);
    assertThat(saved.getValue().getIcResponse()).isEqualTo("here is my rationale");
    verify(commitments, never()).save(any()); // no SO revision → commitment untouched
    verify(auditService)
        .record(eq("DISPUTE_RESPONDED"), eq("AlignmentDispute"), any(), eq(IC_ID), any(), any());
  }

  // --- SO revision (rule-#2 exception): the disputed commitment's supportingOutcomeId is updated
  // --
  @Test
  void respond_revisesSupportingOutcome_updatesCommitmentSo() {
    when(disputes.findById(DISPUTE_ID)).thenReturn(Optional.of(dispute(DisputeStatus.OPEN)));
    when(rcdoReadService.findSupportingOutcome(SO_NEW)).thenReturn(mock(SupportingOutcome.class));
    when(commitments.findById(COMMITMENT_ID)).thenReturn(Optional.of(commitment()));

    service.respond(ic(), DISPUTE_ID, new RespondDisputeRequest(null, SO_NEW));

    ArgumentCaptor<WeeklyCommitment> savedC = ArgumentCaptor.forClass(WeeklyCommitment.class);
    verify(commitments).save(savedC.capture());
    assertThat(savedC.getValue().getSupportingOutcomeId()).isEqualTo(SO_NEW);
    ArgumentCaptor<AlignmentDispute> savedD = ArgumentCaptor.forClass(AlignmentDispute.class);
    verify(disputes).save(savedD.capture());
    assertThat(savedD.getValue().getStatus()).isEqualTo(DisputeStatus.IC_RESPONDED);
  }

  // --- SAFETY pin: the rule-#2 exception touches ONLY supportingOutcomeId — all else frozen ----
  @Test
  void respond_doesNotTouchOtherBaselineFields() {
    when(disputes.findById(DISPUTE_ID)).thenReturn(Optional.of(dispute(DisputeStatus.OPEN)));
    when(rcdoReadService.findSupportingOutcome(SO_NEW)).thenReturn(mock(SupportingOutcome.class));
    when(commitments.findById(COMMITMENT_ID)).thenReturn(Optional.of(commitment()));

    service.respond(ic(), DISPUTE_ID, new RespondDisputeRequest(null, SO_NEW));

    ArgumentCaptor<WeeklyCommitment> savedC = ArgumentCaptor.forClass(WeeklyCommitment.class);
    verify(commitments).save(savedC.capture());
    WeeklyCommitment after = savedC.getValue();
    assertThat(after.getSupportingOutcomeId()).isEqualTo(SO_NEW); // the ONLY change
    assertThat(after.getTitle()).isEqualTo("Draft the runbook");
    assertThat(after.getDescription()).isEqualTo("the runbook detail");
    assertThat(after.getPriority()).isEqualTo(Priority.P1);
    assertThat(after.getWorkType()).isEqualTo(WorkType.STRATEGIC);
    assertThat(after.getConfidence()).isEqualTo(Confidence.MEDIUM);
    assertThat(after.getAlignmentStatus()).isEqualTo(AlignmentStatus.ALIGNED);
  }

  // --- rule #3: a denied authorize is the chokepoint — nothing loaded or persisted ----
  @Test
  void respond_deniedAuthorizer_neverPersists() {
    doThrow(new ResourceNotFoundOrUnauthorizedException())
        .when(authz)
        .authorizeDisputeResponse(any(), eq(DISPUTE_ID));

    assertThatThrownBy(
            () -> service.respond(ic(), DISPUTE_ID, new RespondDisputeRequest("x", null)))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verify(disputes, never()).findById(DISPUTE_ID);
    verify(disputes, never()).save(any());
    verify(commitments, never()).save(any());
  }

  // --- state guard: respond is one-shot OPEN→IC_RESPONDED; a non-OPEN dispute → 409 ----
  @Test
  void respond_nonOpenDispute_throwsIllegalStateTransition() {
    when(disputes.findById(DISPUTE_ID))
        .thenReturn(Optional.of(dispute(DisputeStatus.IC_RESPONDED)));

    assertThatThrownBy(
            () -> service.respond(ic(), DISPUTE_ID, new RespondDisputeRequest("x", null)))
        .isInstanceOf(IllegalStateTransitionException.class);
    verify(disputes, never()).save(any());
  }

  // --- at-least-one-of {icResponse, supportingOutcomeId}: neither → 400 VALIDATION_ERROR ----
  @Test
  void respond_emptyRequest_throwsValidation() {
    when(disputes.findById(DISPUTE_ID)).thenReturn(Optional.of(dispute(DisputeStatus.OPEN)));

    assertThatThrownBy(
            () -> service.respond(ic(), DISPUTE_ID, new RespondDisputeRequest(null, null)))
        .isInstanceOf(ValidationException.class);
    verify(disputes, never()).save(any());
    verify(commitments, never()).save(any());
  }

  // --- an unknown supportingOutcomeId → 400 VALIDATION_ERROR; nothing persisted ----
  @Test
  void respond_unknownSupportingOutcome_throwsValidation() {
    when(disputes.findById(DISPUTE_ID)).thenReturn(Optional.of(dispute(DisputeStatus.OPEN)));
    when(rcdoReadService.findSupportingOutcome(SO_NEW))
        .thenThrow(new ResourceNotFoundOrUnauthorizedException());

    assertThatThrownBy(
            () -> service.respond(ic(), DISPUTE_ID, new RespondDisputeRequest(null, SO_NEW)))
        .isInstanceOf(ValidationException.class);
    verify(commitments, never()).save(any());
    verify(disputes, never()).save(any());
  }

  // --- no-side-effect: respond does NOT re-derive the review or touch the projection ----
  @Test
  void respond_doesNotReDeriveReviewOrTouchProjection() {
    when(disputes.findById(DISPUTE_ID)).thenReturn(Optional.of(dispute(DisputeStatus.OPEN)));

    service.respond(ic(), DISPUTE_ID, new RespondDisputeRequest("rationale", null));

    verify(reviews, never()).save(any()); // dispute stays unresolved → review untouched
    verify(deriver, never()).derive(any());
  }
}
