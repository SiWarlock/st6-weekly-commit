import { render } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { AlignmentChip } from './AlignmentChip';
import type { AlignmentStatus } from '../../shared/lib/dtos';

describe('AlignmentChip (Cadence ALIGNMENT map → dot+label)', () => {
  it('alignment_chip_maps_enum_to_dot_label_tone: ALIGNED→success, NEEDS_REVIEW→warning, MISALIGNED→failure; a tone dot + the text label both render; an unknown value renders nothing', () => {
    const cases: [AlignmentStatus, string, string][] = [
      ['ALIGNED', 'Aligned', 'success'],
      ['NEEDS_REVIEW', 'Needs review', 'warning'],
      ['MISALIGNED', 'Misaligned', 'failure'],
    ];
    for (const [value, label, tone] of cases) {
      const { container, unmount } = render(<AlignmentChip value={value} />);
      const chip = container.querySelector('[data-cy="alignment-chip"]');
      expect(chip).not.toBeNull();
      expect(chip).toHaveTextContent(label);
      expect(chip).toHaveAttribute('data-tone', tone);
      // dot indicator present (not color alone — the dot + label carry meaning).
      expect(chip!.querySelector('[data-cy="alignment-dot"]')).not.toBeNull();
      unmount();
    }
    const { container } = render(
      <AlignmentChip value={'OTHER' as AlignmentStatus} />,
    );
    expect(container).toBeEmptyDOMElement();
  });
});
