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

import com.st6.wc.auth.DomainAuthorizationService;
import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.commitment.dto.CreateCommitmentRequest;
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
import com.st6.wc.rcdo.RcdoReadService;
import com.st6.wc.web.IllegalStateTransitionException;
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
  private final CommitmentService service =
      new CommitmentService(authz, plans, commitments, rcdoReadService, commitmentMapper);

  private static final UUID ACTOR = UUID.randomUUID();
  private static final UUID PLAN_ID = UUID.randomUUID();

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

    verify(authz).authorizePlanAccess(actor(), PLAN_ID); // the chokepoint ran
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
        .authorizePlanAccess(any(), eq(PLAN_ID));

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
}
