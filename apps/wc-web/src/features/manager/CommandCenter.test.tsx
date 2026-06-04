import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import type { ReactElement } from 'react';
import { Provider } from 'react-redux';
import { CommandCenter } from './CommandCenter';
import { useGetCommandCenterQuery } from './managerApi';
import { useGetPlanByIdQuery } from '../plan/plansApi';
import { useMarkReviewedMutation } from '../review/reviewApi';
import { store } from '../../app/store';
import type {
  AlignmentDisputeDto,
  ManagerCommandCenterRowDto,
  PageEnvelope,
  WeeklyCommitmentDto,
  WeeklyPlanDto,
} from '../../shared/lib/dtos';

vi.mock('./managerApi');
vi.mock('../plan/plansApi');
vi.mock('../review/reviewApi');
// CommandCenterFilters (ST.8b-2) sources its Defining-objective dropdown from the
// RCDO read — mock it so these store-free CommandCenter tests don't need a Provider.
vi.mock('../rcdo/rcdoApi', () => ({
  useGetRcdoQuery: () => ({ data: undefined, isLoading: false, isError: false }),
}));

/**
 * Render within the real Redux store — the manager-dispute-surface tests (9.14)
 * render the genuine `CommitmentList` → `DisputePanel` (+ its respond/resolve
 * mutation hooks), which need the `baseApi` store context. The managerApi/
 * plansApi/reviewApi module mocks still drive the canned data; no network fires
 * (mutation hooks don't auto-fetch; CommentThread is COMMENT-gated → no subscribe).
 */
function renderWithStore(ui: ReactElement) {
  return render(<Provider store={store}>{ui}</Provider>);
}

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
    // The 5 alignment risk chips (ST.8b RiskChips — labeled, hide-zero; all
    // nonzero here so all render), each mapped to its own data-cy slot.
    expect(r.querySelector('[data-cy="risk-misaligned"]')).toHaveTextContent(
      '2 misaligned',
    );
    expect(r.querySelector('[data-cy="risk-needsReview"]')).toHaveTextContent(
      '1 needs-review',
    );
    expect(r.querySelector('[data-cy="risk-blocked"]')).toHaveTextContent(
      '3 blocked',
    );
    expect(r.querySelector('[data-cy="risk-carryForward"]')).toHaveTextContent(
      '4 carry-fwd',
    );
    expect(r.querySelector('[data-cy="risk-dispute"]')).toHaveTextContent(
      '5 dispute',
    );
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

    await user.click(screen.getByRole('button', { name: 'Review' }));

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
    await user.click(screen.getByRole('button', { name: 'Review' }));
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

// ST.4 table density: sticky header + hoverable rows (the deterministic pins;
// exact 44px pixel rhythm is an ST.7 /design-review concern).
describe('CommandCenter → table density skin (ST.4)', () => {
  it('command_center_table_is_dense_and_sticky: thead is sticky (sticky top-0); data rows are hoverable (hover:bg-surface-hover)', () => {
    mockQuery({ data: env([row()]) });
    render(<CommandCenter />);

    const thead = document.querySelector('[data-cy="cc-table"] thead');
    expect(thead).not.toBeNull();
    expect(thead!.className).toContain('sticky');
    expect(thead!.className).toContain('top-0');

    const dataRow = document.querySelector('[data-cy="cc-row"]');
    expect(dataRow).not.toBeNull();
    expect(dataRow!.className).toContain('hover:bg-surface-hover');
  });
});

