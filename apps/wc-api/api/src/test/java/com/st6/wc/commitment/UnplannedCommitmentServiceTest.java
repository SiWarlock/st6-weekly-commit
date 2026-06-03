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
import com.st6.wc.commitment.dto.CreateUnplannedCommitmentRequest;
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
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.ManagerReview;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.web.IllegalStateTransitionException;
import com.st6.wc.web.ValidationException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit proof of {@link CommitmentService#createUnplanned} (task 4.3, §3 UNPLANNED labeled / §5 E11
 * / §6 rule #3 / §9 / REQ-F-025/026). The E11 create: authorize the parent plan <strong>owner-only
 * </strong> FIRST via {@link DomainAuthorizationService#authorizePlanMutation} (the chokepoint — a
 * manager-direct-report can READ the locked plan but cannot author on it → 403; cross-team/missing
 * → 404), require the plan be {@code LOCKED} or {@code RECONCILING} (else 409), validate an
 * optional Supporting-Outcome link (unknown → 400), <strong>server-force</strong> {@code
 * commitment_kind=UNPLANNED} + {@code work_type=UNPLANNED} (the inverse of the E5 planned-only
 * create), persist, recompute the manager projection (§9 {@code unplanned_count}), emit an IC
 * audit, and map. Repos/authz/mapper/projection/audit mocked; end-to-end DB shapes + REQ-F-025
 * baseline-unmutated are proven in {@code UnplannedCommitmentEndpointTest}.
 */
class UnplannedCommitmentServiceTest {

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

  private static final UUID IC = UUID.randomUUID();
  private static final UUID MGR = UUID.randomUUID();
  private static final UUID PLAN_ID = UUID.randomUUID();

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

  private static CreateUnplannedCommitmentRequest request(UUID soId) {
    return new CreateUnplannedCommitmentRequest(
        "Hotfix the incident",
        "desc",
        soId,
        Priority.P1,
        Confidence.MEDIUM,
        AlignmentStatus.ALIGNED);
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
    when(commitments.findByWeeklyPlanIdOrderByIdAsc(PLAN_ID)).thenReturn(List.of());
  }

  // --- happy: RECONCILING + owner → server-forces UNPLANNED kind+type, recomputes projection,
  // audit
  @Test
  void createUnplanned_inReconciling_forcesUnplanned_recomputes_audits() {
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.RECONCILING)));
    withManagerAndReview();

    service.createUnplanned(actor(), PLAN_ID, request(null));

    verify(authz).authorizePlanMutation(actor(), PLAN_ID); // owner-only chokepoint (NOT planAccess)
    ArgumentCaptor<WeeklyCommitment> saved = ArgumentCaptor.forClass(WeeklyCommitment.class);
    verify(commitments).save(saved.capture());
    assertThat(saved.getValue().getCommitmentKind()).isEqualTo(CommitmentKind.UNPLANNED); // forced
    assertThat(saved.getValue().getWorkType()).isEqualTo(WorkType.UNPLANNED); // forced
    assertThat(saved.getValue().getWeeklyPlanId()).isEqualTo(PLAN_ID);
    verify(projectionService).recompute(any(), eq(MGR), any(), any()); // §9 unplanned_count upsert
    verify(auditService)
        .record(
            eq("UNPLANNED_COMMITMENT_CREATED"),
            eq("WeeklyCommitment"),
            any(),
            eq(IC),
            any(),
            any());
  }

  // --- also allowed in LOCKED (unplanned work surfaces during the locked week, not only
  // reconciling)
  @Test
  void createUnplanned_inLocked_succeeds() {
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.LOCKED)));
    withManagerAndReview();

    service.createUnplanned(actor(), PLAN_ID, request(null));

    ArgumentCaptor<WeeklyCommitment> saved = ArgumentCaptor.forClass(WeeklyCommitment.class);
    verify(commitments).save(saved.capture());
    assertThat(saved.getValue().getCommitmentKind()).isEqualTo(CommitmentKind.UNPLANNED);
  }

  // --- no active manager → created + audit, projection skipped (mirrors 4.1/4.2) ----
  @Test
  void createUnplanned_noManager_skipsProjection() {
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.LOCKED)));
    when(relationships.findByDirectReportEmployeeIdAndActiveTrue(IC)).thenReturn(Optional.empty());

    service.createUnplanned(actor(), PLAN_ID, request(null));

    verify(commitments).save(any());
    verify(auditService)
        .record(eq("UNPLANNED_COMMITMENT_CREATED"), any(), any(), any(), any(), any());
    verify(projectionService, never()).recompute(any(), any(), any(), any());
  }

  // --- a provided valid Supporting Outcome is resolved + linked ----
  @Test
  void createUnplanned_withValidSo_links() {
    UUID soId = UUID.randomUUID();
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.RECONCILING)));
    withManagerAndReview();

    service.createUnplanned(actor(), PLAN_ID, request(soId));

    verify(rcdoReadService).findSupportingOutcome(soId); // validated
    ArgumentCaptor<WeeklyCommitment> saved = ArgumentCaptor.forClass(WeeklyCommitment.class);
    verify(commitments).save(saved.capture());
    assertThat(saved.getValue().getSupportingOutcomeId()).isEqualTo(soId);
  }

  // --- a provided unknown Supporting Outcome → 400 VALIDATION_ERROR; nothing persisted ----
  @Test
  void createUnplanned_unknownSo_throwsValidation_neverSaves() {
    UUID soId = UUID.randomUUID();
    when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(PlanState.RECONCILING)));
    when(rcdoReadService.findSupportingOutcome(soId))
        .thenThrow(new ResourceNotFoundOrUnauthorizedException());

    assertThatThrownBy(() -> service.createUnplanned(actor(), PLAN_ID, request(soId)))
        .isInstanceOf(ValidationException.class);
    verify(commitments, never()).save(any());
    verify(projectionService, never()).recompute(any(), any(), any(), any());
  }

  // --- state guard: create on DRAFT or RECONCILED → 409 ILLEGAL_STATE_TRANSITION; never persists
  // --
  @Test
  void createUnplanned_onDraftOrReconciled_throwsIllegalStateTransition() {
    for (PlanState state : List.of(PlanState.DRAFT, PlanState.RECONCILED)) {
      when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan(state)));
      assertThatThrownBy(() -> service.createUnplanned(actor(), PLAN_ID, request(null)))
          .as("unplanned create in %s must be rejected", state)
          .isInstanceOf(IllegalStateTransitionException.class);
    }
    verify(commitments, never()).save(any());
    verify(projectionService, never()).recompute(any(), any(), any(), any());
  }

  // --- rule #3: a denied owner-only authorize is the chokepoint — nothing loaded, saved, projected
  // -
  @Test
  void createUnplanned_deniedAuthorizer_neverLoadsOrSaves() {
    doThrow(new ResourceNotFoundOrUnauthorizedException())
        .when(authz)
        .authorizePlanMutation(any(), eq(PLAN_ID));

    assertThatThrownBy(() -> service.createUnplanned(actor(), PLAN_ID, request(null)))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verify(plans, never()).findById(PLAN_ID); // no load before authorization
    verify(commitments, never()).save(any());
    verify(projectionService, never()).recompute(any(), any(), any(), any());
  }
}
