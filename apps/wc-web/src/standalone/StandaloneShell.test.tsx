import { render, screen } from '@testing-library/react';
import { describe, it, expect, afterEach, vi } from 'vitest';
import { StandaloneShell } from './StandaloneShell';
import {
  setAccessTokenProvider,
  setDemoAuthHeaderApplier,
} from '../app/authAccessor';

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
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
    expect(await screen.findByText(/weekly commitments/i)).toBeInTheDocument();
  });
});
