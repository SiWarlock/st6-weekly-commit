import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, afterEach, vi } from 'vitest';
import WeeklyCommitApp from './WeeklyCommitApp';
import {
  getAccessToken,
  hasAccessTokenProvider,
  setAccessTokenProvider,
} from '../app/authAccessor';
import { prepareHeaders } from '../app/baseApi';

afterEach(() => {
  vi.unstubAllEnvs();
  setAccessTokenProvider(null);
});

describe('WeeklyCommitApp (exposed remote module)', () => {
  it('remote_consumes_host_router_no_own_browserrouter: renders the lazy route tree inside a host-supplied router', async () => {
    const { container } = render(
      <MemoryRouter>
        <WeeklyCommitApp getAccessToken={async () => 'host-jwt'} />
      </MemoryRouter>,
    );
    // The default route ('/') is now a lazy chunk (App) mounted via <AppRoutes/>;
    // it resolves behind Suspense without the remote creating its own router —
    // the host MemoryRouter is the only router in the tree.
    await waitFor(() =>
      expect(container.querySelector('[data-cy="token-probe"]')).not.toBeNull(),
    );
  });

  it('remote_registers_host_accessor_into_seam: a host getAccessToken is wired into the 9.1 seam (auth0 Bearer uses the host token)', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'auth0');
    render(
      <MemoryRouter>
        <WeeklyCommitApp getAccessToken={async () => 'host-jwt-123'} />
      </MemoryRouter>,
    );
    // The host accessor is registered into the shared seam on mount.
    expect(hasAccessTokenProvider()).toBe(true);
    expect(await getAccessToken()).toBe('host-jwt-123');
    // ...so prepareHeaders (auth0) attaches the HOST's Bearer (production path).
    const headers = await prepareHeaders(new Headers());
    expect(headers.get('Authorization')).toBe('Bearer host-jwt-123');
    expect(headers.get('X-Demo-Employee-Id')).toBeNull();
  });

  it('remote_without_accessor_surfaces_error: hosted (auth0) with no host accessor → error state, not a crash', () => {
    vi.stubEnv('VITE_AUTH_MODE', 'auth0'); // hosted path requires an accessor
    setAccessTokenProvider(null); // host failed to provide one, no seam set
    const { container } = render(
      <MemoryRouter>
        <WeeklyCommitApp />
      </MemoryRouter>,
    );
    expect(screen.getByRole('alert')).toBeInTheDocument();
    // WC content is NOT rendered when no accessor is available.
    expect(container.querySelector('[data-cy="token-probe"]')).toBeNull();
  });
});
