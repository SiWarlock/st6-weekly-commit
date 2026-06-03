import { render } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { WorkTypeTag } from './WorkTypeTag';
import type { WorkType } from '../../shared/lib/dtos';

describe('WorkTypeTag (Cadence WORKTYPE map → icon+label+tone)', () => {
  it('worktype_tag_maps_enum_to_icon_label_tone: each WorkType renders the Cadence label + tone + an icon glyph; an unknown value renders nothing', () => {
    const cases: [WorkType, string, string][] = [
      ['STRATEGIC', 'Strategic', 'info'],
      ['MAINTENANCE', 'Maintenance', 'neutral'],
      ['BLOCKER', 'Blocker', 'failure'],
      ['UNPLANNED', 'Unplanned', 'accent'],
    ];
    for (const [value, label, tone] of cases) {
      const { container, unmount } = render(<WorkTypeTag value={value} />);
      const tag = container.querySelector('[data-cy="worktype-tag"]');
      expect(tag).not.toBeNull();
      expect(tag).toHaveTextContent(label);
      expect(tag).toHaveAttribute('data-tone', tone);
      // glyph present (the Badge primitive renders an icon svg) — not color alone.
      expect(tag!.querySelector('svg')).not.toBeNull();
      unmount();
    }
    const { container } = render(<WorkTypeTag value={'OTHER' as WorkType} />);
    expect(container).toBeEmptyDOMElement();
  });
});
