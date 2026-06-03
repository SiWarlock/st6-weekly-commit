import { render } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { OutcomePill } from './OutcomePill';
import type { ReconciliationOutcome } from '../../shared/lib/dtos';

describe('OutcomePill (ReconciliationOutcome → tone pill, §3)', () => {
  it('outcome_pill_maps_each_outcome_to_tone: each outcome renders the Cadence label + tone + an icon glyph; an unknown value renders nothing', () => {
    const cases: [ReconciliationOutcome, string, string][] = [
      ['COMPLETED', 'Completed', 'success'],
      ['PARTIALLY_COMPLETED', 'Partial', 'warning'],
      ['BLOCKED', 'Blocked', 'failure'],
      ['CANCELED', 'Canceled', 'neutral'],
      ['CARRIED_FORWARD', 'Carried forward', 'info'],
    ];
    for (const [value, label, tone] of cases) {
      const { container, unmount } = render(<OutcomePill value={value} />);
      const pill = container.querySelector('[data-cy="outcome-pill"]');
      expect(pill).not.toBeNull();
      expect(pill).toHaveTextContent(label);
      expect(pill).toHaveAttribute('data-tone', tone);
      // glyph + text + tone (never color alone).
      expect(pill!.querySelector('svg')).not.toBeNull();
      unmount();
    }
    const { container } = render(
      <OutcomePill value={'NONE' as ReconciliationOutcome} />,
    );
    expect(container).toBeEmptyDOMElement();
  });
});
