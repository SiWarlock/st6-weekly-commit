/**
 * Type declarations for the federated `wc_web` remote modules. These are resolved
 * at runtime from `remoteEntry.js` (VITE_WC_REMOTE_URL); the declarations give the
 * host strict types at the boundary.
 *
 * Keep in lockstep with the remote's exposes (apps/wc-web/vite.config.ts):
 *   './WeeklyCommitApp' → the exposed React component (self-provides its store)
 * (No `./store` expose — the remote owns its Redux store internally.)
 */
declare module "wc_web/WeeklyCommitApp" {
  import type { ComponentType } from "react";

  /** Host-provided auth accessor — required in `auth0` mode (REQ-I-008). */
  export type AccessTokenProvider = () => Promise<string>;

  export interface WeeklyCommitAppProps {
    getAccessToken?: AccessTokenProvider;
  }

  const WeeklyCommitApp: ComponentType<WeeklyCommitAppProps>;
  export default WeeklyCommitApp;
}
