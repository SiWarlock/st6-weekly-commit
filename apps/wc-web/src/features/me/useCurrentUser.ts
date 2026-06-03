import { useGetMeQuery } from './meApi';

export interface CurrentUser {
  // Explicit `| undefined` (not optional `?:`) so the always-present hook result
  // can carry an undefined value under `exactOptionalPropertyTypes`.
  role: 'IC' | 'MANAGER' | undefined;
  /** Defaults to false until the identity is confirmed (fail-closed gating). */
  isManager: boolean;
  persona: string | undefined;
  displayName: string | undefined;
  isLoading: boolean;
  isError: boolean;
}

/**
 * Surfaces the current identity for route gating + the persona-aware default
 * route. `isManager` defaults to **false** until `getMe` resolves, so a manager
 * surface is never flashed to an unconfirmed/errored actor (REQ-UX-005). The
 * eager gating query is why the remote requires a host-provided Redux store
 * (9.13 host-integration contract).
 */
export function useCurrentUser(): CurrentUser {
  const { data, isLoading, isError } = useGetMeQuery();
  return {
    role: data?.role,
    isManager: data?.isManager ?? false,
    persona: data?.persona,
    displayName: data?.displayName,
    isLoading,
    isError,
  };
}
