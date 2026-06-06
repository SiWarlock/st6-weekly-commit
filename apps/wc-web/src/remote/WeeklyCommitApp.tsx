import { useEffect, useRef, useState } from 'react';
import { Provider } from 'react-redux';
import {
  hasAccessTokenProvider,
  setAccessTokenProvider,
  type AccessTokenProvider,
} from '../app/authAccessor';
import { store } from '../app/store';
import { AppRoutes } from '../routes/AppRoutes';
// The remote's compiled design-token + Tailwind/Flowbite stylesheet (Lesson #3),
// imported as a base-resolved URL so the federated host can inject it (otherwise
// the embedded remote renders unstyled — standalone gets it via `main.tsx`, which
// the host never loads). We do NOT use a plain side-effect `import './theme.css'`:
// @originjs's auto CSS-injection is unusable with our absolute `--base` — it builds
// the href as base + bare-filename and DROPS the `assets/` dir → a 404. Vite's
// `?url` resolves the PROCESSED css to the correct base+assetsDir URL
// (…/remote/assets/theme-<hash>.css), which we inject ourselves on mount.
// theme.css carries no demo/persona code (REQ-I-008-safe).
import themeHref from '../styles/theme.css?url';

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
 * `BrowserRouter`) and a host auth accessor (registers it SYNCHRONOUSLY into the
 * 9.1 seam during render, before the child first-query — §29), and
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
  // Keep the registered closure pointing at the CURRENT prop without re-registering.
  const tokenRef = useRef(getAccessToken);
  tokenRef.current = getAccessToken;

  // Register the host accessor SYNCHRONOUSLY during the first render (lazy useState
  // initializer) — NOT a mount useEffect. The child <AppRoutes/> (useCurrentUser →
  // meApi) dispatches its first RTK Query in the same commit, and child effects run
  // BEFORE parent effects, so an effect here registers too late: prepareHeaders →
  // getAccessToken() fires first and throws "No access-token provider configured"
  // (the live host bug; LESSONS §29). Standalone wires its own accessor via
  // Auth0IdentityProvider and passes no prop, so we register only when the host
  // actually provides one.
  const [registered] = useState(() => {
    if (!getAccessToken) {
      return false;
    }
    setAccessTokenProvider(() => {
      const fn = tokenRef.current;
      if (!fn) {
        throw new Error('No access-token provider configured');
      }
      return fn();
    });
    return true;
  });

  // Clear the seam on unmount — only if this component registered it (host mode);
  // in standalone the seam is owned by Auth0IdentityProvider/DemoIdentityProvider.
  useEffect(() => {
    if (!registered) {
      return undefined;
    }
    return () => setAccessTokenProvider(null);
  }, [registered]);

  // Inject the remote's stylesheet via its correct base-resolved URL (see the
  // themeHref import note). Idempotent — only one link, harmless if standalone
  // already loaded the same CSS via main.tsx.
  useEffect(() => {
    if (document.querySelector('link[data-wc-remote-theme]')) {
      return;
    }
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = themeHref;
    link.dataset.wcRemoteTheme = '';
    document.head.appendChild(link);
  }, []);

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
