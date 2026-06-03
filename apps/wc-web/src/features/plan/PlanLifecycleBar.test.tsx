import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { PlanLifecycleBar } from './PlanLifecycleBar';
import { useLockPlanMutation } from './plansApi';
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

afterEach(() => {
  vi.restoreAllMocks();
});

describe('PlanLifecycleBar (state display + allowedActions-driven lifecycle affordances)', () => {
  it('lifecycle_bar_shows_state_and_lock_affordance: renders the current plan-state badge and the LockButton (enabled while LOCK is allowed)', () => {
    vi.mocked(useLockPlanMutation).mockReturnValue([
      vi.fn(),
      { isLoading: false, reset: vi.fn() },
    ] as unknown as ReturnType<typeof useLockPlanMutation>);

    render(<PlanLifecycleBar plan={plan()} />);

    // The current state is shown via the §4.2 StatusBadge (Draft).
    expect(screen.getByText(/draft/i)).toBeInTheDocument();
    // The lock affordance is present + enabled (LOCK ∈ allowedActions).
    expect(screen.getByRole('button', { name: /lock/i })).toBeEnabled();
  });
});
