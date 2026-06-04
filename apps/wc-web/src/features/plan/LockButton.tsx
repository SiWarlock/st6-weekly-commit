import { useState } from 'react';
import { useLockPlanMutation } from './plansApi';
import { can } from '../../shared/lib/allowedActions';
import type { WeeklyPlanDto } from '../../shared/lib/dtos';
import type { ParsedProblem } from '../../shared/lib/problemDetails';

/**
 * Lock affordance (rule #1, E8). Rendered **only** when `LOCK ∈ plan.allowedActions`
 * — the frontend never re-derives lock eligibility (an unlinked/empty plan is
 * rejected by the SERVER), and never renders the button DISABLED when lock is
 * unavailable: the affordance is GATED OUT entirely (§11 server-authoritative;
 * mirrors the START_RECONCILIATION gating in `PlanLifecycleBar`). This matches the
 * canon — there is no lock control once a plan is LOCKED/RECONCILING/RECONCILED.
 * On click it invokes E8 (invalidate→refetch into LOCKED, no optimistic flip). A
 * blocked lock renders the server `safeMessage` + `fieldErrors[]` verbatim
 * (`409 UNLINKED_PLANNED_COMMITMENT`/`EMPTY_PLAN_LOCK`).
 */
export function LockButton({ plan }: { plan: WeeklyPlanDto }) {
  const [lockPlan, { isLoading }] = useLockPlanMutation();
  const [problem, setProblem] = useState<ParsedProblem | null>(null);

  // Gate OUT (not disable) when the server doesn't allow locking — never a
  // dangling disabled control in a post-DRAFT plan (§11; canon parity).
  if (!can('LOCK', plan.allowedActions)) {
    return null;
  }

  async function handleLock() {
    setProblem(null);
    try {
      await lockPlan(plan.id).unwrap();
    } catch (err) {
      setProblem(err as ParsedProblem);
    }
  }

  return (
    <div className="flex flex-col items-end gap-2">
      <button
        type="button"
        disabled={isLoading}
        onClick={handleLock}
        className="rounded-md bg-brand-600 px-4 py-2 text-label font-semibold text-white hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-ring disabled:cursor-not-allowed disabled:opacity-50"
      >
        Lock week
      </button>
      {problem ? (
        <div
          data-cy="lock-error"
          role="alert"
          className="rounded-md border border-tone-failure-border bg-tone-failure-bg p-3 text-tone-failure-fg"
        >
          <p data-cy="lock-error-message">{problem.safeMessage}</p>
          {problem.fieldErrors.length > 0 ? (
            <ul className="mt-1 list-disc pl-5 text-meta">
              {problem.fieldErrors.map((fe) => (
                <li key={fe.field}>{fe.message}</li>
              ))}
            </ul>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
