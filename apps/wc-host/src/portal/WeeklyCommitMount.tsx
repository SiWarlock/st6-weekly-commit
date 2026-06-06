import { lazy, Suspense } from "react";
import type { AccessTokenProvider } from "wc_web/WeeklyCommitApp";

// The exposed remote component — code-split so the host shell paints before the
// remoteEntry chunk loads. The remote is SELF-CONTAINED for state (it provides its
// own Redux store internally), so the host does NOT import a store across the
// federation boundary. A previous `import('wc_web/store')` whose chunk did a
// top-level `await importShared('@reduxjs/toolkit')` deadlocked the cross-build
// shared-scope init and hung the mount forever on "Connecting…".
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
 * Mounts the federated Weekly Commit remote inside the host portal. The host
 * provides exactly two things (§7 host contract):
 *  1. The router — the host's <BrowserRouter> higher in the tree (App.tsx), shared
 *     so the remote's <Routes> read the same Router context.
 *  2. `getAccessToken` — registered by the remote into its auth seam so
 *     `prepareHeaders` sends `Authorization: Bearer <jwt>` (auth0 mode).
 * The remote owns its own Redux store (no host-provided store).
 */
export function WeeklyCommitMount({
  getAccessToken,
}: {
  getAccessToken: AccessTokenProvider;
}) {
  return (
    <Suspense fallback={<MountFallback label="Loading Weekly Commit…" />}>
      <WeeklyCommitApp getAccessToken={getAccessToken} />
    </Suspense>
  );
}
