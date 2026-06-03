import { useState } from 'react';
import { useGetCurrentPlanQuery } from './plansApi';
import { LoadingState } from '../../shared/components/LoadingState';
import { ErrorState } from '../../shared/components/ErrorState';
import { EmptyState } from '../../shared/components/EmptyState';
import { StatusBadge } from '../../shared/components/StatusBadge';
import { CommitmentList } from '../commitment/CommitmentList';
import { CommitmentForm } from '../commitment/CommitmentForm';
import { PlanLifecycleBar } from './PlanLifecycleBar';

/**
 * The IC weekly-planning workspace. Renders `getCurrentPlan` (9.6) with the §7
 * view-states — loading / not-started shell (EmptyState) / error / success — and
 * the commitment list. The create form (commit 2) and lifecycle/lock bar
 * (commit 3) mount here. All control gating is server-driven (`allowedActions[]`).
 */
export function WeeklyPlanView() {
  const { data, isLoading, isError, error } = useGetCurrentPlanQuery();
  const [showForm, setShowForm] = useState(false);

  if (isError) {
    const message =
      (error as { safeMessage?: string } | undefined)?.safeMessage ??
      'Could not load your plan.';
    return <ErrorState message={message} />;
  }
  if (isLoading || !data) {
    return <LoadingState variant="cards" delayMs={0} />;
  }

  const plan = data;
  return (
    <section data-cy="weekly-plan-view" className="mx-auto max-w-3xl p-6">
      <header className="mb-4 flex items-center justify-between gap-3">
        <div>
          <h1 className="text-h2 font-semibold text-ink-primary">
            Weekly commitments
          </h1>
          <p className="text-meta text-ink-secondary">
            {plan.weekStartDate} – {plan.weekEndDate}
          </p>
        </div>
        <StatusBadge kind="plan" value={plan.state} />
      </header>

      <PlanLifecycleBar plan={plan} />

      {plan.commitments.length === 0 ? (
        <EmptyState
          title="No commitments yet"
          message="Add your first weekly commitment to start planning your week."
        />
      ) : (
        <CommitmentList commitments={plan.commitments} planState={plan.state} />
      )}

      {/* Adding commitments is a DRAFT-stage affordance (the server enforces the
          baseline-immutability rule post-lock via 409). The form is collapsed by
          default; it (and its RCDO fetch) only mounts when the IC opens it. */}
      {plan.state === 'DRAFT' ? (
        <div className="mt-4">
          {showForm ? (
            <CommitmentForm planId={plan.id} planState={plan.state} />
          ) : (
            <button
              type="button"
              onClick={() => setShowForm(true)}
              className="rounded-md border border-border-strong bg-surface-raised px-4 py-2 text-label font-semibold text-ink-primary hover:bg-surface-hover focus:outline-none focus:ring-2 focus:ring-brand-ring"
            >
              New commitment
            </button>
          )}
        </div>
      ) : null}
    </section>
  );
}
