import { useState } from 'react';
import { useRespondDisputeMutation } from './disputesApi';
import { SupportingOutcomePicker } from '../rcdo/SupportingOutcomePicker';
import { can } from '../../shared/lib/allowedActions';
import type {
  AlignmentDisputeDto,
  RespondDisputeRequest,
} from '../../shared/lib/dtos';
import type { ParsedProblem } from '../../shared/lib/problemDetails';

/**
 * IC respond control (E18) — gated on `RESPOND_DISPUTE ∈ dispute.allowedActions`
 * (§11 server-authoritative; dormant until backend 5.5b). The IC replies with an
 * `icResponse` and/or re-links to a `newSupportingOutcomeId` (the
 * `SupportingOutcomePicker`); **≥1 of the two is required** (client-blocked when
 * both empty) AND the server validation `safeMessage` surfaces on a `400/422`
 * (§16). No optimistic flip — invalidate→refetch advances the dispute to
 * IC_RESPONDED.
 */
export function DisputeRespondForm({
  dispute,
  planId,
}: {
  dispute: AlignmentDisputeDto;
  planId: string;
}) {
  const [respondDispute, { isLoading }] = useRespondDisputeMutation();
  const [icResponse, setIcResponse] = useState('');
  const [newSoId, setNewSoId] = useState<string | null>(null);
  const [problem, setProblem] = useState<ParsedProblem | null>(null);

  if (!can('RESPOND_DISPUTE', dispute.allowedActions)) {
    return null;
  }

  const hasResponse = icResponse.trim().length > 0;
  const canSubmit = hasResponse || newSoId !== null;

  async function handleSubmit() {
    if (!canSubmit) {
      return;
    }
    setProblem(null);
    const body: RespondDisputeRequest = {
      ...(hasResponse ? { icResponse: icResponse.trim() } : {}),
      ...(newSoId !== null ? { newSupportingOutcomeId: newSoId } : {}),
    };
    try {
      await respondDispute({ disputeId: dispute.id, planId, body }).unwrap();
    } catch (err) {
      setProblem(err as ParsedProblem);
    }
  }

  return (
    <div data-cy="dispute-respond-form" className="flex flex-col gap-2">
      <textarea
        aria-label="Your response"
        value={icResponse}
        onChange={(e) => setIcResponse(e.target.value)}
        rows={2}
        placeholder="Respond to the manager's flag"
        className="w-full rounded-md border border-border-strong bg-surface px-3 py-2 text-body text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
      />
      <div>
        <p className="mb-1 text-meta text-ink-secondary">
          Re-link to a Supporting Outcome (optional)
        </p>
        <SupportingOutcomePicker value={newSoId} onChange={setNewSoId} />
      </div>
      <div className="flex justify-end">
        <button
          type="button"
          disabled={isLoading || !canSubmit}
          onClick={handleSubmit}
          className="rounded-md bg-brand-600 px-4 py-2 text-label font-semibold text-white hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-ring disabled:cursor-not-allowed disabled:opacity-50"
        >
          Respond
        </button>
      </div>
      {problem ? (
        <p
          data-cy="dispute-respond-error"
          role="alert"
          className="rounded-md border border-tone-failure-border bg-tone-failure-bg p-2 text-meta text-tone-failure-fg"
        >
          {problem.safeMessage}
        </p>
      ) : null}
    </div>
  );
}
