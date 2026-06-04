package com.st6.wc.projection;

import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.dispute.AlignmentDispute;
import com.st6.wc.dispute.repo.AlignmentDisputeRepository;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.DisputeStatus;
import com.st6.wc.enums.FlagType;
import com.st6.wc.enums.ReconciliationOutcome;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.RiskBadge;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Synchronously recomputes the two manager-projection read models (task 3.5, §9) from source — the
 * {@code manager_plan_summary} (one row at plan grain) + the {@code manager_heatmap_cell} rows (one
 * per Defining Objective with ≥1 linked commitment, resolved SO → DO via {@link RcdoReadService}).
 * Called inside the lock transaction (and re-called by later mutation slices). §9 count predicates
 * (task 6.2 completes the dispute-driven ones from source): misaligned = {@code
 * alignment_status=MISALIGNED} <strong>OR</strong> an {@code OPEN}/{@code IC_RESPONDED} dispute
 * with {@code flag_type=MISALIGNED} (deduped per commitment; the {@code MISALIGNED} badge consumes
 * the same union); {@code unresolved_dispute_count} = commitments carrying an {@code OPEN}/{@code
 * IC_RESPONDED} dispute; needs-review = {@code NEEDS_REVIEW}; <strong>blocked = {@code
 * reconciliation_outcome=BLOCKED}</strong> (the sibling of {@code
 * carry_forward_count=reconciliation_outcome=CARRIED_FORWARD}; corrected from the latent {@code
 * work_type=BLOCKER}, so {@code blockedCount} is 0 at lock); carry-forward = a non-null {@code
 * carryForwardSourceCommitmentId} (§30, unchanged). Disputes are loaded ONCE over the plan's
 * commitment ids (no N+1; empty set ⇒ no query). {@code is_review_overdue} + the {@code
 * OVERDUE_REVIEW} badge are derived over the injectable {@link Clock} ({@code NOT_REVIEWED AND now
 * > reviewDueAt}); at lock they are false (future due date).
 */
@Service
public class ProjectionService {

  private static final List<DisputeStatus> UNRESOLVED =
      List.of(DisputeStatus.OPEN, DisputeStatus.IC_RESPONDED);

  private final ManagerPlanSummaryRepository summaries;
  private final ManagerHeatmapCellRepository cells;
  private final RcdoReadService rcdoReadService;
  private final Clock clock;
  private final AlignmentDisputeRepository disputes;

  public ProjectionService(
      ManagerPlanSummaryRepository summaries,
      ManagerHeatmapCellRepository cells,
      RcdoReadService rcdoReadService,
      Clock clock,
      AlignmentDisputeRepository disputes) {
    this.summaries = summaries;
    this.cells = cells;
    this.rcdoReadService = rcdoReadService;
    this.clock = clock;
    this.disputes = disputes;
  }

  public void recompute(
      WeeklyPlan plan, UUID managerId, List<WeeklyCommitment> commitments, ManagerReview review) {
    boolean overdue =
        review.getStatus() == ReviewStatus.NOT_REVIEWED
            && clock.instant().isAfter(review.getReviewDueAt());

    // §9 dispute-driven counts: load the plan's unresolved disputes ONCE over the commitment ids
    // (one query, no N+1), then derive both the unresolved-dispute set and the MISALIGNED-flag
    // union
    // set in-memory. Empty commitment set ⇒ no query (an empty SQL IN is invalid).
    List<UUID> commitmentIds = commitments.stream().map(WeeklyCommitment::getId).toList();
    List<AlignmentDispute> unresolved =
        commitmentIds.isEmpty()
            ? List.of()
            : disputes.findByCommitmentIdInAndStatusIn(commitmentIds, UNRESOLVED);
    Set<UUID> unresolvedDisputeCommitmentIds =
        unresolved.stream().map(AlignmentDispute::getCommitmentId).collect(Collectors.toSet());
    Set<UUID> misalignedDisputeCommitmentIds =
        unresolved.stream()
            .filter(d -> d.getFlagType() == FlagType.MISALIGNED)
            .map(AlignmentDispute::getCommitmentId)
            .collect(Collectors.toSet());

    upsertSummary(
        plan,
        managerId,
        commitments,
        review,
        overdue,
        unresolvedDisputeCommitmentIds,
        misalignedDisputeCommitmentIds);
    upsertHeatmapCells(
        plan,
        managerId,
        commitments,
        review,
        overdue,
        unresolvedDisputeCommitmentIds,
        misalignedDisputeCommitmentIds);
  }

