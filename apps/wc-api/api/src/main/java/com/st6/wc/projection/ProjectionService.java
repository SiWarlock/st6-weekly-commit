package com.st6.wc.projection;

import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.RiskBadge;
import com.st6.wc.enums.WorkType;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.projection.repo.ManagerHeatmapCellRepository;
import com.st6.wc.projection.repo.ManagerPlanSummaryRepository;
import com.st6.wc.rcdo.RcdoReadService;
import com.st6.wc.review.ManagerReview;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Synchronously recomputes the two manager-projection read models (task 3.5, §9) from source — the
 * {@code manager_plan_summary} (one row at plan grain) + the {@code manager_heatmap_cell} rows (one
 * per Defining Objective with ≥1 linked commitment, resolved SO → DO via {@link RcdoReadService}).
 * Called inside the lock transaction (and re-called by later mutation slices). §9 count predicates:
 * misaligned = {@code alignment_status=MISALIGNED}; needs-review = {@code NEEDS_REVIEW}; blocked =
 * {@code work_type=BLOCKER}; carry-forward = a non-null {@code carryForwardSourceCommitmentId}. The
 * unresolved-dispute contribution is 0 until the disputes slice extends this (no dispute repo yet).
 * {@code is_review_overdue} + the {@code OVERDUE_REVIEW} badge are derived over the injectable
 * {@link Clock} ({@code NOT_REVIEWED AND now > reviewDueAt}); at lock they are false (future due
 * date).
 */
@Service
public class ProjectionService {

  private final ManagerPlanSummaryRepository summaries;
  private final ManagerHeatmapCellRepository cells;
  private final RcdoReadService rcdoReadService;
  private final Clock clock;

  public ProjectionService(
      ManagerPlanSummaryRepository summaries,
      ManagerHeatmapCellRepository cells,
      RcdoReadService rcdoReadService,
      Clock clock) {
    this.summaries = summaries;
    this.cells = cells;
    this.rcdoReadService = rcdoReadService;
    this.clock = clock;
  }

  public void recompute(
      WeeklyPlan plan, UUID managerId, List<WeeklyCommitment> commitments, ManagerReview review) {
    boolean overdue =
        review.getStatus() == ReviewStatus.NOT_REVIEWED
            && clock.instant().isAfter(review.getReviewDueAt());

    upsertSummary(plan, managerId, commitments, review, overdue);
    upsertHeatmapCells(plan, managerId, commitments, review, overdue);
  }

  private void upsertSummary(
      WeeklyPlan plan,
      UUID managerId,
      List<WeeklyCommitment> commitments,
      ManagerReview review,
      boolean overdue) {
    ManagerPlanSummary s =
        summaries
            .findByManagerEmployeeIdAndEmployeeIdAndWeekStartDate(
                managerId, plan.getEmployeeId(), plan.getWeekStartDate())
            .orElseGet(ProjectionService::newSummary);
    s.setManagerEmployeeId(managerId);
    s.setEmployeeId(plan.getEmployeeId());
    s.setWeeklyPlanId(plan.getId());
    s.setWeekStartDate(plan.getWeekStartDate());
    s.setPlanState(plan.getState());
    s.setReviewStatus(review.getStatus());
    s.setReviewDueAt(review.getReviewDueAt());
    s.setReviewOverdue(overdue);
    s.setPlannedCount(count(commitments, c -> c.getCommitmentKind() == CommitmentKind.PLANNED));
    s.setUnplannedCount(count(commitments, c -> c.getCommitmentKind() == CommitmentKind.UNPLANNED));
    s.setMisalignedCount(count(commitments, ProjectionService::isMisaligned));
    s.setNeedsReviewCount(count(commitments, ProjectionService::isNeedsReview));
    s.setBlockedCount(count(commitments, ProjectionService::isBlocked));
    s.setCarryForwardCount(count(commitments, ProjectionService::isCarryForward));
    s.setUnresolvedDisputeCount(0); // disputes slice extends this
    s.setUpdatedAt(clock.instant());
    summaries.save(s);
  }

