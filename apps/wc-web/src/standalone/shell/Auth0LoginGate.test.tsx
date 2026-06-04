import { render, screen } from '@testing-library/react';
import {
  describe,
  it,
  expect,
  beforeEach,
  afterEach,
  vi,
  type Mock,
} from 'vitest';
import { type ReactNode } from 'react';

vi.mock('@auth0/auth0-react', () => ({
  Auth0Provider: ({ children }: { children?: ReactNode }) => children,
  useAuth0: vi.fn(),
}));

import { useAuth0 } from '@auth0/auth0-react';
import { Auth0LoginGate } from './Auth0LoginGate';

const mockUseAuth0 = useAuth0 as unknown as Mock;

function state(over: Record<string, unknown> = {}) {
  return {
    isLoading: false,
    isAuthenticated: false,
    loginWithRedirect: vi.fn(),
    ...over,
  };
}

beforeEach(() => mockUseAuth0.mockReturnValue(state()));
afterEach(() => vi.restoreAllMocks());

describe('Auth0LoginGate (loading → login screen → app)', () => {
  it('loginGate_loadingWhenIsLoading: isLoading shows neither the app nor the login screen', () => {
    mockUseAuth0.mockReturnValue(state({ isLoading: true }));
    render(
      <Auth0LoginGate>
        <div data-testid="app" />
      </Auth0LoginGate>,
    );
    expect(screen.queryByTestId('app')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /log in/i }),
    ).not.toBeInTheDocument();
  });

  it('loginGate_loginScreenWhenUnauthenticated: unauthenticated shows the login screen, not the app', () => {
    mockUseAuth0.mockReturnValue(
      state({ isLoading: false, isAuthenticated: false }),
    );
    render(
      <Auth0LoginGate>
        <div data-testid="app" />
      </Auth0LoginGate>,
    );
    expect(screen.getByRole('button', { name: /log in/i })).toBeInTheDocument();
    expect(screen.queryByTestId('app')).not.toBeInTheDocument();
  });

  it('loginGate_appWhenAuthenticated: authenticated renders the children (the app)', () => {
    mockUseAuth0.mockReturnValue(state({ isAuthenticated: true }));
    render(
      <Auth0LoginGate>
        <div data-testid="app" />
      </Auth0LoginGate>,
    );
    expect(screen.getByTestId('app')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /log in/i }),
    ).not.toBeInTheDocument();
  });
});
