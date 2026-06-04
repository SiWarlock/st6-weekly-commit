import { useState } from 'react';
import { useResolveDisputeMutation } from './disputesApi';
import { can } from '../../shared/lib/allowedActions';
import type { AlignmentDisputeDto } from '../../shared/lib/dtos';
import type { ParsedProblem } from '../../shared/lib/problemDetails';

/**
 * Manager resolve control (E19) — gated on `RESOLVE_DISPUTE ∈ dispute.allowedActions`
 * (§11 server-authoritative; dormant until backend 5.5b emits it). Optional
 * `resolutionNote`. On a `403 IC_CANNOT_RESOLVE_DISPUTE` (or any rejection) it
 * surfaces the server `safeMessage` verbatim (§16). No optimistic flip —
 * invalidate→refetch (the resolved dispute then drops to `null`, B.6).
 */
export function DisputeResolveAction({
  dispute,
  planId,
}: {
  dispute: AlignmentDisputeDto;
  planId: string;
}) {
  const [resolveDispute, { isLoading }] = useResolveDisputeMutation();
  const [note, setNote] = useState('');
  const [problem, setProblem] = useState<ParsedProblem | null>(null);

  if (!can('RESOLVE_DISPUTE', dispute.allowedActions)) {
    return null;
  }

  async function handleResolve() {
    setProblem(null);
    try {
      await resolveDispute({
        disputeId: dispute.id,
        planId,
        body: note.trim() ? { resolutionNote: note.trim() } : {},
      }).unwrap();
    } catch (err) {
      setProblem(err as ParsedProblem);
    }
  }

  return (
    <div data-cy="dispute-resolve" className="flex flex-col gap-2">
      <textarea
        aria-label="Resolution note"
        value={note}
        onChange={(e) => setNote(e.target.value)}
        rows={2}
        placeholder="Resolution note (optional)"
        className="w-full rounded-md border border-border-strong bg-surface px-3 py-2 text-body text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
      />
      <div className="flex justify-end">
        <button
          type="button"
          disabled={isLoading}
          onClick={handleResolve}
          className="rounded-md bg-brand-600 px-4 py-2 text-label font-semibold text-white hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-ring disabled:cursor-not-allowed disabled:opacity-50"
        >
          Resolve dispute
        </button>
      </div>
      {problem ? (
        <p
          data-cy="dispute-resolve-error"
          role="alert"
          className="rounded-md border border-tone-failure-border bg-tone-failure-bg p-2 text-meta text-tone-failure-fg"
        >
          {problem.safeMessage}
        </p>
      ) : null}
    </div>
  );
}
