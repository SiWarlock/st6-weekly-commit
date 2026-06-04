import { render } from '@testing-library/react';
import {
  describe,
  it,
  expect,
  beforeEach,
  afterEach,
  vi,
  type Mock,
} from 'vitest';
import { type ReactElement, type ReactNode } from 'react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router-dom';

// Mock the SDK so our seam wiring + §16 reset is deterministically testable; the
// real redirect + code-exchange is SDK-owned (runbook-verified, not unit-tested).
// Auth0Provider is a passthrough; the login GATE lives in Auth0LoginGate (so the
// /callback code-exchange can run inside the provider but outside the gate).
vi.mock('@auth0/auth0-react', () => ({
  Auth0Provider: ({ children }: { children?: ReactNode }) => children,
  useAuth0: vi.fn(),
}));

import { useAuth0 } from '@auth0/auth0-react';
import { Auth0IdentityProvider } from './Auth0IdentityProvider';
import { store } from '../app/store';
import { baseApi } from '../app/baseApi';
import {
  getAccessToken,
  hasAccessTokenProvider,
  setAccessTokenProvider,
} from '../app/authAccessor';

const mockUseAuth0 = useAuth0 as unknown as Mock;

interface Auth0State {
  isLoading: boolean;
  isAuthenticated: boolean;
  user?: { sub: string; name?: string; email?: string };
  getAccessTokenSilently: Mock;
  loginWithRedirect: Mock;
  logout: Mock;
  error?: Error;
}

function auth0State(over: Partial<Auth0State> = {}): Auth0State {
  return {
    isLoading: false,
    isAuthenticated: true,
    user: { sub: 'auth0|1', name: 'Dana Okafor', email: 'dana@st6demo.com' },
    getAccessTokenSilently: vi.fn().mockResolvedValue('jwt-123'),
    loginWithRedirect: vi.fn(),
    logout: vi.fn(),
    ...over,
  };
}

beforeEach(() => {
  vi.stubEnv('VITE_AUTH0_DOMAIN', 'tenant.us.auth0.com');
  vi.stubEnv('VITE_AUTH0_CLIENT_ID', 'abc123');
  vi.stubEnv('VITE_AUTH0_AUDIENCE', 'https://api.wc.example.com');
  mockUseAuth0.mockReturnValue(auth0State());
});

afterEach(() => {
  setAccessTokenProvider(null);
  vi.unstubAllEnvs();
  vi.restoreAllMocks();
});

function renderProvider(ui: ReactElement) {
  return render(
    <Provider store={store}>
      <MemoryRouter>{ui}</MemoryRouter>
    </Provider>,
  );
}

describe('Auth0IdentityProvider (standalone-only OAuth identity: seam + reset)', () => {
  it('auth0Provider_wiresAccessTokenSeam: mount feeds getAccessTokenSilently into the 9.1 seam; unmount clears it', async () => {
    mockUseAuth0.mockReturnValue(
      auth0State({
        getAccessTokenSilently: vi.fn().mockResolvedValue('jwt-from-sdk'),
      }),
    );
    const { unmount } = renderProvider(
      <Auth0IdentityProvider>
        <div data-testid="app" />
      </Auth0IdentityProvider>,
    );
    // The seam now resolves to the SDK's token → prepareHeaders' auth0 branch
    // will send `Authorization: Bearer jwt-from-sdk` (the producer side).
    expect(hasAccessTokenProvider()).toBe(true);
    await expect(getAccessToken()).resolves.toBe('jwt-from-sdk');

    unmount();
    expect(hasAccessTokenProvider()).toBe(false);
  });

  it('resetApiState_onIdentityChange: a changed user.sub dispatches resetApiState; the first mount does NOT (§16, keyed on sub)', () => {
    const resetType = baseApi.util.resetApiState().type;
    const dispatchSpy = vi.spyOn(store, 'dispatch');
    const didReset = () =>
      dispatchSpy.mock.calls.some(
        ([a]) => (a as { type?: string }).type === resetType,
      );

    mockUseAuth0.mockReturnValue(auth0State({ user: { sub: 'auth0|1' } }));
    const { rerender } = renderProvider(
      <Auth0IdentityProvider>
        <div data-testid="app" />
      </Auth0IdentityProvider>,
    );
    // First mount must NOT wipe the cache.
    expect(didReset()).toBe(false);

    // A new authenticated identity (different sub) → reset so identity-scoped
    // argless queries (/api/me, /api/plans/current) refetch for the new user.
    mockUseAuth0.mockReturnValue(auth0State({ user: { sub: 'auth0|2' } }));
    rerender(
      <Provider store={store}>
        <MemoryRouter>
          <Auth0IdentityProvider>
            <div data-testid="app" />
          </Auth0IdentityProvider>
        </MemoryRouter>
      </Provider>,
    );
    expect(didReset()).toBe(true);
  });
});
