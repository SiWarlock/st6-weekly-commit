import type { AllowedAction } from './dtos';

/**
 * The single gating predicate for every action-driven control (rows, lock,
 * lifecycle). The server's `allowedActions[]` is the ONLY source of truth — the
 * frontend NEVER re-derives lock eligibility / authorization / lifecycle legality
 * client-side (§5/§6 safety posture; `allowedActions[]` is a UI affordance only).
 * A control renders/enables iff `can(action, commitmentOrPlan.allowedActions)`.
 */
export function can(
  action: AllowedAction,
  allowedActions: readonly AllowedAction[],
): boolean {
  return allowedActions.includes(action);
}
