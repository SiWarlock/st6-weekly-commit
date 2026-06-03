import { StatusBadge } from '../../shared/components/StatusBadge';
import { LockButton } from './LockButton';
import type { WeeklyPlanDto } from '../../shared/lib/dtos';

/**
 * The plan lifecycle bar — shows the current `PlanState` (via the §4.2
 * StatusBadge) and the lifecycle affordances driven by `plan.allowedActions[]`.
 * In 9.7 that is the lock affordance (`LockButton`); 9.8 adds start/close-
 * reconciliation here. The Cadence ST.5 4-node forward-only stepper visual is an
 * ST.7 design-review concern (this is the deterministic behavior scaffold).
 */
export function PlanLifecycleBar({ plan }: { plan: WeeklyPlanDto }) {
  return (
    <div
      data-cy="plan-lifecycle-bar"
      className="mb-4 flex items-center justify-between gap-3 rounded-lg border border-border bg-surface-raised px-4 py-3"
    >
      <StatusBadge kind="plan" value={plan.state} />
      <LockButton plan={plan} />
    </div>
  );
}
