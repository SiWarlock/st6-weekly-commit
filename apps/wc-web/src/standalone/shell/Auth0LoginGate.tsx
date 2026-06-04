import { type ReactNode } from 'react';
import { useAuth0 } from '@auth0/auth0-react';
import { LoadingState } from '../../shared/components/LoadingState';
import { LoginScreen } from './LoginScreen';

/**
 * 9.17 — the auth0 login gate (standalone-only). Applied only on the `/*` route
 * (NOT `/callback`, which must render outside the gate so the SDK code-exchange
 * isn't shadowed). While the SDK initializes → a loading state; unauthenticated →
 * the branded `LoginScreen`; authenticated → the app. Eligibility is read
 * straight from `useAuth0()` (server/SDK-authoritative) — no client-side authz
 * re-derivation (§11).
 */
export function Auth0LoginGate({ children }: { children: ReactNode }) {
  const { isLoading, isAuthenticated } = useAuth0();

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-surface-app p-8">
        <LoadingState delayMs={0} variant="cards" label="Loading…" />
      </div>
    );
  }
  if (!isAuthenticated) {
    return <LoginScreen />;
  }
  return <>{children}</>;
}
