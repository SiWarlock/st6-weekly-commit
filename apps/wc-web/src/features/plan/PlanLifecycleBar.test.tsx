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
  it('lifecycle_bar_shows_state_and_lock_affordance: conveys the current state via the stepper active node and renders the LockButton (enabled while LOCK is allowed)', () => {
    mockHooks();

    const { container } = render(<PlanLifecycleBar plan={plan()} />);

    // State is conveyed by the stepper's ACTIVE node (DRAFT) + the header-card
    // status pill (ST.8c moved the canonical pill into the bar's top row).
    const active = container.querySelector(
      '[data-cy="stepper-node"][data-status="active"]',
    );
    expect(active).toHaveAttribute('data-state', 'DRAFT');
    expect(active).toHaveTextContent(/draft/i);
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
    // No optimistic flip: the stepper's active node still reads LOCKED (not
    // RECONCILING) until the refetched plan prop arrives. (ST.7c dropped the
    // in-bar StatusBadge; the stepper active node is now the in-bar state signal.)
    const active = document.querySelector(
      '[data-cy="stepper-node"][data-status="active"]',
    );
    expect(active).toHaveAttribute('data-state', 'LOCKED');

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

// ST.5a — the 4-node forward-only lifecycle stepper (derived display, §3 order).
describe('PlanLifecycleBar → lifecycle stepper (ST.5a)', () => {
  const nodeStates = (root: HTMLElement) =>
    Array.from(root.querySelectorAll('[data-cy="stepper-node"]')).map((n) =>
      n.getAttribute('data-state'),
    );

  it('lifecycle_stepper_renders_four_nodes_in_order: the stepper renders Draft → Locked → Reconciling → Reconciled in §3 lifecycle order', () => {
    mockHooks();
    const { container } = render(<PlanLifecycleBar plan={plan()} />);

    expect(nodeStates(container)).toEqual([
      'DRAFT',
      'LOCKED',
      'RECONCILING',
      'RECONCILED',
    ]);
    const stepper = container.querySelector('[data-cy="lifecycle-stepper"]')!;
    expect(stepper).toHaveTextContent('Draft');
    expect(stepper).toHaveTextContent('Locked');
    expect(stepper).toHaveTextContent('Reconciling');
    expect(stepper).toHaveTextContent('Reconciled');
  });

  it('lifecycle_stepper_marks_done_active_pending_by_state: for state=RECONCILING, DRAFT+LOCKED are done, RECONCILING is active, RECONCILED is pending (forward-only derivation)', () => {
    mockHooks();
    const { container } = render(
      <PlanLifecycleBar
        plan={plan({ state: 'RECONCILING', allowedActions: [] })}
      />,
    );

    const statusOf = (state: string) =>
      container
        .querySelector(`[data-cy="stepper-node"][data-state="${state}"]`)
        ?.getAttribute('data-status');

    expect(statusOf('DRAFT')).toBe('done');
    expect(statusOf('LOCKED')).toBe('done');
    expect(statusOf('RECONCILING')).toBe('active');
    expect(statusOf('RECONCILED')).toBe('pending');
  });
});

// ST.7c — QA visual fixes: drop the redundant in-bar StatusBadge + constrain the
// stepper width (the "Draft ×3" redundancy + sparse-bars findings).
describe('PlanLifecycleBar → ST.7c QA visual fixes', () => {
  it('lifecycle_bar_header_card_has_status_pill: ST.8c moves the canonical plan status pill INTO the header-card top row (canon-driven reversal of the ST.7c in-bar drop); the stepper still conveys state', () => {
    mockHooks();
    const { container } = render(<PlanLifecycleBar plan={plan()} />);

    // The plan status pill lives in the header card now (mockup plan-head top row).
    expect(container.querySelector('[data-cy="status-badge"]')).not.toBeNull();
    // The stepper still conveys state via its active node.
    const active = container.querySelector(
      '[data-cy="stepper-node"][data-status="active"]',
    );
    expect(active).toHaveAttribute('data-state', 'DRAFT');
  });

  it('lifecycle_stepper_is_width_constrained: the stepper container carries a max-width class (a compact stepper, not full content-max width)', () => {
    mockHooks();
    const { container } = render(<PlanLifecycleBar plan={plan()} />);

    const stepper = container.querySelector('[data-cy="lifecycle-stepper"]');
    expect(stepper).not.toBeNull();
    expect(stepper!.className).toMatch(/\bmax-w-/);
  });
});

describe('PlanLifecycleBar → ST.8c plan-header card (mockup §C.2)', () => {
  const review: WeeklyPlanDto['managerReview'] = {
    id: 'rev-1',
    weeklyPlanId: 'plan-1',
    managerEmployeeId: 'mgr-1',
    status: 'NOT_REVIEWED',
    reviewDueAt: '2026-06-09T17:00:00Z',
    isOverdue: false,
    unresolvedDisputeCount: 0,
    allowedActions: [],
    version: 1,
  };

  it('start_reconciliation_is_gold_and_gated: the Start-reconciliation button uses the warning/gold tone (not bg-brand-600), renders RIGHT of "Add unplanned", and only when START_RECONCILIATION is allowed (§C.2 + LESSONS §11)', () => {
    mockHooks();
    const { rerender } = render(
      <PlanLifecycleBar
        plan={plan({
          state: 'LOCKED',
          allowedActions: ['ADD_UNPLANNED', 'START_RECONCILIATION'],
        })}
        onAddUnplanned={vi.fn()}
      />,
    );

    const start = screen.getByRole('button', { name: /start reconciliation/i });
    expect(start.className).toMatch(/tone-warning/);
    expect(start.className).not.toMatch(/bg-brand-600/);

    // "Add unplanned" sits LEFT of the gold primary.
    const add = screen.getByRole('button', { name: /add unplanned/i });
    expect(
      add.compareDocumentPosition(start) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();

    // Gating preserved: no START_RECONCILIATION → no button (server-authoritative).
    rerender(
      <PlanLifecycleBar
        plan={plan({ state: 'LOCKED', allowedActions: ['ADD_UNPLANNED'] })}
        onAddUnplanned={vi.fn()}
      />,
    );
    expect(
      screen.queryByRole('button', { name: /start reconciliation/i }),
    ).toBeNull();
  });

  it('close_reconciliation_is_success_and_gated: in RECONCILING with CLOSE_RECONCILIATION allowed, the Close button uses the success tone (not bg-brand-600); absent when the action isn’t allowed (§11)', () => {
    mockHooks();
    const { rerender } = render(
      <PlanLifecycleBar
        plan={plan({
          state: 'RECONCILING',
          allowedActions: ['ADD_UNPLANNED', 'CLOSE_RECONCILIATION'],
        })}
        onAddUnplanned={vi.fn()}
      />,
    );

    const close = screen.getByRole('button', {
      name: /close reconciliation/i,
    });
    expect(close.className).toMatch(/tone-success/);
    expect(close.className).not.toMatch(/bg-brand-600/);

    rerender(
      <PlanLifecycleBar
        plan={plan({ state: 'RECONCILING', allowedActions: ['ADD_UNPLANNED'] })}
        onAddUnplanned={vi.fn()}
      />,
    );
    expect(
      screen.queryByRole('button', { name: /close reconciliation/i }),
    ).toBeNull();
  });

  it('plan_header_card_composition: the header-card top row renders the week label + plan status pill + "N planned · M unplanned" counts + the review badge (when present) + the owner; the stepper sits below', () => {
    mockHooks();
    const { container } = render(
      <PlanLifecycleBar
        plan={plan({
          state: 'LOCKED',
          plannedCount: 3,
          unplannedCount: 1,
          employeeDisplayName: 'Ivy Chen',
          managerReview: review,
          allowedActions: ['ADD_UNPLANNED', 'START_RECONCILIATION'],
        })}
        onAddUnplanned={vi.fn()}
      />,
    );

    // The canonical plan status pill now lives IN the header card (ST.8c).
    expect(container.querySelector('[data-cy="status-badge"]')).not.toBeNull();
    expect(screen.getByText('3 planned · 1 unplanned')).toBeInTheDocument();
    expect(screen.getByText(/Week of/i)).toBeInTheDocument();
    expect(screen.getByText('Ivy Chen')).toBeInTheDocument();
    // The stepper still conveys lifecycle progress (bottom row).
    expect(
      container.querySelector('[data-cy="lifecycle-stepper"]'),
    ).not.toBeNull();
  });

  it('week_label_is_friendly: the header card renders the friendly week label (via WeekRangeLabel), not the raw ISO', () => {
    mockHooks();
    const { container } = render(
      <PlanLifecycleBar plan={plan({ weekStartDate: '2026-06-01' })} />,
    );
    expect(container.querySelector('[data-cy="week-range"]')).not.toBeNull();
    expect(screen.getByText(/Week of/i)).toBeInTheDocument();
    expect(container).not.toHaveTextContent('2026-06-01');
  });
});
