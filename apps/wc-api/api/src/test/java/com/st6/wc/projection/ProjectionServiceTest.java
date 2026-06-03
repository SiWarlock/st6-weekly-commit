package com.st6.wc.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.RiskBadge;
import com.st6.wc.enums.WorkType;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.projection.repo.ManagerHeatmapCellRepository;
import com.st6.wc.projection.repo.ManagerPlanSummaryRepository;
import com.st6.wc.rcdo.RcdoReadService;
import com.st6.wc.rcdo.SupportingOutcome;
import com.st6.wc.review.ManagerReview;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@code ProjectionService} unit proof (task 3.5, §9 count derivation) — synchronous recompute of
 * the {@code manager_plan_summary} (one row, plan grain) + {@code manager_heatmap_cell} (one row
 * per Defining Objective with ≥1 linked commitment) from the plan's commitments + review. Pins the
 * §9 count predicates (misaligned=alignment MISALIGNED, blocked=work_type BLOCKER,
 * needsReview=alignment NEEDS_REVIEW) and the heatmap risk-badge derivation
 * (MISALIGNED/NEEDS_REVIEW/BLOCKED from commitments, UNREVIEWED from review status, no
 * OVERDUE_REVIEW when not past due). Repos + RcdoReadService mocked.
 */
class ProjectionServiceTest {

  private final ManagerPlanSummaryRepository summaries = mock(ManagerPlanSummaryRepository.class);
  private final ManagerHeatmapCellRepository cells = mock(ManagerHeatmapCellRepository.class);
  private final RcdoReadService rcdo = mock(RcdoReadService.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-06-02T12:00:00Z"), ZoneOffset.UTC);
  private final ProjectionService service = new ProjectionService(summaries, cells, rcdo, clock);

  private static final UUID MGR = UUID.randomUUID();
  private static final UUID IC = UUID.randomUUID();
  private static final UUID PLAN = UUID.randomUUID();
  private static final UUID DO1 = UUID.randomUUID();
  private static final UUID DO2 = UUID.randomUUID();
  private static final UUID SO1 = UUID.randomUUID();
  private static final UUID SO2 = UUID.randomUUID();
  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);

  private static SupportingOutcome so(UUID id, UUID doId) {
    SupportingOutcome s = new SupportingOutcome();
    s.setId(id);
    s.setDefiningObjectiveId(doId);
    return s;
  }

  private static WeeklyCommitment commitment(UUID so, AlignmentStatus align, WorkType work) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(PLAN);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setSupportingOutcomeId(so);
    c.setAlignmentStatus(align);
    c.setWorkType(work);
    c.setPriority(Priority.P1);
    c.setConfidence(Confidence.MEDIUM);
    return c;
  }

  private static WeeklyPlan plan() {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(PLAN);
    p.setEmployeeId(IC);
    p.setWeekStartDate(WEEK);
    p.setState(PlanState.LOCKED);
    return p;
  }

  private static ManagerReview review() {
    ManagerReview r = new ManagerReview();
    r.setId(UUID.randomUUID());
    r.setWeeklyPlanId(PLAN);
    r.setManagerEmployeeId(MGR);
    r.setStatus(ReviewStatus.NOT_REVIEWED);
    r.setReviewDueAt(Instant.parse("2026-06-08T22:00:00Z")); // future vs the fixed clock
    return r;
  }

  // --- summary counts + per-DO heatmap cells with risk badges, derived from source (§9) ----
  @Test
  void recompute_derivesSummaryAndHeatmapCells() {
    when(summaries.findByManagerEmployeeIdAndEmployeeIdAndWeekStartDate(MGR, IC, WEEK))
        .thenReturn(Optional.empty());
    when(cells.findByManagerEmployeeIdAndEmployeeIdAndWeekStartDate(MGR, IC, WEEK))
        .thenReturn(List.of());
    when(rcdo.findSupportingOutcome(SO1)).thenReturn(so(SO1, DO1));
    when(rcdo.findSupportingOutcome(SO2)).thenReturn(so(SO2, DO2));

    List<WeeklyCommitment> commitments =
        List.of(
            commitment(SO1, AlignmentStatus.NEEDS_REVIEW, WorkType.STRATEGIC), // DO1
            commitment(SO1, AlignmentStatus.MISALIGNED, WorkType.BLOCKER), // DO1
            commitment(SO2, AlignmentStatus.ALIGNED, WorkType.STRATEGIC)); // DO2

    service.recompute(plan(), MGR, commitments, review());

    // ----- manager_plan_summary (plan grain) -----
    ArgumentCaptor<ManagerPlanSummary> sum = ArgumentCaptor.forClass(ManagerPlanSummary.class);
    org.mockito.Mockito.verify(summaries).save(sum.capture());
    ManagerPlanSummary s = sum.getValue();
    assertThat(s.getManagerEmployeeId()).isEqualTo(MGR);
    assertThat(s.getEmployeeId()).isEqualTo(IC);
    assertThat(s.getPlanState()).isEqualTo(PlanState.LOCKED);
    assertThat(s.getReviewStatus()).isEqualTo(ReviewStatus.NOT_REVIEWED);
    assertThat(s.isReviewOverdue()).isFalse(); // future due
    assertThat(s.getPlannedCount()).isEqualTo(3);
    assertThat(s.getUnplannedCount()).isZero();
    assertThat(s.getMisalignedCount()).isEqualTo(1);
    assertThat(s.getNeedsReviewCount()).isEqualTo(1);
    assertThat(s.getBlockedCount()).isEqualTo(1);
    assertThat(s.getCarryForwardCount()).isZero();
    assertThat(s.getUnresolvedDisputeCount()).isZero();

    // ----- manager_heatmap_cell (one per DO) -----
    ArgumentCaptor<ManagerHeatmapCell> cell = ArgumentCaptor.forClass(ManagerHeatmapCell.class);
    org.mockito.Mockito.verify(cells, org.mockito.Mockito.times(2)).save(cell.capture());
    List<ManagerHeatmapCell> saved = cell.getAllValues();

    ManagerHeatmapCell do1 =
        saved.stream()
            .filter(x -> x.getDefiningObjectiveId().equals(DO1))
            .findFirst()
            .orElseThrow();
    assertThat(do1.getCommitmentCount()).isEqualTo(2);
    assertThat(do1.getMisalignedCount()).isEqualTo(1);
    assertThat(do1.getNeedsReviewCount()).isEqualTo(1);
    assertThat(do1.getBlockedCount()).isEqualTo(1);
    assertThat(do1.getRiskBadges())
        .containsExactlyInAnyOrder(
            RiskBadge.MISALIGNED, RiskBadge.NEEDS_REVIEW, RiskBadge.BLOCKED, RiskBadge.UNREVIEWED);

    ManagerHeatmapCell do2 =
        saved.stream()
            .filter(x -> x.getDefiningObjectiveId().equals(DO2))
            .findFirst()
            .orElseThrow();
    assertThat(do2.getCommitmentCount()).isEqualTo(1);
    assertThat(do2.getMisalignedCount()).isZero();
    assertThat(do2.getRiskBadges()).containsExactly(RiskBadge.UNREVIEWED); // only the review badge
  }
}
