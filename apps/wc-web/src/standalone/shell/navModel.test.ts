import { describe, expect, it } from 'vitest';
import { currentWeekLabel } from './navModel';

/**
 * `currentWeekLabel` — the Monday-anchored "Week of …" chrome label, derived from the live date so
 * it advances with the real week (replacing the previously-hardcoded label that was stuck on one
 * demo week). `Date`'s month arg is 0-indexed, so month 5 = June.
 */
describe('currentWeekLabel — Monday-anchored current-week label', () => {
  it('a midweek date resolves to that week’s Mon–Sun range', () => {
    // Wed 2026-06-03 → Mon 2026-06-01
    expect(currentWeekLabel(new Date(2026, 5, 3, 12))).toBe(
      'Week of Jun 1–7, 2026',
    );
  });

  it('a Sunday belongs to the week that began the prior Monday', () => {
    // Sun 2026-06-07 → Mon 2026-06-01
    expect(currentWeekLabel(new Date(2026, 5, 7, 23))).toBe(
      'Week of Jun 1–7, 2026',
    );
  });

  it('advances to the next week — the auto-advance this fixes', () => {
    // Mon 2026-06-08 → "Week of Jun 8–14, 2026" (NOT the stale "Jun 1–7")
    expect(currentWeekLabel(new Date(2026, 5, 8, 0))).toBe(
      'Week of Jun 8–14, 2026',
    );
  });

  it('renders both months for a cross-month week', () => {
    // Mon 2026-06-29 → "Week of Jun 29 – Jul 5, 2026"
    expect(currentWeekLabel(new Date(2026, 5, 29, 9))).toBe(
      'Week of Jun 29 – Jul 5, 2026',
    );
  });
});
