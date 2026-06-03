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
    // getMe rides demo mode. Mock it to an IC so '/' redirects to the workspace.
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            employeeId: '22222222-2222-2222-2222-222222222222',
            email: 'ivy@example.com',
            displayName: 'Ivy Chen',
            role: 'IC',
            persona: 'demo-employee-ic-1',
            isManager: false,
          }),
          { status: 200, headers: { 'content-type': 'application/json' } },
        ),
      ),
    );

    render(<StandaloneShell />);

    // Standalone-only chrome is present (renders immediately).
    expect(screen.getByRole('button', { name: /theme/i })).toBeInTheDocument();
    expect(
      screen.getByRole('combobox', { name: /persona/i }),
    ).toBeInTheDocument();

    // The full path resolves: store → getMe(IC) → '/' redirect → weekly workspace.
    expect(await screen.findByText(/coming in 9\.7/i)).toBeInTheDocument();
  });
});
