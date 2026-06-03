import { createContext, useContext } from 'react';

/**
 * Manager-gating seam (REQ-UX-005). Drives which routes the route tree registers
 * so an IC has no manager entry point in the UI. Default `false` (IC).
 *
 * 9.4 PLACEHOLDER: the value is provided via context (fixture-driven in tests).
 * 9.5 swaps `useIsManager` to read `MeDto.isManager` (B.3) from `meApi`; the
 * route tree consumes this hook either way, so the swap is internal.
 */
export const IsManagerContext = createContext<boolean>(false);

/** Whether the current identity is a manager (gates the /manager/* routes). */
export function useIsManager(): boolean {
  return useContext(IsManagerContext);
}
