import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, afterEach, vi } from 'vitest';
import WeeklyCommitApp from './WeeklyCommitApp';
import {
  getAccessToken,
  hasAccessTokenProvider,
  setAccessTokenProvider,
} from '../app/authAccessor';
import { prepareHeaders } from '../app/baseApi';
import { useCurrentUser } from '../features/me/useCurrentUser';

// The eager route gating reads useCurrentUser (an RTK Query hook). Mock it so the
// remote-module tests isolate router/accessor wiring from the store (the real
// store-backed path is covered by StandaloneShell.test + meApi/useCurrentUser tests).
vi.mock('../features/me/useCurrentUser');

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
    expect(await screen.findByText(/coming in 9\.7/i)).toBeInTheDocument();
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
    expect(screen.queryByText(/coming in 9\.7/i)).toBeNull();
  });
});
