import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { CommandCenter } from './CommandCenter';
import { useGetCommandCenterQuery } from './managerApi';
import { useGetPlanByIdQuery } from '../plan/plansApi';
import { useMarkReviewedMutation } from '../review/reviewApi';
import type {
  ManagerCommandCenterRowDto,
  PageEnvelope,
  WeeklyPlanDto,
} from '../../shared/lib/dtos';

vi.mock('./managerApi');
vi.mock('../plan/plansApi');
vi.mock('../review/reviewApi');

function row(
  overrides: Partial<ManagerCommandCenterRowDto> = {},
): ManagerCommandCenterRowDto {
  return {
    managerEmployeeId: 'mgr-1',
    employeeId: 'emp-1',
    employeeDisplayName: 'Ivy Chen',
    weeklyPlanId: 'plan-1',
    weekStartDate: '2026-06-01',
    planState: 'LOCKED',
    reviewStatus: 'NOT_REVIEWED',
    reviewDueAt: '2026-06-09T17:00:00Z',
    isReviewOverdue: false,
    plannedCount: 3,
    unplannedCount: 1,
    misalignedCount: 0,
    needsReviewCount: 0,
    blockedCount: 0,
    carryForwardCount: 0,
    unresolvedDisputeCount: 0,
    updatedAt: '2026-06-02T10:00:00Z',
    ...overrides,
  };
}

function env(
  rows: ManagerCommandCenterRowDto[],
  page: Partial<PageEnvelope<ManagerCommandCenterRowDto>['page']> = {},
): PageEnvelope<ManagerCommandCenterRowDto> {
  return {
    content: rows,
    page: {
      number: 0,
      size: 25,
      totalElements: rows.length,
      totalPages: 1,
      ...page,
    },
    sort: [{ property: 'weekStartDate', direction: 'DESC' }],
  };
}