// ST.6b — dense table: zebra rows + tone-colored alignment count pills.
describe('CommandCenter → dense table (ST.6b)', () => {
  it('command_center_rows_are_zebra_striped: consecutive data rows alternate parity (data-row-parity even/odd)', () => {
    mockQuery({
      data: env([
        row({ employeeId: 'e1', employeeDisplayName: 'Ann' }),
        row({ employeeId: 'e2', employeeDisplayName: 'Bo' }),
        row({ employeeId: 'e3', employeeDisplayName: 'Cy' }),
      ]),
    });
    render(<CommandCenter />);

    const parities = Array.from(
      document.querySelectorAll('[data-cy="cc-row"]'),
    ).map((r) => r.getAttribute('data-row-parity'));
    expect(parities).toEqual(['even', 'odd', 'even']);
  });

  it('alignment_counts_render_as_tone_pills: each count is a pill with the §7 tone + a glyph + an accessible label (title), not plain "label: N" text — never color-alone (REQ-S-005)', () => {
    mockQuery({
      data: env([
        row({
          misalignedCount: 2,
          needsReviewCount: 1,
          blockedCount: 0,
          carryForwardCount: 4,
          unresolvedDisputeCount: 3,
        }),
      ]),
    });
    render(<CommandCenter />);

    const pill = (kind: string) =>
      document.querySelector(`[data-cy="risk-${kind}"]`) as HTMLElement;

    // ST.8b — the CC risk chips use CC_RISK_CHIP_TAXONOMY (the CommandCenter.jsx
    // CANON), distinct from the heatmap's RISK_TAXONOMY: misaligned→accent,
    // needs-review→info, carry-fwd→warning(ring), dispute→failure.
    expect(pill('misaligned')).toHaveAttribute('data-tone', 'accent');
    expect(pill('needsReview')).toHaveAttribute('data-tone', 'info');
    expect(pill('carryForward')).toHaveAttribute('data-tone', 'warning');
    expect(pill('dispute')).toHaveAttribute('data-tone', 'failure');

    // Glyph + count + label (never color-alone, REQ-S-005).
    expect(pill('misaligned').querySelector('svg')).not.toBeNull();
    expect(pill('misaligned')).toHaveTextContent('2 misaligned');

    // Hide-zero: a zero count renders NO chip (blocked:0 omitted).
    expect(document.querySelector('[data-cy="risk-blocked"]')).toBeNull();
  });
});

// ST.6c — the per-row review surface opens in a themed Flowbite Drawer (right-
// slide + scrim) instead of an inline detail <tr>. Render-only container swap:
// the existing ManagerRowReview body + view-states + server-gating are unchanged.
describe('CommandCenter → review Drawer (ST.6c)', () => {
  const lockedPlan = {
    id: 'plan-1',
    employeeId: 'emp-1',
    employeeDisplayName: 'Ivy Chen',
    weekStartDate: '2026-06-01',
    weekEndDate: '2026-06-07',
    state: 'LOCKED' as const,
    plannedCount: 1,
    unplannedCount: 0,
    commitments: [],
    managerReview: {
      id: 'rev-1',
      weeklyPlanId: 'plan-1',
      managerEmployeeId: 'mgr-1',
      status: 'NOT_REVIEWED' as const,
      reviewDueAt: '2026-06-09T17:00:00Z',
      isOverdue: false,
      unresolvedDisputeCount: 0,
      allowedActions: ['MARK_REVIEWED' as const],
      version: 1,
    },
    allowedActions: [],
    version: 1,
  };

  it('command_center_review_opens_in_drawer: clicking Review opens the review surface inside a [data-cy="review-drawer"] Drawer (not an inline cc-row-detail <tr>); onClose clears it', async () => {
    const user = userEvent.setup();
    mockQuery({ data: env([row({ weeklyPlanId: 'plan-1' })]) });
    mockPlanById({ data: lockedPlan });
    vi.mocked(useMarkReviewedMutation).mockReturnValue([
      vi.fn(),
      { isLoading: false, reset: vi.fn() },
    ] as unknown as ReturnType<typeof useMarkReviewedMutation>);

    render(<CommandCenter />);
    // Closed: the review body is not mounted (lazy plan fetch) and there is no
    // inline detail row — the old expand-in-place <tr> is gone.
    expect(screen.queryByRole('button', { name: /mark reviewed/i })).toBeNull();
    expect(document.querySelector('[data-cy="cc-row-detail"]')).toBeNull();

    await user.click(screen.getByRole('button', { name: 'Review' }));

    // The review surface now renders INSIDE the Drawer, not an inline <tr>.
    const drawer = document.querySelector(
      '[data-cy="review-drawer"]',
    ) as HTMLElement;
    expect(drawer).not.toBeNull();
    expect(
      within(drawer).getByRole('button', { name: /mark reviewed/i }),
    ).toBeInTheDocument();
    expect(document.querySelector('[data-cy="cc-row-detail"]')).toBeNull();
    // Header identifies the report.
    expect(within(drawer).getByText('Ivy Chen')).toBeInTheDocument();

    // onClose (the Drawer's close control) clears the expand → the body unmounts.
    await user.click(within(drawer).getByRole('button', { name: /close/i }));
    expect(screen.queryByRole('button', { name: /mark reviewed/i })).toBeNull();
  });

  it('review_drawer_preserves_view_states_and_gating: an IDOR-safe ErrorState(safeMessage) renders inside the Drawer body and mark-reviewed still self-gates — no regression from the container swap (§6/§7)', async () => {
    const user = userEvent.setup();
    mockQuery({ data: env([row({ weeklyPlanId: 'plan-1' })]) });
    vi.mocked(useMarkReviewedMutation).mockReturnValue([
      vi.fn(),
      { isLoading: false, reset: vi.fn() },
    ] as unknown as ReturnType<typeof useMarkReviewedMutation>);
    mockPlanById({
      isError: true,
      error: { safeMessage: 'This plan is not available.' },
    });

    render(<CommandCenter />);
    await user.click(screen.getByRole('button', { name: 'Review' }));

    const drawer = document.querySelector(
      '[data-cy="review-drawer"]',
    ) as HTMLElement;
    expect(drawer).not.toBeNull();
    // View-state preserved INSIDE the drawer body (IDOR-safe safeMessage).
    expect(
      within(drawer).getByText('This plan is not available.'),
    ).toBeInTheDocument();
    expect(drawer.querySelector('[data-cy="error-state"]')).not.toBeNull();
    // Gating preserved: a 404 → no review → no mark-reviewed affordance.
    expect(screen.queryByRole('button', { name: /mark reviewed/i })).toBeNull();
  });
});

