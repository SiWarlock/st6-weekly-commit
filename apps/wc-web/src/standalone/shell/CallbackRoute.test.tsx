import { render, screen } from '@testing-library/react';
import { describe, it, expect, afterEach, vi, type Mock } from 'vitest';
import { type ReactNode } from 'react';

vi.mock('@auth0/auth0-react', () => ({
  Auth0Provider: ({ children }: { children?: ReactNode }) => children,
  useAuth0: vi.fn(),
}));

import { useAuth0 } from '@auth0/auth0-react';
import { CallbackRoute } from './CallbackRoute';

const mockUseAuth0 = useAuth0 as unknown as Mock;

afterEach(() => vi.restoreAllMocks());

describe('CallbackRoute (/callback redirect processing state)', () => {
  it('callbackRoute_rendersProcessingState: shows a processing state while the SDK exchanges the code (no app, no login screen)', () => {
    mockUseAuth0.mockReturnValue({ isLoading: true, isAuthenticated: false });
    render(<CallbackRoute />);
    // A status/processing affordance is shown (the code-exchange is in flight);
    // onRedirectCallback navigates away to returnTo once it completes.
    expect(screen.getByText(/signing you in/i)).toBeInTheDocument();
  });
});
