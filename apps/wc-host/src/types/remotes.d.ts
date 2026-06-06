/**
 * Type declarations for the federated `wc_web` remote modules. These are resolved
 * at runtime from `remoteEntry.js` (VITE_WC_REMOTE_URL); the declarations give the
 * host strict types at the boundary.
 *
 * Keep in lockstep with the remote's exposes (apps/wc-web/vite.config.ts):
 *   './WeeklyCommitApp' → the exposed React component
 *   './store'           → the configured Redux store (baseApi reducer + middleware)
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

declare module "wc_web/store" {
  import type { Store } from "@reduxjs/toolkit";

  /**
   * The remote's configured store — the SAME instance the remote's components
   * dispatch against (its hooks bind to the remote container's `baseApi`
   * singleton). The host wraps the remote in `<Provider store={store}>`.
   */
  export const store: Store;
}