function mockQuery(value: {
  data?: PageEnvelope<ManagerCommandCenterRowDto>;
  isLoading?: boolean;
  isError?: boolean;
  error?: unknown;
}) {
  vi.mocked(useGetCommandCenterQuery).mockReturnValue({
    data: value.data,
    isLoading: value.isLoading ?? false,
    isError: value.isError ?? false,
    error: value.error,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useGetCommandCenterQuery>);
}

function mockPlanById(value: {
  data?: WeeklyPlanDto;
  isLoading?: boolean;
  isError?: boolean;
  error?: unknown;
}) {
  vi.mocked(useGetPlanByIdQuery).mockReturnValue({
    data: value.data,
    isLoading: value.isLoading ?? false,
    isError: value.isError ?? false,
    error: value.error,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useGetPlanByIdQuery>);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('CommandCenter (E13 roll-up — rows, view-states, pagination, mark-reviewed reach)', () => {
  it('renders_rows_with_counts_and_overdue_flag: each row shows planState + reviewStatus (with the derived Overdue overlay) + the 5 alignment counts, without opening the plan (REQ-UX-003)', () => {
    mockQuery({
      data: env([
        row({
          isReviewOverdue: true,
          misalignedCount: 2,
          needsReviewCount: 1,
          blockedCount: 3,
          carryForwardCount: 4,
          unresolvedDisputeCount: 5,
        }),
      ]),
    });

    render(<CommandCenter />);
    const r = screen
      .getByText('Ivy Chen')
      .closest('[data-cy="cc-row"]') as HTMLElement;

    // planState badge (Locked) + the derived OVERDUE overlay on the review badge.
    // Exact strings: /locked/i would also match the "Blocked" count label.
    expect(within(r).getByText('Locked')).toBeInTheDocument();
    expect(within(r).getByText('Overdue')).toBeInTheDocument();
    // The 5 alignment counts, each mapped to its own slot.
    expect(r.querySelector('[data-cy="cc-misaligned"]')).toHaveTextContent('2');
    expect(r.querySelector('[data-cy="cc-needsReview"]')).toHaveTextContent(
      '1',
    );
    expect(r.querySelector('[data-cy="cc-blocked"]')).toHaveTextContent('3');
    expect(r.querySelector('[data-cy="cc-carryForward"]')).toHaveTextContent(
      '4',
    );
    expect(
      r.querySelector('[data-cy="cc-unresolvedDispute"]'),
    ).toHaveTextContent('5');
  });

  it('renders_loading_empty_error_states: loading → LoadingState; zero content → EmptyState; query error → ErrorState(safeMessage) (§7)', () => {
    // loading
    mockQuery({ isLoading: true });
    const { rerender } = render(<CommandCenter />);
    expect(screen.getByRole('status')).toBeInTheDocument();

    // error
    mockQuery({
      isError: true,
      error: { safeMessage: 'You do not have access to this view.' },
    });
    rerender(<CommandCenter />);
    expect(document.querySelector('[data-cy="error-state"]')).not.toBeNull();
    expect(
      screen.getByText('You do not have access to this view.'),
    ).toBeInTheDocument();

    // empty (no reports)
    mockQuery({ data: env([]) });
    rerender(<CommandCenter />);
    expect(document.querySelector('[data-cy="empty-state"]')).not.toBeNull();
  });

  it('paginates_via_envelope: the page indicator reflects page.number/totalPages; clicking next re-queries with the incremented page param (no client-side slicing)', async () => {
    const user = userEvent.setup();
    mockQuery({ data: env([row()], { number: 0, totalPages: 3 }) });

    render(<CommandCenter />);
    expect(screen.getByText(/page 1 of 3/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /next page/i }));

    // The query hook was re-invoked with page=1 (server-side pagination).
    const calls = vi.mocked(useGetCommandCenterQuery).mock.calls;
    const lastArg = calls[calls.length - 1]?.[0] as { page?: number };
    expect(lastArg.page).toBe(1);
  });

  it('mark_reviewed_reachable_from_command_center: expanding a row lazily fetches its plan (E4 manager-of-owner) and surfaces MarkReviewedAction gated on managerReview.allowedActions (Q2 wiring → Step-7.5 E16 reachability)', async () => {
    const user = userEvent.setup();
    mockQuery({ data: env([row({ weeklyPlanId: 'plan-1' })]) });
    mockPlanById({
      data: {
        id: 'plan-1',
        employeeId: 'emp-1',
        employeeDisplayName: 'Ivy Chen',
        weekStartDate: '2026-06-01',
        weekEndDate: '2026-06-07',
        state: 'LOCKED',
        plannedCount: 1,
        unplannedCount: 0,
        commitments: [],
        managerReview: {
          id: 'rev-1',
          weeklyPlanId: 'plan-1',
          managerEmployeeId: 'mgr-1',
          status: 'NOT_REVIEWED',
          reviewDueAt: '2026-06-09T17:00:00Z',
          isOverdue: false,
          unresolvedDisputeCount: 0,
          allowedActions: ['MARK_REVIEWED'],
          version: 1,
        },
        allowedActions: [],
        version: 1,
      },
    });
    vi.mocked(useMarkReviewedMutation).mockReturnValue([
      vi.fn(),
      { isLoading: false, reset: vi.fn() },
    ] as unknown as ReturnType<typeof useMarkReviewedMutation>);

    render(<CommandCenter />);
    // The action is not mounted until the row is expanded (lazy plan fetch).
    expect(screen.queryByRole('button', { name: /mark reviewed/i })).toBeNull();

    await user.click(screen.getByRole('button', { name: /review/i }));

    expect(
      screen.getByRole('button', { name: /mark reviewed/i }),
    ).toBeInTheDocument();
  });

  it('row_expand_renders_loading_then_idor_safe_error_state: expanding a row shows LoadingState while the lazy plan fetch is pending, and an IDOR-safe ErrorState(safeMessage) on a 404 — never a crash or existence leak (§6, §7 partial-state)', async () => {
    const user = userEvent.setup();
    mockQuery({ data: env([row({ weeklyPlanId: 'plan-1' })]) });
    vi.mocked(useMarkReviewedMutation).mockReturnValue([
      vi.fn(),
      { isLoading: false, reset: vi.fn() },
    ] as unknown as ReturnType<typeof useMarkReviewedMutation>);

    // Pending lazy plan fetch → LoadingState on expand.
    mockPlanById({ isLoading: true });
    const { rerender } = render(<CommandCenter />);
    await user.click(screen.getByRole('button', { name: /review/i }));
    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /mark reviewed/i })).toBeNull();

    // A 404 (non-direct-report or missing) → ErrorState(safeMessage), IDOR-safe:
    // the server's generic safeMessage, never the raw error / existence leak.
    mockPlanById({
      isError: true,
      error: { safeMessage: 'This plan is not available.' },
    });
    rerender(<CommandCenter />);
    expect(document.querySelector('[data-cy="error-state"]')).not.toBeNull();
    expect(screen.getByText('This plan is not available.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /mark reviewed/i })).toBeNull();
  });
});
