import { render } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { ConfidenceMeter } from './ConfidenceMeter';
import type { Confidence } from '../../shared/lib/dtos';

describe('ConfidenceMeter (3-segment meter — fill scales with level)', () => {
  it('confidence_meter_fills_segments_by_level: HIGH=3 filled / MEDIUM=2 / LOW=1 (always 3 segments total); capitalized label + a Confidence title both present; an unknown value renders nothing', () => {
    const cases: [Confidence, number, string][] = [
      ['HIGH', 3, 'High'],
      ['MEDIUM', 2, 'Medium'],
      ['LOW', 1, 'Low'],
    ];
    for (const [value, filled, label] of cases) {
      const { container, unmount } = render(<ConfidenceMeter value={value} />);
      const meter = container.querySelector('[data-cy="confidence-meter"]');
      expect(meter).not.toBeNull();
      // Always three segment elements; `filled` of them are marked filled.
      expect(container.querySelectorAll('[data-cy="conf-seg"]')).toHaveLength(
        3,
      );
      expect(
        container.querySelectorAll('[data-cy="conf-seg"][data-filled="true"]'),
      ).toHaveLength(filled);
      // Text label (the non-color signal) + the accessible title.
      expect(meter).toHaveTextContent(label);
      expect(meter).toHaveAttribute('title', `Confidence: ${label}`);
      unmount();
    }
    const { container } = render(
      <ConfidenceMeter value={'NONE' as Confidence} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  // ST.7c — LOW legibility: the single filled LOW segment is near-invisible, so
  // outline the EMPTY segments (ring) — the 3-slot structure always reads as
  // "N of 3" without changing the §7 LOW tone (stays neutral).
  it('confidence_meter_low_shows_all_three_slots: at LOW the 3 slots are structurally legible — the 2 empty segments carry a ring outline, 1 is filled, and the LOW tone stays neutral (§7 unchanged)', () => {
    const { container } = render(<ConfidenceMeter value="LOW" />);

    const segs = container.querySelectorAll('[data-cy="conf-seg"]');
    expect(segs).toHaveLength(3);

    const filled = container.querySelectorAll(
      '[data-cy="conf-seg"][data-filled="true"]',
    );
    const empty = container.querySelectorAll(
      '[data-cy="conf-seg"][data-filled="false"]',
    );
    expect(filled).toHaveLength(1);
    expect(empty).toHaveLength(2);
    // The empty segments carry the ring outline so the 3-slot structure reads.
    for (const seg of empty) {
      expect(seg.className).toMatch(/\bring-/);
    }
    // §7 LOW tone preserved (neutral) — the fix is structural, not a tone change.
    expect(
      container.querySelector('[data-cy="confidence-meter"]'),
    ).toHaveAttribute('data-tone', 'neutral');
  });
});
