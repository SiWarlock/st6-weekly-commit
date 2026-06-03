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
});
