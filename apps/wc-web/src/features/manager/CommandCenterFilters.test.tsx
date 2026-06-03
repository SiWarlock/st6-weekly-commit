import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { CommandCenterFilters } from './CommandCenterFilters';
import type { CommandCenterParams } from './managerApi';

function value(
  overrides: Partial<CommandCenterParams> = {},
): CommandCenterParams {
  return { weekStart: '2026-06-01', ...overrides };
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('CommandCenterFilters (drives the E13 query params, REQ-F-023)', () => {
  it('drives_E13_query_params: changing reviewState/planState/priority calls onChange with the patch; reviewState=OVERDUE passes through verbatim (server derives, client never)', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<CommandCenterFilters value={value()} onChange={onChange} />);

    // reviewState=OVERDUE — the derived filter passes through unchanged.
    await user.selectOptions(screen.getByLabelText(/review state/i), 'OVERDUE');
    expect(onChange).toHaveBeenCalledWith({ reviewState: 'OVERDUE' });

    await user.selectOptions(screen.getByLabelText(/plan state/i), 'LOCKED');
    expect(onChange).toHaveBeenCalledWith({ planState: 'LOCKED' });

    await user.selectOptions(screen.getByLabelText(/priority/i), 'P0');
    expect(onChange).toHaveBeenCalledWith({ priority: 'P0' });
  });

  it('weekStart_is_required: the weekStart control is marked required (UX-only; the server is authoritative)', () => {
    const onChange = vi.fn();
    render(<CommandCenterFilters value={value()} onChange={onChange} />);
    expect(screen.getByLabelText(/week of/i)).toBeRequired();
  });
});
