import { useEffect } from 'react';
import {
  hasAccessTokenProvider,
  setAccessTokenProvider,
  type AccessTokenProvider,
} from '../app/authAccessor';
import App from '../App';

export interface WeeklyCommitAppProps {
  /**
   * Host-provided auth accessor (remote mode). The host passes its
   * `getAccessToken`; we register it into the shared 9.1 seam so `prepareHeaders`
   * uses the host's token. Standalone wires its own accessor via the seam.
   */
  getAccessToken?: AccessTokenProvider;
}

/**
 * The single exposed Module-Federation module. It CONSUMES a host-provided
 * router (renders inside it — never creates a `BrowserRouter`) and a host auth
 * accessor (registers it into the 9.1 seam). It owns NO chrome (no
 * PersonaSwitcher / ThemeToggle) and contains NO demo/persona code path — those
 * are standalone-only and tree-shaken out of this entry (REQ-I-008).
 */
export default function WeeklyCommitApp({
  getAccessToken,
}: WeeklyCommitAppProps) {
  useEffect(() => {
    if (getAccessToken) {
      setAccessTokenProvider(getAccessToken);
    }
  }, [getAccessToken]);

  // An accessor is only required in the hosted (auth0) path. If the host failed
  // to provide one (and none is already wired), surface an error, never crash.
  const needsAccessor = import.meta.env.VITE_AUTH_MODE === 'auth0';
  const ready =
    !needsAccessor || Boolean(getAccessToken) || hasAccessTokenProvider();

  if (!ready) {
    return (
      <div role="alert">
        Unable to load Weekly Commit: no authentication accessor was provided by
        the host.
      </div>
    );
  }

  return <App />;
}