// 9.14 — manager plan-detail dispute surface: the review Drawer renders the
// report's commitments via the allowedActions-gated CommitmentList, so the
// manager's now-live (backend 5.5b) OPEN_DISPUTE / RESOLVE_DISPUTE controls have
// a home. Rendered with the REAL CommitmentList → DisputePanel (store-wrapped).
describe('CommandCenter → manager dispute surface (9.14)', () => {
  function mgrCommitment(
    overrides: Partial<WeeklyCommitmentDto> & { id: string },
  ): WeeklyCommitmentDto {
    return {
      weeklyPlanId: 'plan-1',
      commitmentKind: 'PLANNED',
      title: `Commitment ${overrides.id}`,
      priority: 'P1',
      workType: 'STRATEGIC',
      confidence: 'HIGH',
      alignmentStatus: 'NEEDS_REVIEW',
      allowedActions: [],
      version: 0,
      ...overrides,
    };
  }

  function openDispute(
    overrides: Partial<AlignmentDisputeDto> = {},
  ): AlignmentDisputeDto {
    return {
      id: 'd-1',
      commitmentId: 'c-1',
      managerEmployeeId: 'mgr-1',
      status: 'OPEN',
      flagType: 'MISALIGNED',
      managerNote: 'Off-strategy — re-link or re-scope.',
      allowedActions: [],
      version: 0,
      ...overrides,
    };
  }

  function plan(
    commitments: WeeklyCommitmentDto[],
    review = true,
  ): WeeklyPlanDto {
    return {
      id: 'plan-1',
      employeeId: 'emp-1',
      employeeDisplayName: 'Ivy Chen',
      weekStartDate: '2026-06-01',
      weekEndDate: '2026-06-07',
      state: 'LOCKED',
      plannedCount: commitments.length,
      unplannedCount: 0,
      commitments,
      managerReview: review
        ? {
            id: 'rev-1',
            weeklyPlanId: 'plan-1',
            managerEmployeeId: 'mgr-1',
            status: 'NOT_REVIEWED',
            reviewDueAt: '2026-06-09T17:00:00Z',
            isOverdue: false,
            unresolvedDisputeCount: 0,
            allowedActions: ['MARK_REVIEWED'],
            version: 1,
          }
        : null,
      allowedActions: [],
      version: 1,
    };
  }

  async function expandReview(planData: WeeklyPlanDto) {
    const user = userEvent.setup();
    mockQuery({ data: env([row({ weeklyPlanId: 'plan-1' })]) });
    mockPlanById({ data: planData });
    vi.mocked(useMarkReviewedMutation).mockReturnValue([
      vi.fn(),
      { isLoading: false, reset: vi.fn() },
    ] as unknown as ReturnType<typeof useMarkReviewedMutation>);
    renderWithStore(<CommandCenter />);
    await user.click(screen.getByRole('button', { name: 'Review' }));
    return document.querySelector('[data-cy="review-drawer"]') as HTMLElement;
  }

  it('manager_review_drawer_renders_report_commitments: the review Drawer renders the report plan commitments via CommitmentList (a row per commitment)', async () => {
    const drawer = await expandReview(
      plan([
        mgrCommitment({ id: 'c-1', title: 'Ship the release train' }),
        mgrCommitment({ id: 'c-2', title: 'Cut activation time' }),
      ]),
    );
    expect(drawer.querySelector('[data-cy="commitment-list"]')).not.toBeNull();
    expect(drawer.querySelectorAll('[data-cy="commitment-row"]')).toHaveLength(
      2,
    );
    expect(
      within(drawer).getByText('Ship the release train'),
    ).toBeInTheDocument();
  });

  it('manager_open_dispute_control_renders_when_authorized: an undisputed commitment with OPEN_DISPUTE ∈ allowedActions shows the open form; one without it does not (§11)', async () => {
    const drawer = await expandReview(
      plan([
        mgrCommitment({
          id: 'c-1',
          title: 'Disputable',
          allowedActions: ['OPEN_DISPUTE'],
        }),
        mgrCommitment({
          id: 'c-2',
          title: 'Not disputable',
          allowedActions: [],
        }),
      ]),
    );
    const rowOf = (title: string) =>
      within(drawer)
        .getByText(title)
        .closest('[data-cy="commitment-row"]') as HTMLElement;
    expect(
      rowOf('Disputable').querySelector('[data-cy="dispute-open-form"]'),
    ).not.toBeNull();
    expect(
      rowOf('Not disputable').querySelector('[data-cy="dispute-open-form"]'),
    ).toBeNull();
  });

  it('manager_resolve_control_renders_on_disputed_commitment: a disputed commitment whose dispute.allowedActions has RESOLVE_DISPUTE shows the dispute display + the resolve control', async () => {
    const drawer = await expandReview(
      plan([
        mgrCommitment({
          id: 'c-1',
          title: 'Disputed work',
          dispute: openDispute({ allowedActions: ['RESOLVE_DISPUTE'] }),
        }),
      ]),
    );
    expect(drawer.querySelector('[data-cy="dispute-panel"]')).not.toBeNull();
    expect(drawer.querySelector('[data-cy="dispute-resolve"]')).not.toBeNull();
    // The IC-only respond control does NOT render for the manager.
    expect(drawer.querySelector('[data-cy="dispute-respond-form"]')).toBeNull();
  });

  it('manager_view_hides_ic_authoring_controls: a LOCKED report plan shows no IC edit/delete/reconciliation/carry-forward controls (their allowedActions/state absent)', async () => {
    const drawer = await expandReview(
      plan([
        mgrCommitment({
          id: 'c-1',
          title: 'Locked work',
          allowedActions: ['OPEN_DISPUTE'],
        }),
      ]),
    );
    expect(
      within(drawer).queryByRole('button', { name: /^edit$/i }),
    ).toBeNull();
    expect(
      within(drawer).queryByRole('button', { name: /delete/i }),
    ).toBeNull();
    expect(
      within(drawer).queryByRole('button', { name: /carry forward/i }),
    ).toBeNull();
    expect(
      within(drawer).queryByRole('button', { name: /record outcome/i }),
    ).toBeNull();
  });

  it('no_review_plan_still_renders_commitments: a plan with managerReview absent still renders the commitment list (no early-return swallow)', async () => {
    const drawer = await expandReview(
      plan([mgrCommitment({ id: 'c-1', title: 'Orphan work' })], false),
    );
    expect(drawer.querySelector('[data-cy="commitment-list"]')).not.toBeNull();
    expect(within(drawer).getByText('Orphan work')).toBeInTheDocument();
  });
});
