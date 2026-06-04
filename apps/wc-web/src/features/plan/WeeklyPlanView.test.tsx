import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { WeeklyPlanView } from './WeeklyPlanView';
import {
  useGetCurrentPlanQuery,
  useLockPlanMutation,
  useStartReconciliationMutation,
  useCloseReconciliationMutation,
} from './plansApi';
import {
  useCarryForwardMutation,
  useUpdateCommitmentMutation,
  useCreateCommitmentMutation,
  useAddUnplannedCommitmentMutation,
  useDeleteCommitmentMutation,
} from '../commitment/commitmentsApi';
import { useGetSyncRecordsQuery, useRetrySyncMutation } from '../sync/syncApi';
import type {
  WeeklyPlanDto,
  WeeklyCommitmentDto,
  OutlookSyncRecordDto,
} from '../../shared/lib/dtos';

vi.mock('./plansApi');
vi.mock('../commitment/commitmentsApi');
vi.mock('../sync/syncApi');
// The unplanned CommitmentForm (ADD_UNPLANNED toggle) mounts the RCDO picker,
// which owns its own query hook — stub it so the view test stays store-free.
vi.mock('../rcdo/SupportingOutcomePicker', () => ({
  SupportingOutcomePicker: () => <div data-testid="so-picker" />,
}));

function tuple() {
  return [vi.fn(), { isLoading: false, reset: vi.fn() }] as unknown as never;
}

beforeEach(() => {
  // WeeklyPlanView mounts PlanLifecycleBar (lock/start/close), CommitmentList's
  // per-row CarryForwardButton/ReconciliationOutcomeForm, and CommitmentForm.
  vi.mocked(useLockPlanMutation).mockReturnValue(tuple());
  vi.mocked(useStartReconciliationMutation).mockReturnValue(tuple());
  vi.mocked(useCloseReconciliationMutation).mockReturnValue(tuple());
  vi.mocked(useCarryForwardMutation).mockReturnValue(tuple());
  vi.mocked(useUpdateCommitmentMutation).mockReturnValue(tuple());
  vi.mocked(useCreateCommitmentMutation).mockReturnValue(tuple());
  vi.mocked(useAddUnplannedCommitmentMutation).mockReturnValue(tuple());
  vi.mocked(useDeleteCommitmentMutation).mockReturnValue(tuple());
  // Sync surface (9.12) — default to no records (no sync panel) unless overridden.
  mockSync([]);
  vi.mocked(useRetrySyncMutation).mockReturnValue(tuple());
});

