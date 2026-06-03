import { render } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { PriorityTag } from './PriorityTag';
import type { Priority } from '../../shared/lib/dtos';

describe('PriorityTag (Cadence chess atom — Priority skin)', () => {
  it('priority_tag_renders_literal_with_tone: P0/P1/P2 render the literal text + descending-urgency tone (P0 failure, P1 warning, P2 neutral, per .wc-pri); an unknown value renders nothing', () => {
    const cases: [Priority, string][] = [
      ['P0', 'failure'],
      ['P1', 'warning'],
      ['P2', 'neutral'],
    ];
    for (const [value, tone] of cases) {
      const { container, unmount } = render(<PriorityTag value={value} />);
      const tag = container.querySelector('[data-cy="priority-tag"]');
      expect(tag).not.toBeNull();
      // the literal P0/P1/P2 is the color-independent signal (text, not icon).
      expect(tag).toHaveTextContent(value);
      expect(tag).toHaveAttribute('data-tone', tone);
      unmount();
    }
    // unknown enum value → renders nothing (no throw) — LESSONS §7.
    const { container } = render(<PriorityTag value={'P9' as Priority} />);
    expect(container).toBeEmptyDOMElement();
  });
});
