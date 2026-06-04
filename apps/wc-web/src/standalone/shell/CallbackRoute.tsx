import { LoadingState } from '../../shared/components/LoadingState';

/**
 * 9.17 — the `/callback` redirect landing (standalone-only). Rendered OUTSIDE the
 * login gate so the Auth0 SDK can exchange the authorization code here without
 * the gate's loading/login branch shadowing it; once the exchange completes,
 * `Auth0IdentityProvider`'s `onRedirectCallback` navigates to `returnTo ?? '/'`.
 * Token-native processing state — no hex.
 */
export function CallbackRoute() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-surface-app px-6 text-center">
      <p className="text-body text-ink-secondary">Signing you in…</p>
      <LoadingState delayMs={0} variant="cards" />
    </div>
  );
}
