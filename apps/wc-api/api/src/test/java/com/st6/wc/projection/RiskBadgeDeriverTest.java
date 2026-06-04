package com.st6.wc.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.st6.wc.enums.RiskBadge;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@code RiskBadgeDeriver} unit proof (task 6.4, §9 enumerated {@code risk_badges} / RISK-014 / B.1
 * vocabulary / Appendix E §1135 R2·R3·R5 coverage). The deriver emits a cell's {@link RiskBadge}
 * list from the SAME source the counts use — each count-driven badge fires iff its already-computed
 * cell count is {@code > 0} (so a badge can NEVER conflict with its count — RISK-014 is
 * structural), plus {@code UNREVIEWED}/{@code OVERDUE_REVIEW} from the review's {@code
 * NOT_REVIEWED}/derived- overdue booleans the caller computes. Behavior-preserving vs the prior
 * inline {@code ProjectionService.riskBadges} (the existing 6.2/6.3b heatmap badge assertions are
 * the regression net). The {@code REVIEWED_WITH_DISPUTES}-past-due ⇒ no-{@code OVERDUE_REVIEW} edge
 * (the overdue boolean is gated on {@code NOT_REVIEWED}) is pinned in {@code ProjectionServiceTest}
 * (069 ADD) — not duplicated here, since this deriver consumes the boolean rather than computing
 * it.
 */
class RiskBadgeDeriverTest {

  private static ManagerHeatmapCell cell(
      int misaligned, int needsReview, int blocked, int carryForward) {
    ManagerHeatmapCell c = new ManagerHeatmapCell();
    c.setMisalignedCount(misaligned);
    c.setNeedsReviewCount(needsReview);
    c.setBlockedCount(blocked);
    c.setCarryForwardCount(carryForward);
    return c;
  }

  // --- R3 Aisha: MISALIGNED (alignment ∪ open MISALIGNED dispute) + NEEDS_REVIEW, review
  // REVIEWED_WITH_DISPUTES (reviewed, not overdue) ⇒ MISALIGNED + NEEDS_REVIEW only ----
  @Test
  void r3_misalignedAndNeedsReview() {
    List<RiskBadge> badges = RiskBadgeDeriver.derive(cell(1, 1, 0, 0), false, false);
    assertThat(badges).contains(RiskBadge.MISALIGNED, RiskBadge.NEEDS_REVIEW);
    assertThat(badges)
        .doesNotContain(
            RiskBadge.OVERDUE_REVIEW,
            RiskBadge.UNREVIEWED,
            RiskBadge.BLOCKED,
            RiskBadge.CARRY_FORWARD);
  }

  // --- R2 Marco (the previously-untested overdue=true branch, RISK-014): NOT_REVIEWED + past-due ⇒
  // OVERDUE_REVIEW co-emits with UNREVIEWED ----
  @Test
  void r2_overdueReviewCoEmitsUnreviewed() {
    List<RiskBadge> badges = RiskBadgeDeriver.derive(cell(0, 0, 0, 0), true, true);
    assertThat(badges).contains(RiskBadge.UNREVIEWED, RiskBadge.OVERDUE_REVIEW);
  }

  // --- R5 Grace: reconciliation_outcome=BLOCKED + a carry-forward successor-link ⇒ BLOCKED +
  // CARRY_FORWARD ----
  @Test
  void r5_blockedAndCarryForward() {
    List<RiskBadge> badges = RiskBadgeDeriver.derive(cell(0, 0, 1, 1), false, false);
    assertThat(badges).contains(RiskBadge.BLOCKED, RiskBadge.CARRY_FORWARD);
    assertThat(badges).doesNotContain(RiskBadge.MISALIGNED, RiskBadge.NEEDS_REVIEW);
  }

  // --- a fully REVIEWED, aligned, completed cell ⇒ empty {} badge array ----
  @Test
  void fullyReviewedAligned_emptyBadges() {
    assertThat(RiskBadgeDeriver.derive(cell(0, 0, 0, 0), false, false)).isEmpty();
  }

  // --- within-SLA NOT_REVIEWED (unreviewed=true, overdue=false) ⇒ UNREVIEWED only, no
  // OVERDUE_REVIEW (the overdue boolean is the caller's NOT_REVIEWED∧past-due derivation) ----
  @Test
  void unreviewedWithinSla_unreviewedOnly_noOverdue() {
    assertThat(RiskBadgeDeriver.derive(cell(0, 0, 0, 0), true, false))
        .containsExactly(RiskBadge.UNREVIEWED);
  }

  // --- RISK-014: every emitted value is a RiskBadge enum constant (no stray value); all 6 fire
  // when
  // every condition holds, in the pinned emission order (stable text[] ordering for rebuild parity)
  @Test
  void allConditions_emitAllSixInOrder_enumOnly() {
    List<RiskBadge> badges = RiskBadgeDeriver.derive(cell(2, 3, 1, 4), true, true);
    assertThat(badges)
        .containsExactly(
            RiskBadge.MISALIGNED,
            RiskBadge.NEEDS_REVIEW,
            RiskBadge.BLOCKED,
            RiskBadge.CARRY_FORWARD,
            RiskBadge.UNREVIEWED,
            RiskBadge.OVERDUE_REVIEW);
    assertThat(RiskBadge.values()).containsAll(badges);
  }
}
