import { lazy, Suspense } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Link } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { AppRoutes } from './AppRoutes';
import { RouteErrorBoundary } from './RouteErrorBoundary';
import { LoadingState } from '../shared/components/LoadingState';
import { useCurrentUser } from '../features/me/useCurrentUser';
import {
  useGetCurrentPlanQuery,
  useLockPlanMutation,
  useStartReconciliationMutation,
  useCloseReconciliationMutation,
} from '../features/plan/plansApi';
import {
  useGetCommandCenterQuery,
  useGetHeatmapQuery,
} from '../features/manager/managerApi';
import type { WeeklyPlanDto } from '../shared/lib/dtos';

vi.mock('../features/me/useCurrentUser');
// The /weekly-commit route now renders WeeklyPlanView (9.7), which reads
// getCurrentPlan. Mock it so the routing tests stay store-free.
vi.mock('../features/plan/plansApi');
// The /manager/command-center route now renders CommandCenter (9.9), which reads
// getCommandCenter. Mock it so the routing tests stay store-free.
vi.mock('../features/manager/managerApi');

const EMPTY_PLAN: WeeklyPlanDto = {
  id: 'plan-1',
  employeeId: 'emp-1',
  employeeDisplayName: 'Ivy Chen',
  weekStartDate: '2026-06-01',
  weekEndDate: '2026-06-07',
  state: 'DRAFT',
  plannedCount: 0,
  unplannedCount: 0,
  commitments: [],
  managerReview: null,
  allowedActions: [],
  version: 1,
};

