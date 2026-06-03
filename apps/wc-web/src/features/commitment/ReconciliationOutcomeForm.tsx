import { useState } from 'react';
import { useUpdateCommitmentMutation } from './commitmentsApi';
import type {
  WeeklyCommitmentDto,
  ReconciliationOutcome,
  PatchCommitmentRequest,
} from '../../shared/lib/dtos';
import type { ParsedProblem } from '../../shared/lib/problemDetails';

/**
 * The four COMPLETION outcomes (§3). `CARRIED_FORWARD` is deliberately absent —
 * carry-forward is reached ONLY via the E12 `CarryForwardButton`; a direct PATCH
 * of `CARRIED_FORWARD` is server-rejected (backend 4.1). Offering a value the
 * server 4xx-rejects would be a UX trap.
 */
type CompletionOutcome = Exclude<ReconciliationOutcome, 'CARRIED_FORWARD'>;

const OUTCOME_OPTIONS: { value: CompletionOutcome; label: string }[] = [
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'PARTIALLY_COMPLETED', label: 'Partially completed' },
  { value: 'BLOCKED', label: 'Blocked' },
  { value: 'CANCELED', label: 'Canceled' },
];

/**
 * Records a per-commitment reconciliation outcome (E6 PATCH `{reconciliationOutcome,
 * outcomeNote}`) via the existing `updateCommitment` mutation — invalidate→refetch,
 * **no optimistic write**. The server is authoritative on validity; its `409`/`422`
 * `safeMessage` + per-field `fieldErrors[]` render verbatim (LESSONS §11). The IC
 * picks exactly one outcome; recording it resolves the commitment server-side, so
 * the form (and any carry-forward control) vanish on the refetch.
 */
export function ReconciliationOutcomeForm({
  commitment,
  planId,
}: {
  commitment: WeeklyCommitmentDto;
  planId: string;
}) {
  const [outcome, setOutcome] = useState<'' | CompletionOutcome>('');
  const [note, setNote] = useState('');
  const [problem, setProblem] = useState<ParsedProblem | null>(null);
  const [update, { isLoading }] = useUpdateCommitmentMutation();

  const outcomeId = `outcome-${commitment.id}`;
  const noteId = `outcome-note-${commitment.id}`;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setProblem(null);
    if (!outcome) {
      return;
    }
    const patch: PatchCommitmentRequest = {
      reconciliationOutcome: outcome,
      ...(note ? { outcomeNote: note } : {}),
    };
    try {
      await update({ id: commitment.id, planId, patch }).unwrap();
      setOutcome('');
      setNote('');
    } catch (err) {
      // The transformErrorResponse output (parsed RFC-7807). Surface verbatim.
      setProblem(err as ParsedProblem);
    }
  }

  return (
    <form
      data-cy="reconciliation-outcome-form"
      onSubmit={handleSubmit}
      className="mt-1 space-y-3 rounded-md border border-border bg-surface-raised p-3"
    >
      <label htmlFor={outcomeId} className="flex flex-col gap-1">
        <span className="text-label text-ink-secondary">Outcome</span>
        <select
          id={outcomeId}
          value={outcome}
          onChange={(e) => setOutcome(e.target.value as '' | CompletionOutcome)}
          className="rounded-md border border-border bg-surface px-3 py-2 text-body text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
        >
          <option value="">Select an outcome…</option>
          {OUTCOME_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </label>

      <label htmlFor={noteId} className="flex flex-col gap-1">
        <span className="text-label text-ink-secondary">Note</span>
        <textarea
          id={noteId}
          value={note}
          onChange={(e) => setNote(e.target.value)}
          rows={2}
          className="rounded-md border border-border bg-surface px-3 py-2 text-body text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
        />
      </label>

      {problem ? (
        <div
          data-cy="outcome-error"
          role="alert"
          className="rounded-md border border-tone-failure-border bg-tone-failure-bg p-3 text-tone-failure-fg"
        >
          <p data-cy="outcome-error-message">{problem.safeMessage}</p>
          {problem.fieldErrors.length > 0 ? (
            <ul className="mt-1 list-disc pl-5 text-meta">
              {problem.fieldErrors.map((fe) => (
                <li key={fe.field}>{fe.message}</li>
              ))}
            </ul>
          ) : null}
        </div>
      ) : null}

      <button
        type="submit"
        disabled={isLoading}
        className="rounded-md bg-brand-600 px-4 py-2 text-label font-semibold text-white hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-ring disabled:cursor-not-allowed disabled:opacity-50"
      >
        Record outcome
      </button>
    </form>
  );
}
