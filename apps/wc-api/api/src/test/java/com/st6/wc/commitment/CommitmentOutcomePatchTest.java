package com.st6.wc.commitment;

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
import com.st6.wc.commitment.dto.PatchCommitmentRequest;
import com.st6.wc.commitment.mapper.CommitmentMapper;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
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
import com.st6.wc.projection.ProjectionRefresher;
import com.st6.wc.rcdo.RcdoReadService;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.ManagerReview;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.web.IllegalStateTransitionException;
import com.st6.wc.web.LockedBaselineEditException;
import com.st6.wc.web.ValidationException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit proof of {@link CommitmentService#update} on the RECONCILING outcome-recording path (task
 * 4.1, §3 single-outcome rule / §5 E6 / §6 rule #3 / §9 / Appendix E Part 1). The outcome contract:
 * authorize the <strong>commitment mutation</strong> FIRST (the chokepoint), then apply a
 * <strong>per-plan-state editable-field allow-list</strong> — {@code reconciliationOutcome}/{@code
 * outcomeNote} are editable ONLY in {@code RECONCILING}; the planned baseline stays frozen in every
 * non-DRAFT state (rule #2); a direct {@code CARRIED_FORWARD} is rejected (set only via E12); the
 * {@code outcomeNote} is normalized/validated like a description; each outcome write recomputes the
 * manager projection synchronously and emits an IC audit, all in one {@code @Version}-guarded
 * transaction. Repos/authz/mapper/projection/audit mocked; end-to-end DB shapes are proven in
 * {@code CommitmentPatchDeleteEndpointTest}.
 */
class CommitmentOutcomePatchTest {

  private final DomainAuthorizationService authz = mock(DomainAuthorizationService.class);
  private final WeeklyPlanRepository plans = mock(WeeklyPlanRepository.class);
  private final WeeklyCommitmentRepository commitments = mock(WeeklyCommitmentRepository.class);
  private final RcdoReadService rcdoReadService = mock(RcdoReadService.class);
  private final CommitmentMapper commitmentMapper = mock(CommitmentMapper.class);
  private final ManagerRelationshipRepository relationships =
      mock(ManagerRelationshipRepository.class);
  private final ManagerReviewRepository reviews = mock(ManagerReviewRepository.class);
  private final ProjectionRefresher projectionRefresher = mock(ProjectionRefresher.class);
  private final AuditService auditService = mock(AuditService.class);

  private final CommitmentService service =
      new CommitmentService(
          authz,
          plans,
          commitments,
          rcdoReadService,
          commitmentMapper,
          projectionRefresher,
          auditService);

  private static final UUID IC = UUID.randomUUID();
  private static final UUID MGR = UUID.randomUUID();
  private static final UUID PLAN_ID = UUID.randomUUID();
  private static final UUID COMMITMENT_ID = UUID.randomUUID();

  private UserPrincipal actor() {
    return new UserPrincipal(IC, RoleType.IC, false);
  }

  private static WeeklyPlan plan(PlanState state) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(PLAN_ID);
    p.setEmployeeId(IC);
    p.setState(state);
    return p;
  }

  private static WeeklyCommitment commitment() {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(COMMITMENT_ID);
    c.setWeeklyPlanId(PLAN_ID);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("Original");
    c.setSupportingOutcomeId(UUID.randomUUID());
    c.setPriority(Priority.P1);
    c.setWorkType(WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(AlignmentStatus.NEEDS_REVIEW);
    return c;
  }

  private static PatchCommitmentRequest patchOutcome(ReconciliationOutcome outcome, String note) {
    PatchCommitmentRequest req = new PatchCommitmentRequest();
    req.setReconciliationOutcome(outcome);
    req.setOutcomeNote(note);
    return req;
  }

  private static PatchCommitmentRequest patchTitle(String title) {
    PatchCommitmentRequest req = new PatchCommitmentRequest();
    req.setTitle(title);
    return req;
  }

  private static PatchCommitmentRequest patchAlignment(AlignmentStatus status) {
    PatchCommitmentRequest req = new PatchCommitmentRequest();
    req.setAlignmentStatus(status);
    return req;
  }

  private static PatchCommitmentRequest patchSupportingOutcome(UUID soId) {
    PatchCommitmentRequest req = new PatchCommitmentRequest();
    req.setSupportingOutcomeId(soId);
    return req;
  }

  private void loadReconciling() {
    when(commitments.findById(COMMITMENT_ID)).thenReturn(Optional.of(commitment()));
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.RECONCILING)));
  }

  private void loadReconcilingUnplanned() {
    WeeklyCommitment c = commitment();
    c.setCommitmentKind(CommitmentKind.UNPLANNED);
    c.setWorkType(WorkType.UNPLANNED);
    c.setSupportingOutcomeId(null); // unplanned starts unlinked — the IC links it before close
    when(commitments.findById(COMMITMENT_ID)).thenReturn(Optional.of(c));
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.RECONCILING)));
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
    when(reviews.findByWeeklyPlanId(PLAN_ID)).thenReturn(Optional.of(review));
    when(commitments.findByWeeklyPlanIdOrderByIdAsc(PLAN_ID)).thenReturn(List.of(commitment()));
  }

  // --- happy: RECONCILING + owner → persist outcome + note, recompute projection, audit ----
  @Test
  void patch_outcomeInReconciling_persistsRecomputesAndAudits() {
    loadReconciling();
    withManagerAndReview();

    service.update(
        actor(), COMMITMENT_ID, patchOutcome(ReconciliationOutcome.COMPLETED, "Shipped"));

    verify(authz).authorizeCommitmentMutation(actor(), COMMITMENT_ID); // the chokepoint ran
    ArgumentCaptor<WeeklyCommitment> saved = ArgumentCaptor.forClass(WeeklyCommitment.class);
    verify(commitments).save(saved.capture());
    assertThat(saved.getValue().getReconciliationOutcome())
        .isEqualTo(ReconciliationOutcome.COMPLETED);
    assertThat(saved.getValue().getOutcomeNote()).isEqualTo("Shipped");
    verify(projectionRefresher).recomputeForPlan(any()); // §9 synchronous upsert
    verify(auditService)
        .record(
            eq("OUTCOME_RECORDED"),
            eq("WeeklyCommitment"),
            eq(COMMITMENT_ID),
            eq(IC),
            any(),
            any());
  }

  // --- no active manager → outcome saved + audit; the projection refresh is now called
  //     unconditionally (the refresher no-ops when there's no manager) ----
  @Test
  void patch_outcomeInReconciling_noManager_refreshes() {
    loadReconciling();
    when(relationships.findByDirectReportEmployeeIdAndActiveTrue(IC)).thenReturn(Optional.empty());

    service.update(actor(), COMMITMENT_ID, patchOutcome(ReconciliationOutcome.BLOCKED, null));

    verify(commitments).save(any());
    verify(auditService).record(eq("OUTCOME_RECORDED"), any(), any(), any(), any(), any());
    verify(projectionRefresher).recomputeForPlan(any());
  }

  // --- single-outcome rule (§3): a DIRECT reconciliationOutcome=CARRIED_FORWARD is rejected ----
  @Test
  void patch_directCarriedForward_throwsValidation_neverSaves() {
    loadReconciling();
    withManagerAndReview();

    assertThatThrownBy(
            () ->
                service.update(
                    actor(),
                    COMMITMENT_ID,
                    patchOutcome(ReconciliationOutcome.CARRIED_FORWARD, null)))
        .isInstanceOf(ValidationException.class);
    verify(commitments, never()).save(any());
    verify(projectionRefresher, never()).recomputeForPlan(any());
  }

  // --- per-state allow-list: outcome fields editable ONLY in RECONCILING (DRAFT/LOCKED/RECONCILED
  // →
  // 409 ILLEGAL_STATE_TRANSITION) ----
  @Test
  void patch_outcomeOutsideReconciling_throwsIllegalStateTransition() {
    for (PlanState state : List.of(PlanState.DRAFT, PlanState.LOCKED, PlanState.RECONCILED)) {
      when(commitments.findById(COMMITMENT_ID)).thenReturn(Optional.of(commitment()));
      when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(state)));
      assertThatThrownBy(
              () ->
                  service.update(
                      actor(), COMMITMENT_ID, patchOutcome(ReconciliationOutcome.COMPLETED, "x")))
          .as("outcome edit in %s must be rejected", state)
          .isInstanceOf(IllegalStateTransitionException.class);
    }
    verify(commitments, never()).save(any());
    verify(projectionRefresher, never()).recomputeForPlan(any());
  }

  // --- per-state allow-list: the planned baseline stays frozen in RECONCILING too (rule #2) ----
  @Test
  void patch_baselineFieldUnderReconciling_throwsLockedBaselineEdit() {
    loadReconciling();

    assertThatThrownBy(() -> service.update(actor(), COMMITMENT_ID, patchTitle("New title")))
        .isInstanceOf(LockedBaselineEditException.class);
    verify(commitments, never()).save(any());
  }

  // --- precedence: a RECONCILING patch touching BOTH a frozen baseline field AND an outcome →
  // baseline-first (409 LOCKED_BASELINE_EDIT); the outcome must NOT be applied (rule-#2 bypass
  // guard — the outcome-apply sits behind the baseline gate in the ordered per-state structure)
  // ----
  @Test
  void patch_baselineAndOutcomeUnderReconciling_baselineFirst_outcomeNotPersisted() {
    loadReconciling();

    PatchCommitmentRequest req = new PatchCommitmentRequest();
    req.setTitle("x"); // a frozen baseline field
    req.setReconciliationOutcome(ReconciliationOutcome.COMPLETED); // + an outcome field

    assertThatThrownBy(() -> service.update(actor(), COMMITMENT_ID, req))
        .isInstanceOf(LockedBaselineEditException.class);
    verify(commitments, never()).save(any()); // the outcome was NOT persisted
    verify(projectionRefresher, never()).recomputeForPlan(any());
  }

  // --- per-state allow-list: alignmentStatus stays read-only post-lock (incl. RECONCILING) ----
  @Test
  void patch_alignmentStatusUnderReconciling_throwsIllegalStateTransition() {
    loadReconciling();

    assertThatThrownBy(
            () -> service.update(actor(), COMMITMENT_ID, patchAlignment(AlignmentStatus.ALIGNED)))
        .isInstanceOf(IllegalStateTransitionException.class);
    verify(commitments, never()).save(any());
  }

  // --- outcomeNote validation (Appendix E Part 1, reused TextNormalizer): blank → NULL ----
  @Test
  void patch_blankOutcomeNote_normalizesToNull() {
    loadReconciling();
    withManagerAndReview();

    service.update(actor(), COMMITMENT_ID, patchOutcome(ReconciliationOutcome.COMPLETED, "   "));

    ArgumentCaptor<WeeklyCommitment> saved = ArgumentCaptor.forClass(WeeklyCommitment.class);
    verify(commitments).save(saved.capture());
    assertThat(saved.getValue().getOutcomeNote()).isNull();
  }

  // --- outcomeNote validation: 4000 code points accepted, 4001 rejected (code-point boundary) ----
  @Test
  void patch_outcomeNoteBoundary_4000Accepted_4001Rejected() {
    loadReconciling();
    withManagerAndReview();

    service.update(
        actor(), COMMITMENT_ID, patchOutcome(ReconciliationOutcome.COMPLETED, "a".repeat(4000)));
    verify(commitments).save(any());

    assertThatThrownBy(
            () ->
                service.update(
                    actor(),
                    COMMITMENT_ID,
                    patchOutcome(ReconciliationOutcome.COMPLETED, "a".repeat(4001))))
        .isInstanceOf(ValidationException.class);
  }

  // --- outcomeNote validation: C0/C1 controls stripped (multi-line normalization) ----
  @Test
  void patch_outcomeNoteControlChars_normalized() {
    loadReconciling();
    withManagerAndReview();

    String note = "ab" + (char) 1 + "cd"; // embedded C0 (SOH) control char
    service.update(
        actor(), COMMITMENT_ID, patchOutcome(ReconciliationOutcome.PARTIALLY_COMPLETED, note));

    ArgumentCaptor<WeeklyCommitment> saved = ArgumentCaptor.forClass(WeeklyCommitment.class);
    verify(commitments).save(saved.capture());
    assertThat(saved.getValue().getOutcomeNote()).isEqualTo("abcd"); // C0 stripped
  }

  // --- @Version guard: a stale-version save conflict propagates (handler → 409) ----
  @Test
  void patch_staleVersion_propagates() {
    loadReconciling();
    withManagerAndReview();
    when(commitments.save(any()))
        .thenThrow(
            new org.springframework.orm.ObjectOptimisticLockingFailureException(
                "stale", new RuntimeException()));

    assertThatThrownBy(
            () ->
                service.update(
                    actor(), COMMITMENT_ID, patchOutcome(ReconciliationOutcome.COMPLETED, "x")))
        .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
  }

  // --- rule #3: a denied mutation-authorize is the chokepoint — nothing loaded, saved, projected
  // --
  @Test
  void patch_deniedAuthorizer_neverLoadsOrSaves() {
    doThrow(new ResourceNotFoundOrUnauthorizedException())
        .when(authz)
        .authorizeCommitmentMutation(any(), eq(COMMITMENT_ID));

    assertThatThrownBy(
            () ->
                service.update(
                    actor(), COMMITMENT_ID, patchOutcome(ReconciliationOutcome.COMPLETED, "x")))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verify(commitments, never()).findById(COMMITMENT_ID);
    verify(commitments, never()).save(any());
    verify(projectionRefresher, never()).recomputeForPlan(any());
  }

  // ===================== E6 allow-list extension (4.5): unplanned SO-link in RECONCILING
  // ==========

  // --- an UNPLANNED commitment's supportingOutcomeId IS editable in RECONCILING (link before
  // close)
  @Test
  void patch_unplannedSoLink_inReconciling_applies() {
    loadReconcilingUnplanned();
    withManagerAndReview();
    UUID soId = UUID.randomUUID(); // rcdoReadService default mock returns (no throw) → valid

    service.update(actor(), COMMITMENT_ID, patchSupportingOutcome(soId));

    verify(rcdoReadService).findSupportingOutcome(soId); // validated
    ArgumentCaptor<WeeklyCommitment> saved = ArgumentCaptor.forClass(WeeklyCommitment.class);
    verify(commitments).save(saved.capture());
    assertThat(saved.getValue().getSupportingOutcomeId()).isEqualTo(soId);
  }

  // --- a PLANNED commitment's supportingOutcomeId stays FROZEN in RECONCILING (never widen, rule
  // #2)
  @Test
  void patch_plannedSoEdit_inReconciling_throwsLockedBaselineEdit() {
    loadReconciling(); // PLANNED commitment

    assertThatThrownBy(
            () -> service.update(actor(), COMMITMENT_ID, patchSupportingOutcome(UUID.randomUUID())))
        .isInstanceOf(LockedBaselineEditException.class);
    verify(commitments, never()).save(any());
  }

  // --- an unknown SO on the unplanned link → 400 VALIDATION_ERROR; nothing saved ----
  @Test
  void patch_unplannedSoLink_unknownSo_throwsValidation() {
    loadReconcilingUnplanned();
    UUID soId = UUID.randomUUID();
    when(rcdoReadService.findSupportingOutcome(soId))
        .thenThrow(new ResourceNotFoundOrUnauthorizedException());

    assertThatThrownBy(() -> service.update(actor(), COMMITMENT_ID, patchSupportingOutcome(soId)))
        .isInstanceOf(ValidationException.class);
    verify(commitments, never()).save(any());
  }
}
