import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach, afterEach, vi, type Mock } from 'vitest';
import { type ReactNode } from 'react';

vi.mock('@auth0/auth0-react', () => ({
  Auth0Provider: ({ children }: { children?: ReactNode }) => children,
  useAuth0: vi.fn(),
}));

import { useAuth0 } from '@auth0/auth0-react';
import { Auth0IdentitySlot } from './Auth0IdentitySlot';

const mockUseAuth0 = useAuth0 as unknown as Mock;
const logout = vi.fn();

beforeEach(() => {
  logout.mockReset();
  mockUseAuth0.mockReturnValue({
    isAuthenticated: true,
    user: { sub: 'auth0|1', name: 'Dana Okafor', email: 'dana@st6demo.com' },
    logout,
  });
});

afterEach(() => vi.restoreAllMocks());

describe('Auth0IdentitySlot (app-bar "logged in as X" + logout)', () => {
  it('identitySlot_showsUserAndLogout: shows the signed-in identity and a Log out button calling logout with returnTo=origin', async () => {
    const user = userEvent.setup();
    render(<Auth0IdentitySlot />);

    // The signed-in identity is shown (name, falling back to email).
    expect(screen.getByText(/Dana Okafor/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /log out/i }));
    expect(logout).toHaveBeenCalledWith({
      logoutParams: { returnTo: window.location.origin },
    });
  });

  it('identitySlot_fallsBackToEmail: with no name, the email is shown as the identity', () => {
    mockUseAuth0.mockReturnValue({
      isAuthenticated: true,
      user: { sub: 'auth0|2', email: 'lena@st6demo.com' },
      logout,
    });
    render(<Auth0IdentitySlot />);
    expect(screen.getByText(/lena@st6demo.com/)).toBeInTheDocument();
  });
});
