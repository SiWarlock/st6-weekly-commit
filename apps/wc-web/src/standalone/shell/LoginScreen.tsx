import { Button } from 'flowbite-react';
import { useAuth0 } from '@auth0/auth0-react';

/**
 * 9.17 — the branded unauthenticated login screen (standalone-only). A "Log in"
 * button hands off to the Auth0 universal login (`loginWithRedirect`). Demos
 * better than an instant universal-login bounce (brief Q1). Token-native — no hex.
 */
export function LoginScreen() {
  const { loginWithRedirect } = useAuth0();

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 bg-surface-app px-6 text-center">
      <div className="flex items-center gap-2">
        <span className="flex h-8 w-8 items-center justify-center rounded-md bg-brand-500 text-white">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth={2.4}
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden
            className="h-5 w-5"
          >
            <path d="M5 13l4 4L19 7" />
          </svg>
        </span>
        <span className="text-h3 font-semibold text-ink-primary">
          ST6 Weekly Commit
        </span>
      </div>
      <p className="max-w-sm text-body text-ink-muted">
        Sign in to plan your week and keep every commitment aligned to strategy.
      </p>
      <Button color="primary" onClick={() => void loginWithRedirect()}>
        Log in
      </Button>
    </div>
  );
}
