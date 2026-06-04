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
import com.st6.wc.auth.AuthorizationDeniedException;
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
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.WorkType;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.ProjectionRefresher;
import com.st6.wc.rcdo.RcdoReadService;
import com.st6.wc.web.IllegalStateTransitionException;
import com.st6.wc.web.ValidationException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit proof of the E6 {@code managerAlignmentNote} write (task 5.7, §3 / §6 rule #3 §33 manager-
 * capability / §15). The one manager-owned, post-lock-mutable commitment field: a {@code
 * managerAlignmentNote}-providing patch routes to the <strong>manager-of-owner</strong> authz
 * ({@code authorizeManagerAlignmentNote} — the inverse of every other IC-owner-only E6 field),
 * rejects mixing with any IC field (single-actor-per-patch → 400), guards {@code LOCKED}+ (DRAFT →
 * 409), validates/normalizes (§26), and emits a note-body-free {@code COMMITMENT_ALIGNMENT_NOTED}
 * audit. The IC path is untouched. Authz/repos/mapper mocked; DB shapes in {@code
 * ManagerAlignmentNoteEndpointTest}.
 */
class ManagerAlignmentNoteServiceTest {

  private final DomainAuthorizationService authz = mock(DomainAuthorizationService.class);
  private final WeeklyPlanRepository plans = mock(WeeklyPlanRepository.class);
  private final WeeklyCommitmentRepository commitments = mock(WeeklyCommitmentRepository.class);
  private final RcdoReadService rcdoReadService = mock(RcdoReadService.class);
  private final CommitmentMapper commitmentMapper = mock(CommitmentMapper.class);
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

  private static final UUID MANAGER_ID = UUID.fromString("a0000000-0000-0000-0000-0000000000aa");
  private static final UUID IC_ID = UUID.fromString("a0000000-0000-0000-0000-000000000011");
  private static final UUID PLAN_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
  private static final UUID COMMITMENT_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");

  private UserPrincipal manager() {
    return new UserPrincipal(MANAGER_ID, RoleType.MANAGER, true);
  }

