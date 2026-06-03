import { useState } from 'react';
import { useDeleteCommitmentMutation } from './commitmentsApi';
import type { WeeklyCommitmentDto } from '../../shared/lib/dtos';
import type { ParsedProblem } from '../../shared/lib/problemDetails';

/**
 * DRAFT-stage delete affordance (E7, 9.7b). Self-contained (owns the mutation +
 * error), mirroring `LockButton`/`CarryForwardButton`. Uses a **non-blocking
 * in-app two-step confirm** (Delete → Confirm delete / Cancel) — never
 * `window.confirm` (blocking native dialog; not cleanly testable). Cancel re-arms
 * to the initial state. On confirm it invokes `deleteCommitment` → invalidate→
 * refetch (the row vanishes only on the refetch — **no optimistic removal**). The
 * server is authoritative: a post-lock delete is rejected `409 LOCKED_BASELINE_EDIT`
 * and the `safeMessage` renders verbatim (the affordance is normally DRAFT-gated,
 * but the client never re-derives legality — LESSONS §11).
 */
export function DeleteCommitmentButton({
  commitment,
  planId,
}: {
  commitment: WeeklyCommitmentDto;
  planId: string;
}) {
  const [deleteCommitment, { isLoading }] = useDeleteCommitmentMutation();
  const [confirming, setConfirming] = useState(false);
  const [problem, setProblem] = useState<ParsedProblem | null>(null);

  async function handleConfirm() {
    setProblem(null);
    try {
      await deleteCommitment({ id: commitment.id, planId }).unwrap();
      // Success → the plan refetch drops this row (no optimistic removal here).
    } catch (err) {
      setProblem(err as ParsedProblem);
    }
  }

  return (
    <div className="flex flex-col items-end gap-2">
      {confirming ? (
        <div className="flex items-center gap-2">
          <span className="text-meta text-ink-secondary">
            Delete this commitment?
          </span>
          <button
            type="button"
            disabled={isLoading}
            onClick={handleConfirm}
            className="rounded-md bg-tone-failure-bg px-3 py-1 text-label font-semibold text-tone-failure-fg hover:opacity-90 focus:outline-none focus:ring-2 focus:ring-brand-ring disabled:cursor-not-allowed disabled:opacity-50"
          >
            Confirm delete
          </button>
          <button
            type="button"
            onClick={() => setConfirming(false)}
            className="rounded-md border border-border-strong bg-surface-raised px-3 py-1 text-label text-ink-primary hover:bg-surface-hover focus:outline-none focus:ring-2 focus:ring-brand-ring"
          >
            Cancel
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => setConfirming(true)}
          className="rounded-md border border-border-strong bg-surface-raised px-3 py-1 text-label text-ink-primary hover:bg-surface-hover focus:outline-none focus:ring-2 focus:ring-brand-ring"
        >
          Delete
        </button>
      )}
      {problem ? (
        <div
          data-cy="delete-error"
          role="alert"
          className="rounded-md border border-tone-failure-border bg-tone-failure-bg p-3 text-tone-failure-fg"
        >
          <p data-cy="delete-error-message">{problem.safeMessage}</p>
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
