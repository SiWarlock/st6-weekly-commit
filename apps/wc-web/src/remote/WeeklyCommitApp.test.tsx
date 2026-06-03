import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import WeeklyCommitApp from './WeeklyCommitApp';
import {
  getAccessToken,
  hasAccessTokenProvider,
  setAccessTokenProvider,
} from '../app/authAccessor';
import { prepareHeaders } from '../app/baseApi';
import { useCurrentUser } from '../features/me/useCurrentUser';
import {
  useGetCurrentPlanQuery,
  useLockPlanMutation,
  useStartReconciliationMutation,
  useCloseReconciliationMutation,
} from '../features/plan/plansApi';
import { useGetSyncRecordsQuery } from '../features/sync/syncApi';
import type { WeeklyPlanDto } from '../shared/lib/dtos';

// The eager route gating reads useCurrentUser (an RTK Query hook). Mock it so the
// remote-module tests isolate router/accessor wiring from the store (the real
// store-backed path is covered by StandaloneShell.test + meApi/useCurrentUser tests).
vi.mock('../features/me/useCurrentUser');
// /weekly-commit now renders WeeklyPlanView (9.7) → reads getCurrentPlan. Mock it.
vi.mock('../features/plan/plansApi');
// WeeklyPlanView (9.12) also reads getSyncRecords — mock it (no records).
vi.mock('../features/sync/syncApi');

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
  vi.mocked(useGetSyncRecordsQuery).mockReturnValue({
    data: [],
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useGetSyncRecordsQuery>);
  vi.mocked(useCloseReconciliationMutation).mockReturnValue([
    vi.fn(),
    { isLoading: false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useCloseReconciliationMutation>);
});

function mockCurrentUser(value: {
  isManager?: boolean;
  role?: 'IC' | 'MANAGER';
  isLoading?: boolean;
  isError?: boolean;
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
  vi.unstubAllEnvs();
  vi.restoreAllMocks();
  setAccessTokenProvider(null);
});

describe('WeeklyCommitApp (exposed remote module)', () => {
  it('remote_consumes_host_router_no_own_browserrouter: renders the lazy route tree inside a host-supplied router (IC "/" → weekly workspace)', async () => {
    mockCurrentUser({ isManager: false, role: 'IC' });
    render(
      <MemoryRouter>
        <WeeklyCommitApp getAccessToken={async () => 'host-jwt'} />
      </MemoryRouter>,
    );
    // The persona-aware '/' redirect resolves to the IC weekly workspace chunk,
    // rendered inside the host MemoryRouter (no BrowserRouter created by the remote).
    expect(await screen.findByText(/weekly commitments/i)).toBeInTheDocument();
  });

  it('remote_registers_host_accessor_into_seam: a host getAccessToken is wired into the 9.1 seam (auth0 Bearer uses the host token)', async () => {
    mockCurrentUser({ isManager: false, role: 'IC' });
    vi.stubEnv('VITE_AUTH_MODE', 'auth0');
    render(
      <MemoryRouter>
        <WeeklyCommitApp getAccessToken={async () => 'host-jwt-123'} />
      </MemoryRouter>,
    );
    expect(hasAccessTokenProvider()).toBe(true);
    expect(await getAccessToken()).toBe('host-jwt-123');
    const headers = await prepareHeaders(new Headers());
    expect(headers.get('Authorization')).toBe('Bearer host-jwt-123');
    expect(headers.get('X-Demo-Employee-Id')).toBeNull();
  });

  it('remote_without_accessor_surfaces_error: hosted (auth0) with no host accessor → error state, not a crash', () => {
    mockCurrentUser({ isManager: false, role: 'IC' });
    vi.stubEnv('VITE_AUTH_MODE', 'auth0');
    setAccessTokenProvider(null);
    render(
      <MemoryRouter>
        <WeeklyCommitApp />
      </MemoryRouter>,
    );
    // The WeeklyCommitApp ready-guard renders before <AppRoutes/>, so no route
    // content is reached when the host provided no accessor.
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.queryByText(/weekly commitments/i)).toBeNull();
  });
});