  private static WeeklyPlan plan(PlanState state) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(PLAN_ID);
    p.setEmployeeId(IC_ID); // the plan is owned by the IC; the manager is the direct manager
    p.setState(state);
    return p;
  }

  private static WeeklyCommitment commitment() {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(COMMITMENT_ID);
    c.setWeeklyPlanId(PLAN_ID);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("Original title");
    c.setDescription("original description");
    c.setSupportingOutcomeId(UUID.fromString("c0000000-0000-0000-0000-000000000001"));
    c.setPriority(Priority.P1);
    c.setWorkType(WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(AlignmentStatus.ALIGNED);
    return c;
  }

  private static PatchCommitmentRequest patchNote(String note) {
    PatchCommitmentRequest req = new PatchCommitmentRequest();
    req.setManagerAlignmentNote(note);
    return req;
  }

  private void stubManagerNotePath(PlanState state) {
    when(commitments.findById(COMMITMENT_ID)).thenReturn(Optional.of(commitment()));
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(state)));
  }

  // --- the direct manager sets managerAlignmentNote on a LOCKED plan → set + audit + save ----
  @Test
  void managerNote_byDirectManager_lockedPlan_sets() {
    stubManagerNotePath(PlanState.LOCKED);

    service.update(manager(), COMMITMENT_ID, patchNote("revisit the alignment here"));

    verify(authz)
        .authorizeManagerAlignmentNote(manager(), COMMITMENT_ID); // manager-of-owner chokepoint
    verify(authz, never()).authorizeCommitmentMutation(any(), any()); // NOT the IC path
    ArgumentCaptor<WeeklyCommitment> saved = ArgumentCaptor.forClass(WeeklyCommitment.class);
    verify(commitments).save(saved.capture());
    assertThat(saved.getValue().getManagerAlignmentNote()).isEqualTo("revisit the alignment here");
    verify(auditService)
        .record(
            eq("COMMITMENT_ALIGNMENT_NOTED"),
            eq("WeeklyCommitment"),
            eq(COMMITMENT_ID),
            eq(MANAGER_ID),
            any(),
            any());
  }

  // --- post-lock-mutable: LOCKED, RECONCILING, RECONCILED all allowed ----
  @Test
  void managerNote_postLockStates_allowed() {
    for (PlanState state :
        new PlanState[] {PlanState.LOCKED, PlanState.RECONCILING, PlanState.RECONCILED}) {
      stubManagerNotePath(state);
      service.update(manager(), COMMITMENT_ID, patchNote("note for " + state));
    }
    verify(commitments, org.mockito.Mockito.times(3)).save(any());
  }

  // --- state guard: DRAFT → 409 (manager-owned fields are post-lock only, REQ-F-010); never save
  // --
  @Test
  void managerNote_onDraftPlan_throwsIllegalStateTransition() {
    stubManagerNotePath(PlanState.DRAFT);

    assertThatThrownBy(() -> service.update(manager(), COMMITMENT_ID, patchNote("nope")))
        .isInstanceOf(IllegalStateTransitionException.class);
    verify(commitments, never()).save(any());
    verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
  }

  // --- mixing rejection (Q2): managerAlignmentNote + any IC field → 400; manager authz NOT reached
  @Test
  void managerNote_mixedWithIcField_throwsValidation() {
    PatchCommitmentRequest mixed = patchNote("a note");
    mixed.setTitle("also editing the title"); // an IC-owned field

    assertThatThrownBy(() -> service.update(manager(), COMMITMENT_ID, mixed))
        .isInstanceOf(ValidationException.class);
    verify(authz, never()).authorizeManagerAlignmentNote(any(), any());
    verify(commitments, never()).save(any());
  }

  // --- rule #3: a denied authorize is the chokepoint — nothing loaded or persisted ----
  @Test
  void managerNote_deniedAuthorizer_neverPersists() {
    doThrow(new ResourceNotFoundOrUnauthorizedException())
        .when(authz)
        .authorizeManagerAlignmentNote(any(), eq(COMMITMENT_ID));

    assertThatThrownBy(() -> service.update(manager(), COMMITMENT_ID, patchNote("x")))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verify(commitments, never()).findById(COMMITMENT_ID);
    verify(commitments, never()).save(any());
  }

  // --- the IC-owner is denied the manager-only field at the chokepoint (403) — nothing persisted
  // --
  @Test
  void managerNote_byIcOwner_deniedAtChokepoint() {
    doThrow(new AuthorizationDeniedException("IC_CANNOT_WRITE_MANAGER_NOTE"))
        .when(authz)
        .authorizeManagerAlignmentNote(any(), eq(COMMITMENT_ID));

    assertThatThrownBy(
            () ->
                service.update(
                    new UserPrincipal(IC_ID, RoleType.IC, false), COMMITMENT_ID, patchNote("mine")))
        .isInstanceOf(AuthorizationDeniedException.class);
    verify(commitments, never()).save(any());
  }

  // --- present-null clears the note (the manager retracts it) ----
  @Test
  void managerNote_clearsOnPresentNull() {
    stubManagerNotePath(PlanState.LOCKED);

    service.update(manager(), COMMITMENT_ID, patchNote(null));

    ArgumentCaptor<WeeklyCommitment> saved = ArgumentCaptor.forClass(WeeklyCommitment.class);
    verify(commitments).save(saved.capture());
    assertThat(saved.getValue().getManagerAlignmentNote()).isNull();
  }

  // --- validation: >4000 code points → 400; never save ----
  @Test
  void managerNote_tooLong_throwsValidation() {
    stubManagerNotePath(PlanState.LOCKED);

    assertThatThrownBy(() -> service.update(manager(), COMMITMENT_ID, patchNote("x".repeat(4001))))
        .isInstanceOf(ValidationException.class);
    verify(commitments, never()).save(any());
  }

  // --- SAFETY: the manager note write touches ONLY managerAlignmentNote — baseline byte-identical
  // --
  @Test
  void managerNote_doesNotTouchBaseline() {
    stubManagerNotePath(PlanState.LOCKED);

    service.update(manager(), COMMITMENT_ID, patchNote("the note"));

    ArgumentCaptor<WeeklyCommitment> saved = ArgumentCaptor.forClass(WeeklyCommitment.class);
    verify(commitments).save(saved.capture());
    WeeklyCommitment after = saved.getValue();
    assertThat(after.getManagerAlignmentNote()).isEqualTo("the note");
    assertThat(after.getTitle()).isEqualTo("Original title");
    assertThat(after.getDescription()).isEqualTo("original description");
    assertThat(after.getSupportingOutcomeId())
        .isEqualTo(UUID.fromString("c0000000-0000-0000-0000-000000000001"));
    assertThat(after.getPriority()).isEqualTo(Priority.P1);
    assertThat(after.getWorkType()).isEqualTo(WorkType.STRATEGIC);
    assertThat(after.getConfidence()).isEqualTo(Confidence.MEDIUM);
    assertThat(after.getAlignmentStatus()).isEqualTo(AlignmentStatus.ALIGNED);
  }

  // --- §15: the COMMITMENT_ALIGNMENT_NOTED audit carries NO note body ----
  @Test
  void managerNote_auditHasNoNoteBody() {
    stubManagerNotePath(PlanState.LOCKED);

    service.update(manager(), COMMITMENT_ID, patchNote("SENSITIVE-MANAGER-NOTE"));

    ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> metadata = ArgumentCaptor.forClass(String.class);
    verify(auditService)
        .record(
            eq("COMMITMENT_ALIGNMENT_NOTED"),
            any(),
            any(),
            any(),
            summary.capture(),
            metadata.capture());
    assertThat(metadata.getValue()).doesNotContain("SENSITIVE-MANAGER-NOTE");
    assertThat(summary.getValue()).doesNotContain("SENSITIVE-MANAGER-NOTE");
  }

  // --- no-regression: a pure IC patch (no managerAlignmentNote) routes to the IC path, untouched
  // --
  @Test
  void icPatch_noManagerNote_usesIcPath() {
    when(commitments.findById(COMMITMENT_ID)).thenReturn(Optional.of(commitment()));
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.DRAFT)));
    PatchCommitmentRequest icPatch = new PatchCommitmentRequest();
    icPatch.setTitle("new IC title");

    service.update(new UserPrincipal(IC_ID, RoleType.IC, false), COMMITMENT_ID, icPatch);

    verify(authz).authorizeCommitmentMutation(any(), eq(COMMITMENT_ID)); // the IC path
    verify(authz, never()).authorizeManagerAlignmentNote(any(), any());
  }
}
