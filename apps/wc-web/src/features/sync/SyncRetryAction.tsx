import { useState } from 'react';
import { useRetrySyncMutation } from './syncApi';
import { can } from '../../shared/lib/allowedActions';
import type { OutlookSyncRecordDto } from '../../shared/lib/dtos';
import type { ParsedProblem } from '../../shared/lib/problemDetails';

/**
 * Manual Outlook-sync retry (E23). Renders **iff** `can('RETRY_SYNC',
 * record.allowedActions)` (server-authoritative — `RETRY_SYNC` is present only on a
 * `FAILED` record the actor owns; never re-derived client-side, LESSONS §11).
 * Self-contained (owns the mutation + error), mirroring `LockButton`. On click it
 * re-publishes (no body) → invalidate→refetch (the badge moves toward
 * `RETRY_REQUESTED`/`QUEUED`; **no optimistic flip**). A `409` (e.g. retry on a
 * non-`FAILED` record) renders the server `safeMessage` verbatim.
 */
export function SyncRetryAction({ record }: { record: OutlookSyncRecordDto }) {
  const [retrySync, { isLoading }] = useRetrySyncMutation();
  const [problem, setProblem] = useState<ParsedProblem | null>(null);

  if (!can('RETRY_SYNC', record.allowedActions)) {
    return null;
  }

  async function handleRetry() {
    setProblem(null);
    try {
      await retrySync({ syncRecordId: record.id }).unwrap();
    } catch (err) {
      setProblem(err as ParsedProblem);
    }
  }

  return (
    <div className="flex flex-col items-start gap-2">
      <button
        type="button"
        disabled={isLoading}
        onClick={handleRetry}
        className="rounded-md border border-border-strong bg-surface-raised px-3 py-1 text-label text-ink-primary hover:bg-surface-hover focus:outline-none focus:ring-2 focus:ring-brand-ring disabled:cursor-not-allowed disabled:opacity-50"
      >
        Retry sync
      </button>
      {problem ? (
        <div
          data-cy="sync-retry-error"
          role="alert"
          className="rounded-md border border-tone-failure-border bg-tone-failure-bg p-3 text-tone-failure-fg"
        >
          <p data-cy="sync-retry-error-message">{problem.safeMessage}</p>
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
