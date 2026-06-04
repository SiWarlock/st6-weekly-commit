import { render, screen } from '@testing-library/react';
import { describe, it, expect, afterEach, vi, type Mock } from 'vitest';
import { type ReactNode } from 'react';
import { StandaloneShell } from './StandaloneShell';
import {
  setAccessTokenProvider,
  setDemoAuthHeaderApplier,
} from '../app/authAccessor';

// Auth0 mode statically imports the SDK (standalone-only). Mock it so the
// VITE_AUTH_MODE branch is testable without a live Auth0 tenant; the demo-mode
// test below never renders the auth0 path, so the mock is inert there.
vi.mock('@auth0/auth0-react', () => ({
  Auth0Provider: ({ children }: { children?: ReactNode }) => children,
  useAuth0: vi.fn(),
}));
import { useAuth0 } from '@auth0/auth0-react';
const mockUseAuth0 = useAuth0 as unknown as Mock;

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  setAccessTokenProvider(null);
  setDemoAuthHeaderApplier(null);
});

describe('StandaloneShell (full standalone provider tree)', () => {
  it('standalone_mounts_app_with_full_providers: mounts WeeklyCommitApp inside router+store+theme+identity, runs the gating getMe, and lands the persona-aware route', async () => {
    // Standalone wires the real store + DemoIdentityProvider; the eager gating
    // getMe rides demo mode. Route the fetch: /api/me → an IC (so '/' redirects to
    // the workspace) and /api/plans/current → a not-started plan (the workspace
    // then reads getCurrentPlan via WeeklyPlanView, 9.7).
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    const json = (body: unknown) =>
      new Response(JSON.stringify(body), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      });
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: Request) => {
        if (input.url.includes('/api/plans/current')) {
          return json({
            id: 'plan-1',
            employeeId: '22222222-2222-2222-2222-222222222222',
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
          });
        }
        return json({
          employeeId: '22222222-2222-2222-2222-222222222222',
          email: 'ivy@example.com',
          displayName: 'Ivy Chen',
          role: 'IC',
          persona: 'demo-employee-ic-1',
          isManager: false,
        });
      }),
    );

    render(<StandaloneShell />);

    // Standalone-only app-shell chrome is present (renders immediately). ST.8a
    // restyled the persona switcher from a <select> into the app-bar identity
    // dropdown (a button trigger), so it's queried as a button now.
    expect(screen.getByRole('button', { name: /theme/i })).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /persona/i }),
    ).toBeInTheDocument();

    // The full path resolves: store → getMe(IC) → '/' redirect → WeeklyPlanView.
    // (ST.8c renamed the h1 to "My Weekly Commit", which also appears in the
    // app-shell breadcrumb/nav — so assert on the empty-plan EmptyState, which is
    // unique to the rendered WeeklyPlanView for this no-commitments fixture.)
    expect(await screen.findByText(/no commitments yet/i)).toBeInTheDocument();
  });

  it('standalone_auth0Mode_gatesWithLoginScreen: VITE_AUTH_MODE=auth0 mounts the Auth0 login path (gate → login screen), and the demo PersonaSwitcher is NOT rendered (retired for the deployed real-OAuth demo)', () => {
    vi.stubEnv('VITE_AUTH_MODE', 'auth0');
    vi.stubEnv('VITE_AUTH0_DOMAIN', 'tenant.us.auth0.com');
    vi.stubEnv('VITE_AUTH0_CLIENT_ID', 'abc123');
    vi.stubEnv('VITE_AUTH0_AUDIENCE', 'https://api.wc.example.com');
    mockUseAuth0.mockReturnValue({
      isLoading: false,
      isAuthenticated: false,
      loginWithRedirect: vi.fn(),
    });

    render(<StandaloneShell />);

    // Auth0 mode, unauthenticated → the branded login screen; no app mount (no
    // getMe fetch needed). The demo PersonaSwitcher is absent in auth0 mode.
    expect(screen.getByRole('button', { name: /log in/i })).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /persona/i }),
    ).not.toBeInTheDocument();
  });

  it('standalone_auth0Mode_authenticatedNestResolves: authenticated, the full routing nest (Routes → /* → gate → AppShell → WeeklyCommitApp) renders the app and /callback does NOT shadow / (the deployed-demo critical path)', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'auth0');
    vi.stubEnv('VITE_AUTH0_DOMAIN', 'tenant.us.auth0.com');
    vi.stubEnv('VITE_AUTH0_CLIENT_ID', 'abc123');
    vi.stubEnv('VITE_AUTH0_AUDIENCE', 'https://api.wc.example.com');
    mockUseAuth0.mockReturnValue({
      isLoading: false,
      isAuthenticated: true,
      user: { sub: 'auth0|1', name: 'Dana Okafor', email: 'dana@st6demo.com' },
      getAccessTokenSilently: vi.fn().mockResolvedValue('jwt-abc'),
      loginWithRedirect: vi.fn(),
      logout: vi.fn(),
    });
    const json = (body: unknown) =>
      new Response(JSON.stringify(body), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      });
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: Request) => {
        if (input.url.includes('/api/plans/current')) {
          return json({
            id: 'plan-1',
            employeeId: '22222222-2222-2222-2222-222222222222',
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
          });
        }
        return json({
          employeeId: '22222222-2222-2222-2222-222222222222',
          email: 'ivy@example.com',
          displayName: 'Ivy Chen',
          role: 'IC',
          persona: 'demo-employee-ic-1',
          isManager: false,
        });
      }),
    );

    render(<StandaloneShell />);

    // The /* nest resolves at '/': gate (authenticated) → AppShell → WeeklyCommitApp
    // → AppRoutes → RootRedirect → the IC workspace (empty-plan EmptyState). If
    // /callback shadowed '/', we'd see the "signing you in" processing state instead.
    expect(await screen.findByText(/no commitments yet/i)).toBeInTheDocument();
    expect(screen.queryByText(/signing you in/i)).not.toBeInTheDocument();
    // The auth0 identity slot (not the demo PersonaSwitcher) is in the app-bar.
    expect(screen.getByText(/Dana Okafor/)).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /persona/i }),
    ).not.toBeInTheDocument();
  });
});
