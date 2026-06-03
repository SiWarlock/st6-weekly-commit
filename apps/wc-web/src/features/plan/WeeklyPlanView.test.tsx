import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { WeeklyPlanView } from './WeeklyPlanView';
import { useGetCurrentPlanQuery, useLockPlanMutation } from './plansApi';
import type { WeeklyPlanDto } from '../../shared/lib/dtos';

vi.mock('./plansApi');

beforeEach(() => {
  // WeeklyPlanView renders PlanLifecycleBar→LockButton (useLockPlanMutation).
  vi.mocked(useLockPlanMutation).mockReturnValue([
    vi.fn(),
    { isLoading: false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useLockPlanMutation>);
});

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
    commitments: [
      {
        id: 'c-1',
        weeklyPlanId: 'plan-1',
        commitmentKind: 'PLANNED',
        title: 'Ship onboarding',
        priority: 'P1',
        workType: 'STRATEGIC',
        confidence: 'HIGH',
        alignmentStatus: 'ALIGNED',
        hasUnresolvedDispute: false,
        allowedActions: [],
        version: 0,
      },
    ],
    managerReview: null,
    allowedActions: ['LOCK'],
    version: 1,
    ...overrides,
  };
}

function mockQuery(value: {
  data?: WeeklyPlanDto;
  isLoading?: boolean;
  isError?: boolean;
  error?: unknown;
}) {
  vi.mocked(useGetCurrentPlanQuery).mockReturnValue({
    data: value.data,
    isLoading: value.isLoading ?? false,
    isError: value.isError ?? false,
    error: value.error,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useGetCurrentPlanQuery>);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('WeeklyPlanView (IC workspace — getCurrentPlan view-states, §7)', () => {
  it('weekly_plan_view_renders_loading_empty_error_success: each of the four view-states renders its slot; the not-started shell prompts the first commitment', () => {
    // loading
    mockQuery({ isLoading: true });
    const { rerender } = render(<WeeklyPlanView />);
    expect(screen.getByRole('status')).toBeInTheDocument();

    // error → ErrorState with the safe message
    mockQuery({
      isError: true,
      error: { safeMessage: 'Could not load your plan.' },
    });
    rerender(<WeeklyPlanView />);
    expect(document.querySelector('[data-cy="error-state"]')).not.toBeNull();
    expect(screen.getByText('Could not load your plan.')).toBeInTheDocument();

    // empty (not-started shell: no commitments) → EmptyState prompting first commitment
    mockQuery({ data: plan({ commitments: [], plannedCount: 0 }) });
    rerender(<WeeklyPlanView />);
    expect(document.querySelector('[data-cy="empty-state"]')).not.toBeNull();

    // success → the commitment list renders
    mockQuery({ data: plan() });
    rerender(<WeeklyPlanView />);
    expect(screen.getByText('Ship onboarding')).toBeInTheDocument();
  });
});
