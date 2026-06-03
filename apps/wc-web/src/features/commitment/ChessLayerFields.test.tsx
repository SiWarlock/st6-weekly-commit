import { render, screen, within } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { ChessLayerFields, type ChessValue } from './ChessLayerFields';

const VALUE: ChessValue = {
  priority: 'P1',
  workType: 'STRATEGIC',
  confidence: 'HIGH',
  alignmentStatus: 'NEEDS_REVIEW',
};

describe('ChessLayerFields (priority/workType/confidence/alignmentStatus, §3)', () => {
  it('workType_omits_UNPLANNED_in_planned_form: the planned-only form offers STRATEGIC/MAINTENANCE/BLOCKER but never UNPLANNED', () => {
    render(
      <ChessLayerFields value={VALUE} onChange={vi.fn()} planState="DRAFT" />,
    );
    const workType = screen.getByLabelText(/work type/i);
    const options = within(workType)
      .getAllByRole('option')
      .map((o) => (o as HTMLOptionElement).value);
    expect(options).toEqual(
      expect.arrayContaining(['STRATEGIC', 'MAINTENANCE', 'BLOCKER']),
    );
    expect(options).not.toContain('UNPLANNED');
  });

  it('alignmentStatus_readonly_when_not_draft: alignmentStatus is an editable control in DRAFT but static labelled text once the plan is past DRAFT (§3 baseline)', () => {
    const { rerender } = render(
      <ChessLayerFields value={VALUE} onChange={vi.fn()} planState="DRAFT" />,
    );
    // DRAFT → editable.
    expect(screen.getByLabelText(/alignment/i)).toBeInTheDocument();

    // LOCKED → read-only static text (NOT an editable control).
    rerender(
      <ChessLayerFields value={VALUE} onChange={vi.fn()} planState="LOCKED" />,
    );
    expect(screen.queryByLabelText(/alignment/i)).toBeNull();
    expect(
      document.querySelector('[data-cy="alignment-readonly"]'),
    ).not.toBeNull();
  });
});
