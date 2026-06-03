import { render } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { StatusBadge } from './StatusBadge';
import { RiskBadge } from './RiskBadge';

function badgeIn(container: HTMLElement): HTMLElement | null {
  return container.querySelector(
    '[data-cy="status-badge"], [data-cy="risk-badge"]',
  );
}

describe('StatusBadge (PlanState / ReviewStatus — §4.2)', () => {
  it('status_badge_renders_planstate_and_reviewstatus: each value renders its pinned tone + icon + label', () => {
    const planCases = [
      ['DRAFT', 'neutral', 'Draft'],
      ['LOCKED', 'info', 'Locked'],
      ['RECONCILING', 'warning', 'Reconciling'],
      ['RECONCILED', 'success', 'Reconciled'],
      ['NOT_STARTED', 'neutral', 'Not started'],
    ] as const;
    for (const [value, tone, label] of planCases) {
      const { container, unmount } = render(
        <StatusBadge kind="plan" value={value} />,
      );
      const badge = badgeIn(container);
      expect(badge).not.toBeNull();
      expect(badge?.getAttribute('data-tone')).toBe(tone);
      expect(badge?.querySelector('svg')).not.toBeNull();
      expect(badge).toHaveTextContent(label);
      unmount();
    }

    const reviewCases = [
      ['NOT_REVIEWED', 'neutral', 'Not reviewed'],
      ['REVIEWED_WITH_DISPUTES', 'warning', 'Reviewed · disputes'],
      ['REVIEWED', 'success', 'Reviewed'],
    ] as const;
    for (const [value, tone, label] of reviewCases) {
      const { container, unmount } = render(
        <StatusBadge kind="review" value={value} />,
      );
      const badge = badgeIn(container);
      expect(badge?.getAttribute('data-tone')).toBe(tone);
      expect(badge?.querySelector('svg')).not.toBeNull();
      expect(badge).toHaveTextContent(label);
      unmount();
    }

    // Derived OVERDUE overlay — the real semantic: OVERDUE derives upstream from
    // NOT_REVIEWED (isReviewOverdue = now>due AND NOT_REVIEWED, §3); read-time
    // derived, never a stored status.
    const { container } = render(
      <StatusBadge kind="review" value="NOT_REVIEWED" derivedOverdue />,
    );
    const badge = badgeIn(container);
    expect(badge?.getAttribute('data-tone')).toBe('failure');
    expect(badge).toHaveTextContent('Overdue');
  });
});

describe('RiskBadge (six B.1 values — §4.2)', () => {
  it('risk_badge_renders_six_values_with_ring_variant: tone+icon per value; BLOCKED/CARRY_FORWARD ring; unknown → nothing', () => {
    const cases = [
      ['MISALIGNED', 'failure', false],
      ['BLOCKED', 'failure', true],
      ['OVERDUE_REVIEW', 'failure', false],
      ['NEEDS_REVIEW', 'warning', false],
      ['CARRY_FORWARD', 'warning', true],
      ['UNREVIEWED', 'neutral', false],
    ] as const;
    for (const [value, tone, ring] of cases) {
      const { container, unmount } = render(<RiskBadge value={value} />);
      const badge = container.querySelector('[data-cy="risk-badge"]');
      expect(badge).not.toBeNull();
      expect(badge?.getAttribute('data-tone')).toBe(tone);
      expect(badge?.querySelector('svg')).not.toBeNull();
      if (ring) {
        expect(badge?.getAttribute('data-ring')).toBe('true');
      } else {
        expect(badge?.getAttribute('data-ring')).toBeNull();
      }
      unmount();
    }

    // Unknown / absent value renders nothing (no throw).
    const { container } = render(<RiskBadge value="NOT_A_RISK" />);
    expect(container.querySelector('[data-cy="risk-badge"]')).toBeNull();
  });
});

describe('color-not-only-signal (REQ-S-005 a11y)', () => {
  it('color_is_never_the_only_signal: every badge carries glyph + text + color', () => {
    const nodes = [
      <StatusBadge key="a" kind="plan" value="LOCKED" />,
      <StatusBadge key="b" kind="review" value="REVIEWED" />,
      <RiskBadge key="c" value="BLOCKED" />,
      <RiskBadge key="d" value="UNREVIEWED" />,
    ];
    for (const node of nodes) {
      const { container, unmount } = render(node);
      const badge = badgeIn(container);
      expect(badge).not.toBeNull();
      expect(badge?.querySelector('svg')).not.toBeNull(); // glyph
      expect((badge?.textContent ?? '').trim().length).toBeGreaterThan(0); // text
      expect(badge?.getAttribute('data-tone')).toBeTruthy(); // color
      unmount();
    }
  });
});
