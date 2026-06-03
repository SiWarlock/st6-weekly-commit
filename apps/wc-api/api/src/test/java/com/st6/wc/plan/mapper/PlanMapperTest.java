package com.st6.wc.plan.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.mapper.CommitmentMapper;
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
import com.st6.wc.review.mapper.ReviewMapper;
import com.st6.wc.review.repo.ManagerReviewRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit proof of {@link PlanMapper} (task 3.3a/3.4a, Appendix B.5). Assembles a {@link
 * WeeklyPlanDto} (records, never entities) from the plan + commitments: delegates each commitment
 * to {@link CommitmentMapper}, computes the planned/unplanned counts, stamps {@code
 * allowedActions[]} via the real {@link AllowedActionResolver}, and leaves {@code managerReview}
 * null while DRAFT (3.5 wires the review). The commitment→DTO shape itself (breadcrumb,
 * dispute-omission) is proven in {@code CommitmentMapperTest}.
 */
class PlanMapperTest {

  private final RcdoReadService rcdoReadService = mock(RcdoReadService.class);
  private final ManagerReviewRepository reviews = mock(ManagerReviewRepository.class);
  private final ReviewMapper reviewMapper = mock(ReviewMapper.class);
  private final PlanMapper mapper =
      new PlanMapper(
          new CommitmentMapper(rcdoReadService),
          new AllowedActionResolver(),
          reviews,
          reviewMapper);

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
}
