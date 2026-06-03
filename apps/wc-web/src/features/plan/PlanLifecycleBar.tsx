import { useState } from 'react';
import { StatusBadge } from '../../shared/components/StatusBadge';
import { LockButton } from './LockButton';
import {
  useStartReconciliationMutation,
  useCloseReconciliationMutation,
} from './plansApi';
import { can } from '../../shared/lib/allowedActions';
import type { WeeklyPlanDto } from '../../shared/lib/dtos';
import type { ParsedProblem } from '../../shared/lib/problemDetails';

/**
 * The plan lifecycle bar — shows the current `PlanState` (via the §4.2 StatusBadge)
 * and the lifecycle affordances driven ONLY by `plan.allowedActions[]` (F.4 map):
 * LOCK (E8, `LockButton`), START_RECONCILIATION (E9), ADD_UNPLANNED (E11, delegated
 * to `onAddUnplanned`), CLOSE_RECONCILIATION (E10). START/CLOSE are no-body POSTs →
 * mutation→invalidate→refetch into the new state (**no optimistic flip**); a blocked
 * close (`422 UNPLANNED_MISSING_LINK_AT_CLOSE`) renders the server `safeMessage`
 * verbatim. The Cadence ST.5 forward-only stepper visual is an ST.7 concern.
 */
export function PlanLifecycleBar({
  plan,
  onAddUnplanned,
}: {
  plan: WeeklyPlanDto;
  onAddUnplanned?: () => void;
}) {
  const [startReconciliation, { isLoading: starting }] =
    useStartReconciliationMutation();
  const [closeReconciliation, { isLoading: closing }] =
    useCloseReconciliationMutation();
  const [problem, setProblem] = useState<ParsedProblem | null>(null);

  const canStart = can('START_RECONCILIATION', plan.allowedActions);
  const canClose = can('CLOSE_RECONCILIATION', plan.allowedActions);
  const canAddUnplanned = can('ADD_UNPLANNED', plan.allowedActions);

  async function handleStart() {
    setProblem(null);
    try {
      await startReconciliation(plan.id).unwrap();
    } catch (err) {
      setProblem(err as ParsedProblem);
    }
  }

  async function handleClose() {
    setProblem(null);
    try {
      await closeReconciliation(plan.id).unwrap();
    } catch (err) {
      setProblem(err as ParsedProblem);
    }
  }

  return (
    <div
      data-cy="plan-lifecycle-bar"
      className="mb-4 flex flex-col gap-2 rounded-lg border border-border bg-surface-raised px-4 py-3"
    >
      <div className="flex items-center justify-between gap-3">
        <StatusBadge kind="plan" value={plan.state} />
        <div className="flex flex-wrap items-center justify-end gap-2">
          <LockButton plan={plan} />
          {canStart ? (
            <button
              type="button"
              disabled={starting}
              onClick={handleStart}
              className="rounded-md bg-brand-600 px-4 py-2 text-label font-semibold text-white hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-ring disabled:cursor-not-allowed disabled:opacity-50"
            >
              Start reconciliation
            </button>
          ) : null}
          {canAddUnplanned && onAddUnplanned ? (
            <button
              type="button"
              onClick={onAddUnplanned}
              className="rounded-md border border-border-strong bg-surface px-4 py-2 text-label font-semibold text-ink-primary hover:bg-surface-hover focus:outline-none focus:ring-2 focus:ring-brand-ring"
            >
              Add unplanned
            </button>
          ) : null}
          {canClose ? (
            <button
              type="button"
              disabled={closing}
              onClick={handleClose}
              className="rounded-md bg-brand-600 px-4 py-2 text-label font-semibold text-white hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-ring disabled:cursor-not-allowed disabled:opacity-50"
            >
              Close reconciliation
            </button>
          ) : null}
        </div>
      </div>
      {problem ? (
        <div
          data-cy="lifecycle-error"
          role="alert"
          className="rounded-md border border-tone-failure-border bg-tone-failure-bg p-3 text-tone-failure-fg"
        >
          <p data-cy="lifecycle-error-message">{problem.safeMessage}</p>
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
