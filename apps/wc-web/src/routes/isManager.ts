import { useCurrentUser } from '../features/me/useCurrentUser';

/**
 * Whether the current identity is a manager — gates the `/manager/*` routes
 * (REQ-UX-005). Reads the real `MeDto.isManager` via `useCurrentUser` and is
 * **fail-closed**: returns `false` while the `getMe` query is pending or errored,
 * so a manager entry point never flashes to an unconfirmed/IC actor.
 *
 * (9.4 used a default-false context placeholder; 9.5 wires the real role.)
 */
export function useIsManager(): boolean {
  const { isManager, isLoading, isError } = useCurrentUser();
  if (isLoading || isError) {
    return false;
  }
  return isManager;
}
