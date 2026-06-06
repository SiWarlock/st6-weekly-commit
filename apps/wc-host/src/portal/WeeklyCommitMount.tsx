import { lazy, Suspense, useEffect, useState } from "react";
import { Provider } from "react-redux";
import type { Store } from "@reduxjs/toolkit";
import type { AccessTokenProvider } from "wc_web/WeeklyCommitApp";

// The exposed remote component — code-split so the host shell paints before the
// remoteEntry chunk loads.
const WeeklyCommitApp = lazy(() => import("wc_web/WeeklyCommitApp"));

function MountFallback({ label }: { label: string }) {
  return (
    <div className="wc-mount-status" role="status" aria-live="polite">
      <span className="wc-spinner" aria-hidden="true" />
      {label}
    </div>
  );
}

/**
 * Mounts the federated Weekly Commit remote inside the host portal.
 *
 * Two host-provided dependencies the remote requires before render (§7 host
 * contract):
 *  1. A Redux <Provider> — the SAME store instance the remote dispatches against.
 *     We import it from the remote container (`wc_web/store`) so it carries the
 *     remote's `baseApi` singleton (a host-built store would be a second RTK Query
 *     instance → queries never resolve). It loads async, so we gate the mount on it.
 *  2. The router context — provided by the host's <BrowserRouter> higher in the
 *     tree (App.tsx), shared so the remote's <Routes> read the same context.
 *
 * `getAccessToken` is registered by the remote into its auth seam so
 * `prepareHeaders` sends `Authorization: Bearer <jwt>` (auth0 mode).
 */
export function WeeklyCommitMount({
  getAccessToken,
}: {
  getAccessToken: AccessTokenProvider;
}) {
  const [store, setStore] = useState<Store | null>(null);
  const [storeError, setStoreError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    import("wc_web/store")
      .then((m) => {
        if (active) setStore(m.store);
      })
      .catch((err: unknown) => {
        if (active)
          setStoreError(
            err instanceof Error ? err.message : "Failed to load remote store",
          );
      });
    return () => {
      active = false;
    };
  }, []);

  if (storeError) {
    return (
      <div className="wc-mount-error" role="alert">
        <strong>Couldn’t load Weekly Commit.</strong>
        <p>The remote could not be reached: {storeError}</p>
        <p className="wc-mount-hint">
          Check that the remote is being served at{" "}
          <code>VITE_WC_REMOTE_URL</code>.
        </p>
      </div>
    );
  }

  if (!store) {
    return <MountFallback label="Connecting to Weekly Commit…" />;
  }

  return (
    <Provider store={store}>
      <Suspense fallback={<MountFallback label="Loading Weekly Commit…" />}>
        <WeeklyCommitApp getAccessToken={getAccessToken} />
      </Suspense>
    </Provider>
  );
}