  private void upsertHeatmapCells(
      WeeklyPlan plan,
      UUID managerId,
      List<WeeklyCommitment> commitments,
      ManagerReview review,
      boolean overdue) {
    // group the LINKED commitments by their Supporting Outcome's parent Defining Objective.
    Map<UUID, List<WeeklyCommitment>> byDefiningObjective = new LinkedHashMap<>();
    for (WeeklyCommitment c : commitments) {
      if (c.getSupportingOutcomeId() == null) {
        continue; // unlinked commitments map to no DO cell (cannot happen for planned at lock)
      }
      UUID definingObjectiveId =
          rcdoReadService
              .findSupportingOutcome(c.getSupportingOutcomeId())
              .getDefiningObjectiveId();
      byDefiningObjective.computeIfAbsent(definingObjectiveId, k -> new ArrayList<>()).add(c);
    }

    Map<UUID, ManagerHeatmapCell> existing = new LinkedHashMap<>();
    for (ManagerHeatmapCell cell :
        cells.findByManagerEmployeeIdAndEmployeeIdAndWeekStartDate(
            managerId, plan.getEmployeeId(), plan.getWeekStartDate())) {
      existing.put(cell.getDefiningObjectiveId(), cell);
    }

    boolean unreviewed = review.getStatus() == ReviewStatus.NOT_REVIEWED;
    byDefiningObjective.forEach(
        (definingObjectiveId, group) -> {
          ManagerHeatmapCell cell = existing.getOrDefault(definingObjectiveId, newCell());
          cell.setManagerEmployeeId(managerId);
          cell.setEmployeeId(plan.getEmployeeId());
          cell.setWeekStartDate(plan.getWeekStartDate());
          cell.setDefiningObjectiveId(definingObjectiveId);
          cell.setCommitmentCount(group.size());
          cell.setPlannedCount(count(group, c -> c.getCommitmentKind() == CommitmentKind.PLANNED));
          cell.setUnplannedCount(
              count(group, c -> c.getCommitmentKind() == CommitmentKind.UNPLANNED));
          cell.setMisalignedCount(count(group, ProjectionService::isMisaligned));
          cell.setNeedsReviewCount(count(group, ProjectionService::isNeedsReview));
          cell.setBlockedCount(count(group, ProjectionService::isBlocked));
          cell.setCarryForwardCount(count(group, ProjectionService::isCarryForward));
          cell.setUnresolvedDisputeCount(0);
          cell.setRiskBadges(riskBadges(group, unreviewed, overdue));
          cell.setUpdatedAt(clock.instant());
          cells.save(cell);
        });
  }

  private static List<RiskBadge> riskBadges(
      List<WeeklyCommitment> group, boolean unreviewed, boolean overdue) {
    List<RiskBadge> badges = new ArrayList<>();
    if (group.stream().anyMatch(ProjectionService::isMisaligned)) {
      badges.add(RiskBadge.MISALIGNED);
    }
    if (group.stream().anyMatch(ProjectionService::isNeedsReview)) {
      badges.add(RiskBadge.NEEDS_REVIEW);
    }
    if (group.stream().anyMatch(ProjectionService::isBlocked)) {
      badges.add(RiskBadge.BLOCKED);
    }
    if (group.stream().anyMatch(ProjectionService::isCarryForward)) {
      badges.add(RiskBadge.CARRY_FORWARD);
    }
    if (unreviewed) {
      badges.add(RiskBadge.UNREVIEWED);
    }
    if (overdue) {
      badges.add(RiskBadge.OVERDUE_REVIEW);
    }
    return badges;
  }

  private static boolean isMisaligned(WeeklyCommitment c) {
    return c.getAlignmentStatus() == AlignmentStatus.MISALIGNED;
  }

  private static boolean isNeedsReview(WeeklyCommitment c) {
    return c.getAlignmentStatus() == AlignmentStatus.NEEDS_REVIEW;
  }

  private static boolean isBlocked(WeeklyCommitment c) {
    return c.getWorkType() == WorkType.BLOCKER;
  }

  private static boolean isCarryForward(WeeklyCommitment c) {
    return c.getCarryForwardSourceCommitmentId() != null;
  }

  private static int count(
      List<WeeklyCommitment> commitments, java.util.function.Predicate<WeeklyCommitment> p) {
    return (int) commitments.stream().filter(p).count();
  }

  private static ManagerPlanSummary newSummary() {
    ManagerPlanSummary s = new ManagerPlanSummary();
    s.setId(UUID.randomUUID());
    return s;
  }

  private static ManagerHeatmapCell newCell() {
    ManagerHeatmapCell c = new ManagerHeatmapCell();
    c.setId(UUID.randomUUID());
    return c;
  }
}
