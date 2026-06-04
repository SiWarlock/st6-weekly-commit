import { Fragment, useState } from 'react';
import { HiCheck } from 'react-icons/hi';
import { LockButton } from './LockButton';
import {
  useStartReconciliationMutation,
  useCloseReconciliationMutation,
} from './plansApi';
import { can } from '../../shared/lib/allowedActions';
import { StatusBadge } from '../../shared/components/StatusBadge';
import { Avatar } from '../../shared/components/Avatar';
import { WeekRangeLabel } from '../../shared/components/WeekRangeLabel';
import type { PlanState, WeeklyPlanDto } from '../../shared/lib/dtos';
import type { ParsedProblem } from '../../shared/lib/problemDetails';

/**
 * The §3 plan lifecycle in forward-only order. The stepper is DERIVED display
 * off `plan.state` — nodes before the current state are `done`, the current is
 * `active`, after is `pending`. Non-interactive (a roadmap, not a control).
 */
type StepStatus = 'done' | 'active' | 'pending';
const LIFECYCLE_ORDER: PlanState[] = [
  'DRAFT',
  'LOCKED',
  'RECONCILING',
  'RECONCILED',
];
const STATE_LABEL: Record<PlanState, string> = {
  DRAFT: 'Draft',
  LOCKED: 'Locked',
  RECONCILING: 'Reconciling',
  RECONCILED: 'Reconciled',
};

function stepperNodes(
  state: PlanState,
): { state: PlanState; label: string; status: StepStatus }[] {
  const current = LIFECYCLE_ORDER.indexOf(state);
  return LIFECYCLE_ORDER.map((s, i) => ({
    state: s,
    label: STATE_LABEL[s],
    status: i < current ? 'done' : i === current ? 'active' : 'pending',
  }));
}

const DOT_CLASS: Record<StepStatus, string> = {
  done: 'bg-brand-600 border-brand-600',
  active: 'border-brand-400 bg-surface ring-2 ring-brand-soft',
  pending: 'border-border-strong bg-surface',
};
const LABEL_CLASS: Record<StepStatus, string> = {
  done: 'text-ink-primary font-medium',
  active: 'text-ink-primary font-medium',
  pending: 'text-ink-muted',
};

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
      className="mb-4 flex flex-col gap-3 rounded-lg border border-border bg-surface-raised px-4 py-3"
    >
      {/* Top row (ST.8c plan-header card): week label + plan status pill +
          planned/unplanned counts + manager-review badge (when present) + owner. */}
      <div className="flex flex-wrap items-center gap-3">
        <span className="text-meta text-ink-secondary">
          Week of <WeekRangeLabel weekStart={plan.weekStartDate} />
        </span>
        <StatusBadge kind="plan" value={plan.state} />
        <span className="text-meta text-ink-secondary">
          {`${plan.plannedCount} planned · ${plan.unplannedCount} unplanned`}
        </span>
        {plan.managerReview ? (
          <StatusBadge
            kind="review"
            value={plan.managerReview.status}
            derivedOverdue={plan.managerReview.isOverdue}
          />
        ) : null}
        <span className="ml-auto inline-flex items-center gap-2">
          <Avatar name={plan.employeeDisplayName} />
          <span className="text-label text-ink-primary">
            {plan.employeeDisplayName}
          </span>
        </span>
      </div>

      {/* Bottom row: the forward-only lifecycle stepper (ST.5a — derived display
          off plan.state, non-interactive; done nodes carry a check glyph) + the
          server-gated primary actions (driven ONLY by plan.allowedActions[]). */}
      <div className="flex flex-wrap items-center gap-3">
        <div
          data-cy="lifecycle-stepper"
          role="group"
          aria-label="Plan lifecycle progress"
          className="flex max-w-md items-center gap-1"
        >
          {stepperNodes(plan.state).map((n, i, arr) => (
            <Fragment key={n.state}>
              <div
                data-cy="stepper-node"
                data-state={n.state}
                data-status={n.status}
                className="flex flex-col items-center gap-1"
              >
                <span
                  aria-hidden
                  className={`flex h-4 w-4 items-center justify-center rounded-full border-2 ${DOT_CLASS[n.status]}`}
                >
                  {n.status === 'done' ? (
                    <HiCheck aria-hidden className="h-2.5 w-2.5 text-white" />
                  ) : null}
                </span>
                <span className={`text-meta ${LABEL_CLASS[n.status]}`}>
                  {n.label}
                </span>
              </div>
              {i < arr.length - 1 ? (
                <span
                  aria-hidden
                  data-cy="stepper-bar"
                  className={`h-0.5 flex-1 ${n.status === 'done' ? 'bg-brand-600' : 'bg-border-strong'}`}
                />
              ) : null}
            </Fragment>
          ))}
        </div>

        {/* "Add unplanned work" sits LEFT of the gold/success primary (canon). */}
        <div className="ml-auto flex flex-wrap items-center justify-end gap-2">
          <LockButton plan={plan} />
          {canAddUnplanned && onAddUnplanned ? (
            <button
              type="button"
              onClick={onAddUnplanned}
              className="rounded-md border border-border-strong bg-surface px-4 py-2 text-label font-semibold text-ink-primary hover:bg-surface-hover focus:outline-none focus:ring-2 focus:ring-brand-ring"
            >
              Add unplanned
            </button>
          ) : null}
          {/* GOLD Start-reconciliation — the warning tone token (canon
              variant="warning"), NOT brand blue. Still gated on the server's
              START_RECONCILIATION (LESSONS §11 — no client re-derivation). */}
          {canStart ? (
            <button
              type="button"
              disabled={starting}
              onClick={handleStart}
              className="rounded-md bg-tone-warning-solid px-4 py-2 text-label font-semibold text-white hover:opacity-90 focus:outline-none focus:ring-2 focus:ring-brand-ring disabled:cursor-not-allowed disabled:opacity-50"
            >
              Start reconciliation
            </button>
          ) : null}
          {/* Close week — the success tone token (canon variant="success"). Gated. */}
          {canClose ? (
            <button
              type="button"
              disabled={closing}
              onClick={handleClose}
              className="rounded-md bg-tone-success-solid px-4 py-2 text-label font-semibold text-white hover:opacity-90 focus:outline-none focus:ring-2 focus:ring-brand-ring disabled:cursor-not-allowed disabled:opacity-50"
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
