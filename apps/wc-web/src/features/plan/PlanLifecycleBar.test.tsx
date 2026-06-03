import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { PlanLifecycleBar } from './PlanLifecycleBar';
import {
  useLockPlanMutation,
  useStartReconciliationMutation,
  useCloseReconciliationMutation,
} from './plansApi';
import type { WeeklyPlanDto } from '../../shared/lib/dtos';

vi.mock('./plansApi');

function plan(overrides: Partial<WeeklyPlanDto> = {}): WeeklyPlanDto {
  return {
    id: 'plan-1',
    employeeId: 'emp-1',
    employeeDisplayName: 'Ivy Chen',
    weekStartDate: '2026-06-01',
    weekEndDate: '2026-06-07',
    state: 'DRAFT',
    plannedCount: 1,
    unplannedCount: 0,
    commitments: [],
    managerReview: null,
    allowedActions: ['LOCK'],
    version: 1,
    ...overrides,
  };
}

function mockHooks({
  start,
  close,
}: {
  start?: ReturnType<typeof vi.fn>;
  close?: ReturnType<typeof vi.fn>;
} = {}) {
  vi.mocked(useLockPlanMutation).mockReturnValue([
    vi.fn(),
    { isLoading: false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useLockPlanMutation>);
  vi.mocked(useStartReconciliationMutation).mockReturnValue([
    start ?? vi.fn(),
    { isLoading: false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useStartReconciliationMutation>);
  vi.mocked(useCloseReconciliationMutation).mockReturnValue([
    close ?? vi.fn(),
    { isLoading: false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useCloseReconciliationMutation>);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('PlanLifecycleBar (state display + allowedActions-driven lifecycle affordances)', () => {
  it('lifecycle_bar_shows_state_and_lock_affordance: renders the current plan-state badge and the LockButton (enabled while LOCK is allowed)', () => {
    mockHooks();

    render(<PlanLifecycleBar plan={plan()} />);

    // The current state is shown via the §4.2 StatusBadge (Draft).
    expect(screen.getByText(/draft/i)).toBeInTheDocument();
    // The lock affordance is present + enabled (LOCK ∈ allowedActions).
    expect(screen.getByRole('button', { name: /lock/i })).toBeEnabled();
  });

  it('renders_lifecycle_actions_per_allowedActions: START shown iff START_RECONCILIATION; CLOSE iff CLOSE_RECONCILIATION; ADD_UNPLANNED iff ADD_UNPLANNED; DRAFT-with-only-LOCK shows none of the three (server-authoritative, never re-derived)', () => {
    mockHooks();

    // LOCKED with START allowed → only Start reconciliation.
    const { rerender } = render(
      <PlanLifecycleBar
        plan={plan({
          state: 'LOCKED',
          allowedActions: ['START_RECONCILIATION'],
        })}
        onAddUnplanned={vi.fn()}
      />,
    );
    expect(
      screen.getByRole('button', { name: /start reconciliation/i }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /close reconciliation/i }),
    ).toBeNull();
    expect(screen.queryByRole('button', { name: /add unplanned/i })).toBeNull();

    // RECONCILING with CLOSE + ADD_UNPLANNED allowed → Close + Add, no Start.
    rerender(
      <PlanLifecycleBar
        plan={plan({
          state: 'RECONCILING',
          allowedActions: ['CLOSE_RECONCILIATION', 'ADD_UNPLANNED'],
        })}
        onAddUnplanned={vi.fn()}
      />,
    );
    expect(
      screen.getByRole('button', { name: /close reconciliation/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /add unplanned/i }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /start reconciliation/i }),
    ).toBeNull();

    // DRAFT with only LOCK → none of the three reconciliation affordances.
    rerender(
      <PlanLifecycleBar
        plan={plan({ state: 'DRAFT', allowedActions: ['LOCK'] })}
      />,
    );
    expect(
      screen.queryByRole('button', { name: /start reconciliation/i }),
    ).toBeNull();
    expect(
      screen.queryByRole('button', { name: /close reconciliation/i }),
    ).toBeNull();
    expect(screen.queryByRole('button', { name: /add unplanned/i })).toBeNull();
  });

  it('start_and_close_are_server_gated_no_optimistic: START click triggers the mutation without an optimistic state flip; a CLOSE error (UNPLANNED_MISSING_LINK_AT_CLOSE) renders the safeMessage verbatim', async () => {
    const user = userEvent.setup();
    const start = vi.fn(() => ({ unwrap: () => Promise.resolve({}) }));
    const close = vi.fn(() => ({
      unwrap: () =>
        Promise.reject({
          safeMessage: 'Link every unplanned commitment before closing.',
          code: 'UNPLANNED_MISSING_LINK_AT_CLOSE',
          fieldErrors: [],
        }),
    }));
    mockHooks({ start, close });

    // START: clicking does not optimistically flip the badge (LOCKED stays LOCKED
    // until the refetched plan prop arrives).
    const { rerender } = render(
      <PlanLifecycleBar
        plan={plan({
          state: 'LOCKED',
          allowedActions: ['START_RECONCILIATION'],
        })}
      />,
    );
    await user.click(
      screen.getByRole('button', { name: /start reconciliation/i }),
    );
    expect(start).toHaveBeenCalledWith('plan-1');
    expect(screen.getByText(/locked/i)).toBeInTheDocument();
    expect(screen.queryByText(/reconciling/i)).toBeNull();

    // CLOSE error surfaces verbatim.
    rerender(
      <PlanLifecycleBar
        plan={plan({
          state: 'RECONCILING',
          allowedActions: ['CLOSE_RECONCILIATION'],
        })}
      />,
    );
    await user.click(
      screen.getByRole('button', { name: /close reconciliation/i }),
    );
    expect(
      await screen.findByText(
        'Link every unplanned commitment before closing.',
      ),
    ).toBeInTheDocument();
  });
});
