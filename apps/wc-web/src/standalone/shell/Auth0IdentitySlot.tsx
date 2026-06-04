import { Button } from 'flowbite-react';
import { HiOutlineUserCircle } from 'react-icons/hi';
import { useAuth0 } from '@auth0/auth0-react';

/**
 * 9.17 — the app-bar identity slot for auth0 mode (standalone-only): "signed in
 * as <name|email>" + a Log out button (`logout` back to the SPA origin). Injected
 * into `AppBar` in place of the demo `PersonaSwitcher` (retired for the deployed
 * real-OAuth demo). Token-native — no hex.
 */
export function Auth0IdentitySlot() {
  const { user, logout } = useAuth0();
  const label = user?.name ?? user?.email ?? 'Account';

  return (
    <div className="flex items-center gap-2">
      <span className="inline-flex items-center gap-1.5 text-label text-ink-secondary">
        <HiOutlineUserCircle aria-hidden className="h-5 w-5 text-ink-muted" />
        <span className="text-meta text-ink-muted">Signed in as</span>
        <span className="font-medium text-ink-primary">{label}</span>
      </span>
      <Button
        size="xs"
        color="secondary"
        onClick={() =>
          void logout({ logoutParams: { returnTo: window.location.origin } })
        }
      >
        Log out
      </Button>
    </div>
  );
}
