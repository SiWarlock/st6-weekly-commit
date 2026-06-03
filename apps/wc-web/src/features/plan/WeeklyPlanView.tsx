import { useState } from 'react';
import { useGetCurrentPlanQuery } from './plansApi';
import { LoadingState } from '../../shared/components/LoadingState';
import { ErrorState } from '../../shared/components/ErrorState';
import { EmptyState } from '../../shared/components/EmptyState';
import { StatusBadge } from '../../shared/components/StatusBadge';
import { CommitmentList } from '../commitment/CommitmentList';
import { CommitmentForm } from '../commitment/CommitmentForm';
import { PlanLifecycleBar } from './PlanLifecycleBar';
import { useGetSyncRecordsQuery } from '../sync/syncApi';
import { SyncStatusBadge } from '../sync/SyncStatusBadge';
import { SyncRetryAction } from '../sync/SyncRetryAction';
import { CommentThread } from '../comment/CommentThread';

/**
 * The IC weekly-planning workspace. Renders `getCurrentPlan` (9.6) with the §7
 * view-states — loading / not-started shell (EmptyState) / error / success — and
 * the commitment list. The create form (commit 2) and lifecycle/lock bar
 * (commit 3) mount here. All control gating is server-driven (`allowedActions[]`).
 */
export function WeeklyPlanView() {
  const { data, isLoading, isError, error } = useGetCurrentPlanQuery();
  // Sync surface (9.12) — read this plan's Outlook-sync records once the plan is
  // loaded. Renders independently below, so a FAILED sync never blocks the
  // lifecycle (rule #4). The hook is unconditional (skip-until-plan-loaded).
  const { data: syncRecords } = useGetSyncRecordsQuery(data?.id ?? '', {
    skip: !data?.id,
  });
  const [showForm, setShowForm] = useState(false);
  const [showUnplannedForm, setShowUnplannedForm] = useState(false);
  const [editingCommitmentId, setEditingCommitmentId] = useState<string | null>(
    null,
  );

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
  const editingCommitment =
    editingCommitmentId === null
      ? null
      : (plan.commitments.find((c) => c.id === editingCommitmentId) ?? null);
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

      <PlanLifecycleBar
        plan={plan}
        onAddUnplanned={() => setShowUnplannedForm(true)}
      />

      {plan.commitments.length === 0 ? (
        <EmptyState
          title="No commitments yet"
          message="Add your first weekly commitment to start planning your week."
        />
      ) : (
        <CommitmentList
          commitments={plan.commitments}
          planState={plan.state}
          planId={plan.id}
          onEdit={(c) => setEditingCommitmentId(c.id)}
        />
      )}

      {/* Plan-level comment thread (9.11b) — COMMENT-gated + lazy; renders
          nothing unless the server permits COMMENT on the plan. */}
      <CommentThread
        targetType="PLAN"
        targetId={plan.id}
        allowedActions={plan.allowedActions}
      />

      {/* Outlook-sync surface (9.12) — visible but NON-BLOCKING (rule #4): a
          FAILED record shows a warning + retry while the lifecycle/list above
          stay fully usable. Renders only safeMessage (rule #7). */}
      {syncRecords && syncRecords.length > 0 ? (
        <section data-cy="sync-status-panel" className="mt-4 space-y-2">
          <h2 className="text-label font-semibold text-ink-secondary">
            Calendar sync
          </h2>
          {syncRecords.map((r) => (
            <div
              key={r.id}
              className="space-y-2 rounded-lg border border-border bg-surface px-4 py-3"
            >
              <SyncStatusBadge record={r} />
              <SyncRetryAction record={r} />
            </div>
          ))}
        </section>
      ) : null}

      {/* Edit an existing DRAFT commitment (9.7b, E6 PATCH). Keyed on the target
          so switching rows re-initialises the pre-filled form. */}
      {editingCommitment ? (
        <div className="mt-4">
          <CommitmentForm
            key={editingCommitment.id}
            planId={plan.id}
            planState={plan.state}
            commitment={editingCommitment}
            onDone={() => setEditingCommitmentId(null)}
          />
        </div>
      ) : null}

      {/* ADD_UNPLANNED (E11) — opened from the lifecycle bar's server-gated toggle.
          The unplanned form forces UNPLANNED kind server-side; SO is optional at
          create (enforced at close). */}
      {showUnplannedForm ? (
        <div className="mt-4">
          <CommitmentForm
            planId={plan.id}
            planState={plan.state}
            kind="UNPLANNED"
          />
        </div>
      ) : null}

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
