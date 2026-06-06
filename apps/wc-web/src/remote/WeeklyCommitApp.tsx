import { useEffect } from 'react';
import { Provider } from 'react-redux';
import {
  hasAccessTokenProvider,
  setAccessTokenProvider,
  type AccessTokenProvider,
} from '../app/authAccessor';
import { store } from '../app/store';
import { AppRoutes } from '../routes/AppRoutes';

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
 * router (renders the lazy `<AppRoutes/>` inside it — never creates a
 * `BrowserRouter`) and a host auth accessor (registers it into the 9.1 seam), and
 * SELF-PROVIDES its own Redux `<Provider store={store}>` (the store + baseApi are
 * remote-internal; the host doesn't supply them). Self-providing — rather than a
 * separate `import('wc_web/store')` — is deliberate: a `./store` expose whose
 * chunk did a top-level `await importShared('@reduxjs/toolkit')` deadlocked the
 * cross-build shared-scope init and hung the host on "Connecting…". In standalone
 * the shell also provides the same store instance (nested Provider, same store —
 * harmless). It owns NO chrome (no PersonaSwitcher / ThemeToggle) and contains NO
 * demo/persona code path — those are standalone-only and tree-shaken out of this
 * entry (REQ-I-008).
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

  return (
    <Provider store={store}>
      <AppRoutes />
    </Provider>
  );
}
