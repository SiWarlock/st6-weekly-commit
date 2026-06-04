package com.st6.wc.plan.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.st6.wc.action.AllowedAction;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.mapper.CommitmentMapper;
import com.st6.wc.dispute.mapper.DisputeMapper;
import com.st6.wc.dispute.repo.AlignmentDisputeRepository;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.WorkType;
import com.st6.wc.plan.AllowedActionResolver;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.dto.WeeklyPlanDto;
import com.st6.wc.rcdo.RcdoReadService;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.mapper.ReviewMapper;
import com.st6.wc.review.repo.ManagerReviewRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit proof of {@link PlanMapper} (task 3.3a/3.4a, Appendix B.5). Assembles a {@link
 * WeeklyPlanDto} (records, never entities) from the plan + commitments: delegates each commitment
 * to {@link CommitmentMapper}, computes the planned/unplanned counts, stamps {@code
 * allowedActions[]} via the real {@link AllowedActionResolver}, and leaves {@code managerReview}
 * null while DRAFT (3.5 wires the review). The commitment→DTO shape itself (breadcrumb,
 * dispute-nesting) is proven in {@code CommitmentMapperTest}.
 */
class PlanMapperTest {

  private final RcdoReadService rcdoReadService = mock(RcdoReadService.class);
  private final AlignmentDisputeRepository disputes = mock(AlignmentDisputeRepository.class);
  private final ManagerReviewRepository reviews = mock(ManagerReviewRepository.class);
  private final ReviewMapper reviewMapper = mock(ReviewMapper.class);
  private final ManagerRelationshipRepository relationships =
      mock(ManagerRelationshipRepository.class);
  private final PlanMapper mapper =
      new PlanMapper(
          new CommitmentMapper(
              rcdoReadService, new AllowedActionResolver(), disputes, new DisputeMapper()),
          new AllowedActionResolver(),
          reviews,
          reviewMapper,
          relationships);

  private static final UUID OWNER = UUID.randomUUID();

  private static WeeklyPlan draftPlan() {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(OWNER);
    p.setWeekStartDate(LocalDate.of(2026, 6, 1));
    p.setWeekEndDate(LocalDate.of(2026, 6, 7));
    p.setState(PlanState.DRAFT);
    p.setVersion(0L);
    return p;
  }

  private static WeeklyCommitment commitment(CommitmentKind kind) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(UUID.randomUUID());
    c.setCommitmentKind(kind);
    c.setTitle("title");
    c.setSupportingOutcomeId(null); // unlinked → no breadcrumb lookup
    c.setPriority(Priority.P1);
    c.setWorkType(kind == CommitmentKind.UNPLANNED ? WorkType.UNPLANNED : WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(AlignmentStatus.NEEDS_REVIEW);
    c.setVersion(0L);
    return c;
  }

  @Test
  void toWeeklyPlanDto_mapsB5Fields_countsAndNullReviewWhileDraft() {
    WeeklyPlan plan = draftPlan();

    WeeklyPlanDto dto =
        mapper.toWeeklyPlanDto(
            plan,
            "Ada Lovelace",
            List.of(commitment(CommitmentKind.PLANNED), commitment(CommitmentKind.UNPLANNED)),
            OWNER);

    assertThat(dto.id()).isEqualTo(plan.getId());
    assertThat(dto.employeeId()).isEqualTo(OWNER);
    assertThat(dto.employeeDisplayName()).isEqualTo("Ada Lovelace");
    assertThat(dto.weekStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
    assertThat(dto.state()).isEqualTo(PlanState.DRAFT);
    assertThat(dto.plannedCount()).isEqualTo(1);
    assertThat(dto.unplannedCount()).isEqualTo(1);
    assertThat(dto.commitments()).hasSize(2);
    assertThat(dto.managerReview()).as("null while DRAFT (B.5; 3.5 wires the review)").isNull();
    assertThat(dto.version()).isZero();
  }

  // --- 4.4b: the IC's RECONCILING plan-read surfaces per-commitment CARRY_FORWARD on the eligible
  // nested commitments (the must-have — the frontend CarryForwardButton consumes this) ----
  @Test
  void toWeeklyPlanDto_reconciling_nestedCommitmentsCarryAffordance() {
    WeeklyPlan plan = draftPlan();
    plan.setState(PlanState.RECONCILING);

    WeeklyPlanDto dto =
        mapper.toWeeklyPlanDto(plan, "Ada", List.of(commitment(CommitmentKind.PLANNED)), OWNER);

    assertThat(dto.commitments().get(0).allowedActions()).contains(AllowedAction.CARRY_FORWARD);
  }

  private static final UUID MANAGER = UUID.randomUUID();

  private static WeeklyPlan lockedPlan() {
    WeeklyPlan p = draftPlan();
    p.setState(PlanState.LOCKED);
    return p;
  }

  private static ManagerRelationship relationship(UUID managerId, UUID reportId) {
    ManagerRelationship r = new ManagerRelationship();
    r.setId(UUID.randomUUID());
    r.setManagerEmployeeId(managerId);
    r.setDirectReportEmployeeId(reportId);
    r.setActive(true);
    return r;
  }

  // --- 5.5b: a direct manager viewing a report's LOCKED plan → viewerIsDirectManager determined
  // from the active relationship, threaded so the nested commitment surfaces OPEN_DISPUTE ----
  @Test
  void toWeeklyPlanDto_managerViewer_threadsOpenDisputeToNestedCommitment() {
    when(relationships.findByDirectReportEmployeeIdAndActiveTrue(OWNER))
        .thenReturn(Optional.of(relationship(MANAGER, OWNER)));

    WeeklyPlanDto dto =
        mapper.toWeeklyPlanDto(
            lockedPlan(), "Ada", List.of(commitment(CommitmentKind.PLANNED)), MANAGER);

    assertThat(dto.commitments().get(0).allowedActions()).contains(AllowedAction.OPEN_DISPUTE);
  }

  // --- 5.5b: viewerIsDirectManager is determined ONCE per plan read (one relationship lookup),
  // not per-commitment (Q1 — keep the read cheap) ----
  @Test
  void toWeeklyPlanDto_determinesViewerIsDirectManagerOncePerRead() {
    when(relationships.findByDirectReportEmployeeIdAndActiveTrue(OWNER))
        .thenReturn(Optional.of(relationship(MANAGER, OWNER)));

    mapper.toWeeklyPlanDto(
        lockedPlan(),
        "Ada",
        List.of(commitment(CommitmentKind.PLANNED), commitment(CommitmentKind.PLANNED)),
        MANAGER);

    verify(relationships, times(1)).findByDirectReportEmployeeIdAndActiveTrue(OWNER);
  }
}
