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
import com.st6.wc.commitment.dto.CreateCommitmentRequest;
import com.st6.wc.commitment.dto.PatchCommitmentRequest;
import com.st6.wc.commitment.mapper.CommitmentMapper;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.WorkType;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.ProjectionService;
import com.st6.wc.rcdo.RcdoReadService;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.web.IllegalStateTransitionException;
import com.st6.wc.web.LockedBaselineEditException;
import com.st6.wc.web.ValidationException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit proof of {@link CommitmentService#create} (task 3.4a, §5 E5 / §6 rule #3 / Appendix B.6).
 * The create flow: authorize the <strong>parent plan</strong> FIRST (the chokepoint — no write
 * before authorization), require the plan be {@code DRAFT} (else 409), reject {@code
 * workType=UNPLANNED} (planned-only endpoint → 400), validate an optional Supporting-Outcome link
 * (unknown → 400), force {@code commitmentKind=PLANNED}, persist, map. Repos/authz/mapper mocked.
 */
class CommitmentServiceTest {

  private final DomainAuthorizationService authz = mock(DomainAuthorizationService.class);
  private final WeeklyPlanRepository plans = mock(WeeklyPlanRepository.class);
  private final WeeklyCommitmentRepository commitments = mock(WeeklyCommitmentRepository.class);
  private final RcdoReadService rcdoReadService = mock(RcdoReadService.class);
  private final CommitmentMapper commitmentMapper = mock(CommitmentMapper.class);
  private final ManagerRelationshipRepository relationships =
      mock(ManagerRelationshipRepository.class);
  private final ManagerReviewRepository reviews = mock(ManagerReviewRepository.class);
  private final ProjectionService projectionService = mock(ProjectionService.class);
  private final AuditService auditService = mock(AuditService.class);
  private final CommitmentService service =
      new CommitmentService(
          authz,
          plans,
          commitments,
          rcdoReadService,
          commitmentMapper,
          relationships,
          reviews,
          projectionService,
          auditService);

  private static final UUID ACTOR = UUID.randomUUID();
  private static final UUID PLAN_ID = UUID.randomUUID();
  private static final UUID COMMITMENT_ID = UUID.randomUUID();

  private UserPrincipal actor() {
    return new UserPrincipal(ACTOR, RoleType.IC, false);
  }

  private static WeeklyPlan plan(PlanState state) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(PLAN_ID);
    p.setEmployeeId(ACTOR);
    p.setState(state);
    return p;
  }

  private static WeeklyCommitment commitment() {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(COMMITMENT_ID);
    c.setWeeklyPlanId(PLAN_ID);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("Original");
    c.setPriority(Priority.P1);
    c.setWorkType(WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(AlignmentStatus.NEEDS_REVIEW);
    return c;
  }

  private static PatchCommitmentRequest patchTitlePriority(String title, Priority prio) {
    PatchCommitmentRequest req = new PatchCommitmentRequest();
    req.setTitle(title);
    req.setPriority(prio);
    return req;
  }

  private static PatchCommitmentRequest patchSupportingOutcome(UUID soId) {
    PatchCommitmentRequest req = new PatchCommitmentRequest();
    req.setSupportingOutcomeId(soId);
    return req;
  }

  private static PatchCommitmentRequest patchAlignment(AlignmentStatus status) {
    PatchCommitmentRequest req = new PatchCommitmentRequest();
    req.setAlignmentStatus(status);
    return req;
  }

  private static PatchCommitmentRequest patchWorkType(WorkType workType) {
    PatchCommitmentRequest req = new PatchCommitmentRequest();
    req.setWorkType(workType);
    return req;
  }

  private static CreateCommitmentRequest request(WorkType workType, UUID soId) {
    return new CreateCommitmentRequest(
        "Ship the thing",
        "desc",
        soId,
        Priority.P1,
        workType,
        Confidence.MEDIUM,
        AlignmentStatus.ALIGNED);
  }

  // --- authorize-first chokepoint, then persist a PLANNED commitment ----
  @Test
  void create_authorizesThenPersistsPlannedCommitment() {
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.DRAFT)));

    service.create(actor(), PLAN_ID, request(WorkType.STRATEGIC, null));

    verify(authz).authorizePlanMutation(actor(), PLAN_ID); // owner-only chokepoint (NOT planAccess)
    ArgumentCaptor<WeeklyCommitment> saved = ArgumentCaptor.forClass(WeeklyCommitment.class);
    verify(commitments).save(saved.capture());
    assertThat(saved.getValue().getCommitmentKind()).isEqualTo(CommitmentKind.PLANNED); // forced
    assertThat(saved.getValue().getWeeklyPlanId()).isEqualTo(PLAN_ID);
    assertThat(saved.getValue().getTitle()).isEqualTo("Ship the thing");
  }

  // --- rule #3: a denied authorize is the chokepoint — nothing is persisted ----
  @Test
  void create_deniedAuthorizer_neverPersists() {
    doThrow(new ResourceNotFoundOrUnauthorizedException())
        .when(authz)
        .authorizePlanMutation(any(), eq(PLAN_ID));

    assertThatThrownBy(() -> service.create(actor(), PLAN_ID, request(WorkType.STRATEGIC, null)))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verify(commitments, never()).save(any());
    verify(plans, never()).findById(PLAN_ID); // no plan load before authorization
  }

  // --- planned-only endpoint: workType=UNPLANNED → 400 VALIDATION_ERROR ----
  @Test
  void create_unplannedWorkType_throwsValidation() {
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.DRAFT)));

    assertThatThrownBy(() -> service.create(actor(), PLAN_ID, request(WorkType.UNPLANNED, null)))
        .isInstanceOf(ValidationException.class);
    verify(commitments, never()).save(any());
  }

  // --- create requires DRAFT: a non-DRAFT plan → 409 ILLEGAL_STATE_TRANSITION ----
  @Test
  void create_nonDraftPlan_throwsIllegalStateTransition() {
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.LOCKED)));

    assertThatThrownBy(() -> service.create(actor(), PLAN_ID, request(WorkType.STRATEGIC, null)))
        .isInstanceOf(IllegalStateTransitionException.class);
    verify(commitments, never()).save(any());
  }

  // --- unknown supportingOutcomeId → 400 VALIDATION_ERROR (invalid request input, Q2) ----
  @Test
  void create_unknownSupportingOutcome_throwsValidation() {
    UUID soId = UUID.randomUUID();
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.DRAFT)));
    when(rcdoReadService.findSupportingOutcome(soId))
        .thenThrow(new ResourceNotFoundOrUnauthorizedException());

    assertThatThrownBy(() -> service.create(actor(), PLAN_ID, request(WorkType.STRATEGIC, soId)))
        .isInstanceOf(ValidationException.class);
    verify(commitments, never()).save(any());
  }

  // ===================== update (E6) =====================

  // --- authorize-mutation chokepoint, then apply ONLY the provided fields ----
  @Test
  void update_authorizesThenAppliesProvidedFields() {
    when(commitments.findById(COMMITMENT_ID)).thenReturn(Optional.of(commitment()));
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.DRAFT)));

    service.update(actor(), COMMITMENT_ID, patchTitlePriority("New title", Priority.P0));

    verify(authz).authorizeCommitmentMutation(actor(), COMMITMENT_ID); // the chokepoint ran
    ArgumentCaptor<WeeklyCommitment> saved = ArgumentCaptor.forClass(WeeklyCommitment.class);
    verify(commitments).save(saved.capture());
    assertThat(saved.getValue().getTitle()).isEqualTo("New title");
    assertThat(saved.getValue().getPriority()).isEqualTo(Priority.P0);
    assertThat(saved.getValue().getConfidence()).isEqualTo(Confidence.MEDIUM); // untouched
  }

  // --- rule #3: a denied mutation-authorize is the chokepoint — nothing is loaded or saved ----
  @Test
  void update_deniedAuthorizer_neverLoadsOrSaves() {
    doThrow(new ResourceNotFoundOrUnauthorizedException())
        .when(authz)
        .authorizeCommitmentMutation(any(), eq(COMMITMENT_ID));

    assertThatThrownBy(
            () -> service.update(actor(), COMMITMENT_ID, patchTitlePriority("x", Priority.P1)))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verify(commitments, never()).findById(COMMITMENT_ID); // no load before authorization
    verify(commitments, never()).save(any());
  }

  // --- rule #2: a baseline-field edit on a LOCKED plan → LockedBaselineEditException ----
  @Test
  void update_lockedPlan_baselineField_throwsLockedBaselineEdit() {
    when(commitments.findById(COMMITMENT_ID)).thenReturn(Optional.of(commitment()));
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.LOCKED)));

    assertThatThrownBy(
            () -> service.update(actor(), COMMITMENT_ID, patchTitlePriority("x", Priority.P1)))
        .isInstanceOf(LockedBaselineEditException.class);
    verify(commitments, never()).save(any());
  }

  // --- alignmentStatus post-lock is read-only → ILLEGAL_STATE_TRANSITION (distinct from baseline)
  // -
  @Test
  void update_lockedPlan_alignmentStatus_throwsIllegalStateTransition() {
    when(commitments.findById(COMMITMENT_ID)).thenReturn(Optional.of(commitment()));
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.LOCKED)));

    assertThatThrownBy(
            () -> service.update(actor(), COMMITMENT_ID, patchAlignment(AlignmentStatus.ALIGNED)))
        .isInstanceOf(IllegalStateTransitionException.class);
    verify(commitments, never()).save(any());
  }

  // --- unknown supportingOutcomeId on a re-link → 400 VALIDATION_ERROR ----
  @Test
  void update_unknownSupportingOutcome_throwsValidation() {
    UUID soId = UUID.randomUUID();
    when(commitments.findById(COMMITMENT_ID)).thenReturn(Optional.of(commitment()));
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.DRAFT)));
    when(rcdoReadService.findSupportingOutcome(soId))
        .thenThrow(new ResourceNotFoundOrUnauthorizedException());

    assertThatThrownBy(() -> service.update(actor(), COMMITMENT_ID, patchSupportingOutcome(soId)))
        .isInstanceOf(ValidationException.class);
    verify(commitments, never()).save(any());
  }

  // --- planned-only on PATCH too: workType=UNPLANNED → 400 VALIDATION_ERROR (UNPLANNED is E11)
  // ----
  @Test
  void update_unplannedWorkType_throwsValidation() {
    when(commitments.findById(COMMITMENT_ID)).thenReturn(Optional.of(commitment()));
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.DRAFT)));

    assertThatThrownBy(
            () -> service.update(actor(), COMMITMENT_ID, patchWorkType(WorkType.UNPLANNED)))
        .isInstanceOf(ValidationException.class);
    verify(commitments, never()).save(any());
  }

  // ===================== delete (E7) =====================

  // --- authorize-mutation chokepoint, then delete a DRAFT-plan commitment ----
  @Test
  void delete_authorizesThenDeletes() {
    WeeklyCommitment c = commitment();
    when(commitments.findById(COMMITMENT_ID)).thenReturn(Optional.of(c));
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.DRAFT)));

    service.discard(actor(), COMMITMENT_ID);

    verify(authz).authorizeCommitmentMutation(actor(), COMMITMENT_ID);
    verify(commitments).delete(c);
  }

  // --- rule #3: a denied delete-authorize is the chokepoint — nothing is loaded or deleted ----
  @Test
  void delete_deniedAuthorizer_neverLoadsOrDeletes() {
    doThrow(new ResourceNotFoundOrUnauthorizedException())
        .when(authz)
        .authorizeCommitmentMutation(any(), eq(COMMITMENT_ID));

    assertThatThrownBy(() -> service.discard(actor(), COMMITMENT_ID))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verify(commitments, never()).findById(COMMITMENT_ID);
    verify(commitments, never()).delete(any());
  }

  // --- delete requires DRAFT: a non-DRAFT plan → 409 ILLEGAL_STATE_TRANSITION ----
  @Test
  void delete_nonDraftPlan_throwsIllegalStateTransition() {
    when(commitments.findById(COMMITMENT_ID)).thenReturn(Optional.of(commitment()));
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.LOCKED)));

    assertThatThrownBy(() -> service.discard(actor(), COMMITMENT_ID))
        .isInstanceOf(IllegalStateTransitionException.class);
    verify(commitments, never()).delete(any());
  }
}
