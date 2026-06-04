import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach, afterEach, vi, type Mock } from 'vitest';
import { type ReactNode } from 'react';

vi.mock('@auth0/auth0-react', () => ({
  Auth0Provider: ({ children }: { children?: ReactNode }) => children,
  useAuth0: vi.fn(),
}));

import { useAuth0 } from '@auth0/auth0-react';
import { LoginScreen } from './LoginScreen';

const mockUseAuth0 = useAuth0 as unknown as Mock;
const loginWithRedirect = vi.fn();

beforeEach(() => {
  loginWithRedirect.mockReset();
  mockUseAuth0.mockReturnValue({
    isLoading: false,
    isAuthenticated: false,
    loginWithRedirect,
  });
});

afterEach(() => vi.restoreAllMocks());

describe('LoginScreen (the unauthenticated gate)', () => {
  it('loginScreen_logInTriggersRedirect: shows a branded Log in button that calls loginWithRedirect', async () => {
    const user = userEvent.setup();
    render(<LoginScreen />);

    const button = screen.getByRole('button', { name: /log in/i });
    expect(button).toBeInTheDocument();

    await user.click(button);
    expect(loginWithRedirect).toHaveBeenCalledTimes(1);
  });
});