beforeEach(() => {
  vi.mocked(useGetCurrentPlanQuery).mockReturnValue({
    data: EMPTY_PLAN,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useGetCurrentPlanQuery>);
  vi.mocked(useLockPlanMutation).mockReturnValue([
    vi.fn(),
    { isLoading: false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useLockPlanMutation>);
  // PlanLifecycleBar (9.8) also reads the start/close-reconciliation hooks.
  vi.mocked(useStartReconciliationMutation).mockReturnValue([
    vi.fn(),
    { isLoading: false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useStartReconciliationMutation>);
  vi.mocked(useCloseReconciliationMutation).mockReturnValue([
    vi.fn(),
    { isLoading: false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useCloseReconciliationMutation>);
  // CommandCenter (9.9) reads getCommandCenter; a loaded empty envelope renders
  // the command-center shell (the chunk-resolved marker the routing tests assert).
  vi.mocked(useGetCommandCenterQuery).mockReturnValue({
    data: {
      content: [],
      page: { number: 0, size: 25, totalElements: 0, totalPages: 0 },
      sort: [],
    },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useGetCommandCenterQuery>);
  // HeatmapGrid (9.10) reads getHeatmap; a loaded empty response renders the
  // heatmap shell (the chunk-resolved marker the routing tests assert).
  vi.mocked(useGetHeatmapQuery).mockReturnValue({
    data: { weekStart: '2026-06-01', cells: [] },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useGetHeatmapQuery>);
});

function mockCurrentUser(value: {
  isManager?: boolean;
  isLoading?: boolean;
  isError?: boolean;
  role?: 'IC' | 'MANAGER';
}) {
  vi.mocked(useCurrentUser).mockReturnValue({
    role: value.role,
    isManager: value.isManager ?? false,
    persona: undefined,
    displayName: undefined,
    isLoading: value.isLoading ?? false,
    isError: value.isError ?? false,
  });
}

afterEach(() => {
  vi.restoreAllMocks();
});

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AppRoutes />
    </MemoryRouter>,
  );
}

describe('AppRoutes — lazy route tree + real-role gating (9.5 / REQ-NF-005 / REQ-UX-005)', () => {
  it('nonroot_routes_render_their_lazy_chunk_behind_suspense: each non-root route shows the Suspense LoadingState then resolves its own lazy chunk', async () => {
    const ROUTES: ReadonlyArray<{
      path: string;
      phrase: RegExp;
      manager: boolean;
    }> = [
      { path: '/weekly-commit', phrase: /weekly commitments/i, manager: false },
      {
        path: '/weekly-commit/history/abc-123',
        phrase: /coming in 9\.8/i,
        manager: false,
      },
      {
        path: '/manager/command-center',
        phrase: /direct-report alignment/i,
        manager: true,
      },
      {
        path: '/manager/heatmap',
        phrase: /risk by report/i,
        manager: true,
      },
    ];
    for (const { path, phrase, manager } of ROUTES) {
      mockCurrentUser({ isManager: manager });
      const { unmount } = renderAt(path);
      expect(screen.getByRole('status')).toBeInTheDocument();
      expect(await screen.findByText(phrase)).toBeInTheDocument();
      unmount();
    }
  });

  it('root_route_redirects_by_persona: "/" navigates managers to the command center and ICs to the weekly workspace; pending → LoadingState, error → ErrorState', async () => {
    // Manager → /manager/command-center
    mockCurrentUser({ isManager: true, role: 'MANAGER' });
    const mgr = renderAt('/');
    expect(
      await screen.findByText(/direct-report alignment/i),
    ).toBeInTheDocument();
    mgr.unmount();

    // IC → /weekly-commit
    mockCurrentUser({ isManager: false, role: 'IC' });
    const ic = renderAt('/');
    expect(await screen.findByText(/weekly commitments/i)).toBeInTheDocument();
    ic.unmount();

    // Pending → LoadingState (no premature redirect)
    mockCurrentUser({ isLoading: true });
    const pending = renderAt('/');
    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.queryByText(/coming in 9\./i)).toBeNull();
    pending.unmount();

    // Error → ErrorState (no blank/loop)
    mockCurrentUser({ isError: true });
    renderAt('/');
    await waitFor(() =>
      expect(document.querySelector('[data-cy="error-state"]')).not.toBeNull(),
    );
  });

  it('ic_persona_has_no_manager_route_entry: an IC hitting a manager URL is redirected away and sees no manager element or entry point (REQ-UX-005)', async () => {
    mockCurrentUser({ isManager: false, role: 'IC' });
    const { container } = renderAt('/manager/command-center');

    // Manager route is not registered for an IC → catch-all → '/' → IC default.
    expect(await screen.findByText(/weekly commitments/i)).toBeInTheDocument();
    expect(screen.queryByText(/direct-report alignment/i)).toBeNull();
    expect(container.querySelector('a[href*="/manager"]')).toBeNull();
  });

  it('manager_persona_resolves_manager_routes: a manager resolves both /manager/* lazy chunks (REQ-UX-005 positive control)', async () => {
    mockCurrentUser({ isManager: true, role: 'MANAGER' });
    const cc = renderAt('/manager/command-center');
    expect(
      await screen.findByText(/direct-report alignment/i),
    ).toBeInTheDocument();
    cc.unmount();

    renderAt('/manager/heatmap');
    expect(await screen.findByText(/risk by report/i)).toBeInTheDocument();
  });

  it('gating_fails_closed_while_me_pending: while getMe is pending, a manager URL renders no manager element (fail-closed, REQ-UX-005)', async () => {
    mockCurrentUser({ isManager: true, isLoading: true });
    renderAt('/manager/command-center');
    // useIsManager false while pending → route unregistered → '/' → LoadingState.
    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.queryByText(/direct-report alignment/i)).toBeNull();
  });

  it('failed_lazy_import_renders_errorstate: a rejected dynamic import surfaces the shared ErrorState (generic, leak-free), never a blank screen', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    const Boom = lazy(() =>
      Promise.reject(new Error('secret-chunk-path load failed')),
    );
    render(
      <RouteErrorBoundary>
        <Suspense fallback={<LoadingState delayMs={0} />}>
          <Boom />
        </Suspense>
      </RouteErrorBoundary>,
    );
    await waitFor(() =>
      expect(document.querySelector('[data-cy="error-state"]')).not.toBeNull(),
    );
    expect(screen.queryByText(/secret-chunk-path/)).toBeNull();
  });

  it('route_change_resolves_correct_lazy_module: navigating between routes swaps to the correct lazy chunk', async () => {
    mockCurrentUser({ isManager: false, role: 'IC' });
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/weekly-commit']}>
        <nav>
          <Link to="/weekly-commit/history/p-1">history</Link>
        </nav>
        <AppRoutes />
      </MemoryRouter>,
    );

    expect(await screen.findByText(/weekly commitments/i)).toBeInTheDocument();
    await user.click(screen.getByRole('link', { name: /history/i }));
    expect(await screen.findByText(/coming in 9\.8/i)).toBeInTheDocument();
    expect(screen.queryByText(/weekly commitments/i)).toBeNull();
  });
});