  private void upsertSummary(
      WeeklyPlan plan,
      UUID managerId,
      List<WeeklyCommitment> commitments,
      ManagerReview review,
      boolean overdue,
      Set<UUID> unresolvedDisputeCommitmentIds,
      Set<UUID> misalignedDisputeCommitmentIds) {
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
    s.setMisalignedCount(countMisaligned(commitments, misalignedDisputeCommitmentIds));
    s.setNeedsReviewCount(count(commitments, ProjectionService::isNeedsReview));
    s.setBlockedCount(count(commitments, ProjectionService::isBlocked));
    s.setCarryForwardCount(count(commitments, ProjectionService::isCarryForward));
    s.setUnresolvedDisputeCount(countWithDispute(commitments, unresolvedDisputeCommitmentIds));
    s.setUpdatedAt(clock.instant());
    summaries.save(s);
  }

  private void upsertHeatmapCells(
      WeeklyPlan plan,
      UUID managerId,
      List<WeeklyCommitment> commitments,
      ManagerReview review,
      boolean overdue,
      Set<UUID> unresolvedDisputeCommitmentIds,
      Set<UUID> misalignedDisputeCommitmentIds) {
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
          cell.setMisalignedCount(countMisaligned(group, misalignedDisputeCommitmentIds));
          cell.setNeedsReviewCount(count(group, ProjectionService::isNeedsReview));
          cell.setBlockedCount(count(group, ProjectionService::isBlocked));
          cell.setCarryForwardCount(count(group, ProjectionService::isCarryForward));
          cell.setUnresolvedDisputeCount(countWithDispute(group, unresolvedDisputeCommitmentIds));
          cell.setRiskBadges(
              riskBadges(group, unreviewed, overdue, misalignedDisputeCommitmentIds));
          cell.setUpdatedAt(clock.instant());
          cells.save(cell);
        });

    // 6.3b stale-cell deletion: a Defining Objective no longer touched by any commitment (e.g. a
    // dispute-respond rule-#2 SO revision remapped a commitment to a different DO) would leave an
    // orphan cell under the upsert-only path — delete it so a fresh recompute (and the 6.7 rebuild)
    // never reads a stale heatmap row (RISK-003 drift; rebuild==incremental). Summary rows are
    // unaffected — the grain (manager,employee,week) is stable; only DO cells go stale.
    existing.forEach(
        (definingObjectiveId, cell) -> {
          if (!byDefiningObjective.containsKey(definingObjectiveId)) {
            cells.delete(cell);
          }
        });
  }

  private static List<RiskBadge> riskBadges(
      List<WeeklyCommitment> group,
      boolean unreviewed,
      boolean overdue,
      Set<UUID> misalignedDisputeCommitmentIds) {
    List<RiskBadge> badges = new ArrayList<>();
    if (group.stream().anyMatch(c -> isMisaligned(c, misalignedDisputeCommitmentIds))) {
      badges.add(RiskBadge.MISALIGNED); // alignment ∪ open-MISALIGNED-dispute (§9 union)
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

  /**
   * §9 misaligned = alignment MISALIGNED OR an open MISALIGNED-flag dispute (deduped per
   * commitment).
   */
  private static int countMisaligned(
      List<WeeklyCommitment> group, Set<UUID> misalignedDisputeCommitmentIds) {
    return count(group, c -> isMisaligned(c, misalignedDisputeCommitmentIds));
  }

  /** §9 unresolved-dispute count = commitments carrying an OPEN/IC_RESPONDED dispute. */
  private static int countWithDispute(
      List<WeeklyCommitment> group, Set<UUID> unresolvedDisputeCommitmentIds) {
    return count(group, c -> unresolvedDisputeCommitmentIds.contains(c.getId()));
  }

  private static boolean isMisaligned(
      WeeklyCommitment c, Set<UUID> misalignedDisputeCommitmentIds) {
    return c.getAlignmentStatus() == AlignmentStatus.MISALIGNED
        || misalignedDisputeCommitmentIds.contains(c.getId());
  }

  private static boolean isNeedsReview(WeeklyCommitment c) {
    return c.getAlignmentStatus() == AlignmentStatus.NEEDS_REVIEW;
  }

  /**
   * §9 blocked = {@code reconciliation_outcome=BLOCKED} (task 6.2 — the corrected source, the
   * sibling of {@code carry_forward_count=reconciliation_outcome=CARRIED_FORWARD}). The shipped
   * {@code work_type=BLOCKER} was a latent bug: BLOCKER is a planning category, not a risk outcome,
   * and would fail {@code rebuild==seed} for the R5 Grace fixture (ARCH §1127/§1135/§1387, §17). At
   * lock no commitment has a reconciliation outcome yet, so {@code blockedCount} is 0 at lock.
   */
  private static boolean isBlocked(WeeklyCommitment c) {
    return c.getReconciliationOutcome() == ReconciliationOutcome.BLOCKED;
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