function mockSync(records: OutlookSyncRecordDto[]) {
  vi.mocked(useGetSyncRecordsQuery).mockReturnValue({
    data: records,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useGetSyncRecordsQuery>);
}

function commitment(
  overrides: Partial<WeeklyCommitmentDto> & { id: string },
): WeeklyCommitmentDto {
  return {
    weeklyPlanId: 'plan-1',
    commitmentKind: 'PLANNED',
    title: `Commitment ${overrides.id}`,
    priority: 'P1',
    workType: 'STRATEGIC',
    confidence: 'HIGH',
    alignmentStatus: 'ALIGNED',
    allowedActions: [],
    version: 0,
    ...overrides,
  };
}

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
    commitments: [commitment({ id: 'c-1', title: 'Ship onboarding' })],
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

  it('reconciling_plan_surfaces_outcome_and_carryforward_affordances: in RECONCILING the view mounts BOTH the carry-forward control AND the outcome form for an unresolved CARRY_FORWARD commitment; in DRAFT it mounts neither', () => {
    mockQuery({
      data: plan({
        state: 'RECONCILING',
        allowedActions: [],
        commitments: [
          commitment({
            id: 'c-1',
            title: 'Carry me over',
            allowedActions: ['CARRY_FORWARD'],
          }),
        ],
      }),
    });
    const { rerender } = render(<WeeklyPlanView />);

    // The carry-forward affordance (CarryForwardButton) is reachable.
    expect(
      screen.getByRole('button', { name: /carry forward/i }),
    ).toBeInTheDocument();
    // The outcome form (ReconciliationOutcomeForm's labelled select) is reachable.
    expect(screen.getByLabelText(/outcome/i)).toBeInTheDocument();

    // DRAFT → no reconciliation affordances.
    mockQuery({ data: plan({ state: 'DRAFT' }) });
    rerender(<WeeklyPlanView />);
    expect(screen.queryByRole('button', { name: /carry forward/i })).toBeNull();
    expect(screen.queryByLabelText(/outcome/i)).toBeNull();
  });

  it('add_unplanned_toggle_reveals_the_unplanned_commitment_form: when ADD_UNPLANNED is allowed, the lifecycle bar exposes the toggle and clicking it mounts the unplanned CommitmentForm (E11)', async () => {
    const user = userEvent.setup();
    mockQuery({
      data: plan({
        state: 'RECONCILING',
        allowedActions: ['ADD_UNPLANNED'],
        commitments: [
          commitment({
            id: 'c-1',
            title: 'Already recorded',
            reconciliationOutcome: 'COMPLETED',
          }),
        ],
      }),
    });
    render(<WeeklyPlanView />);

    // The unplanned form is not mounted until the toggle is clicked.
    expect(
      screen.queryByRole('button', { name: /add unplanned commitment/i }),
    ).toBeNull();

    await user.click(screen.getByRole('button', { name: /^add unplanned$/i }));

    // The unplanned CommitmentForm (its distinct submit) is now mounted.
    expect(
      screen.getByRole('button', { name: /add unplanned commitment/i }),
    ).toBeInTheDocument();
  });

  it('edit_flow_reachable_from_WeeklyPlanView: a DRAFT row exposes Edit + Delete; clicking Edit opens CommitmentForm in edit mode (Save changes) pre-filled for that commitment (9.7b reach)', async () => {
    const user = userEvent.setup();
    mockQuery({
      data: plan({
        state: 'DRAFT',
        commitments: [commitment({ id: 'c-1', title: 'Ship onboarding' })],
      }),
    });
    render(<WeeklyPlanView />);

    // Per-row Delete affordance (DeleteCommitmentButton) is reachable.
    expect(
      screen.getByRole('button', { name: /^delete$/i }),
    ).toBeInTheDocument();
    // No edit form until Edit is clicked.
    expect(screen.queryByRole('button', { name: /save changes/i })).toBeNull();

    await user.click(screen.getByRole('button', { name: /edit/i }));

    // The edit form opens (Save changes) pre-filled for that commitment.
    expect(
      screen.getByRole('button', { name: /save changes/i }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/title/i)).toHaveValue('Ship onboarding');
  });

  it('plan_view_shows_sync_and_stays_usable_with_FAILED: a FAILED sync record renders the warning + retry, AND the lifecycle bar + commitment list stay rendered/usable — proving the non-blocking guarantee (rule #4, REQ-E-004/REQ-UX-004)', () => {
    mockSync([
      {
        id: 'sync-1',
        ownerEmployeeId: 'emp-1',
        relatedType: 'WEEKLY_PLAN',
        relatedId: 'plan-1',
        eventKind: 'IC_PLANNING',
        status: 'FAILED',
        safeMessage: 'Calendar sync failed; you can retry.',
        failureCode: 'GRAPH_FORBIDDEN',
        retryCount: 1,
        allowedActions: ['RETRY_SYNC'],
        version: 0,
      },
    ]);
    mockQuery({
      data: plan({
        state: 'DRAFT',
        commitments: [commitment({ id: 'c-1', title: 'Ship onboarding' })],
      }),
    });
    render(<WeeklyPlanView />);

    // The FAILED warning + retry are visible.
    expect(
      screen.getByText('Calendar sync failed; you can retry.'),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /retry sync/i }),
    ).toBeInTheDocument();
    // rule #7: the failure code is never rendered.
    expect(screen.queryByText(/GRAPH_FORBIDDEN/)).toBeNull();

    // NON-BLOCKING (rule #4): the lifecycle bar (Lock) + commitment list stay
    // rendered/usable alongside the FAILED sync.
    expect(screen.getByRole('button', { name: /lock/i })).toBeInTheDocument();
    expect(screen.getByText('Ship onboarding')).toBeInTheDocument();
  });
});

// Step-7.5 reachability (9.11b): the plan-level comment thread mounts wired to
// {PLAN, planId} iff the plan allows COMMENT. Collapsed by default → no query
// fires, so the real CommentThread renders without a store.
describe('WeeklyPlanView → plan-level CommentThread wiring (9.11b reachability)', () => {
  it('comment_thread_reachable_for_plan_gated: a plan that allows COMMENT mounts a CommentThread for {PLAN, planId}; a plan without COMMENT mounts none', () => {
    mockQuery({ data: plan({ state: 'LOCKED', allowedActions: ['COMMENT'] }) });
    const { container, rerender } = render(<WeeklyPlanView />);
    expect(
      container.querySelector(
        '[data-cy="comment-thread"][data-target-type="PLAN"][data-target-id="plan-1"]',
      ),
    ).not.toBeNull();

    // COMMENT absent on the plan → no plan-level thread.
    mockQuery({ data: plan({ state: 'LOCKED', allowedActions: ['LOCK'] }) });
    rerender(<WeeklyPlanView />);
    expect(
      container.querySelector(
        '[data-cy="comment-thread"][data-target-type="PLAN"]',
      ),
    ).toBeNull();
  });
});
