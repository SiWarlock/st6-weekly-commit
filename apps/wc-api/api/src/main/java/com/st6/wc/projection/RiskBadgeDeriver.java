package com.st6.wc.projection;

import com.st6.wc.enums.RiskBadge;
import java.util.ArrayList;
import java.util.List;

/**
 * Derives a heatmap cell's enumerated {@link RiskBadge} list (task 6.4, §9 enumerated {@code
 * risk_badges} / RISK-014 / Appendix B.1 vocabulary). Extracted from {@code ProjectionService}'s
 * inline derivation so the incremental path (6.2/6.3b) and the 6.7 rebuild share ONE badge source
 * (rebuild==incremental, §17). Each count-driven badge fires iff the cell's already-computed count
 * is {@code > 0} — deriving from the SAME stored count the cell carries means a badge can never
 * conflict with its count (RISK-014 is <strong>structural</strong>, not a re-derivation that could
 * drift). {@code UNREVIEWED}/{@code OVERDUE_REVIEW} come from the review's {@code
 * NOT_REVIEWED}/derived-overdue booleans the caller computes (the {@code NOT_REVIEWED}-gating —
 * including {@code REVIEWED_WITH_DISPUTES}-past-due ⇒ not overdue — lives upstream in {@code
 * ProjectionService}, where those booleans are computed over the injectable {@code Clock}).
 * Emission order is fixed for a stable {@code text[]} array.
 */
public final class RiskBadgeDeriver {

  private RiskBadgeDeriver() {}

  /**
   * Emit the enumerated risk badges for {@code cell} from its (already-set) counts plus the review
   * booleans. Behavior-preserving vs the prior inline {@code ProjectionService.riskBadges} ({@code
   * count > 0 ⟺ anyMatch(predicate)}; the stored {@code misalignedCount} is the 6.2 dispute-union,
   * so the {@code MISALIGNED} badge fires off the union exactly as before).
   */
  public static List<RiskBadge> derive(
      ManagerHeatmapCell cell, boolean unreviewed, boolean overdue) {
    List<RiskBadge> badges = new ArrayList<>();
    if (cell.getMisalignedCount() > 0) {
      badges.add(RiskBadge.MISALIGNED); // alignment ∪ open-MISALIGNED-dispute (§9 union, 6.2)
    }
    if (cell.getNeedsReviewCount() > 0) {
      badges.add(RiskBadge.NEEDS_REVIEW);
    }
    if (cell.getBlockedCount() > 0) {
      badges.add(RiskBadge.BLOCKED); // reconciliation_outcome=BLOCKED (6.2-corrected source)
    }
    if (cell.getCarryForwardCount() > 0) {
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
}
