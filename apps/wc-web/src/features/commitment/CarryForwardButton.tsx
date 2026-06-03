import { useState } from 'react';
import { useCarryForwardMutation } from './commitmentsApi';
import { can } from '../../shared/lib/allowedActions';
import type { WeeklyCommitmentDto } from '../../shared/lib/dtos';
import type { ParsedProblem } from '../../shared/lib/problemDetails';

/**
 * The E12 carry-forward affordance (§3) — the ONLY path that sets the
 * `CARRIED_FORWARD` outcome (a direct PATCH of it is server-rejected, backend 4.1).
 * Renders **iff** `CARRY_FORWARD ∈ commitment.allowedActions[]` — the frontend
 * never re-derives eligibility (server-authoritative, LESSONS §11). On click it
 * invokes E12 (invalidate→refetch — no optimistic flip); a rejection renders the
 * server `safeMessage` verbatim. Self-contained, mirroring `LockButton`.
 */
export function CarryForwardButton({
  commitment,
  planId,
}: {
  commitment: WeeklyCommitmentDto;
  planId: string;
}) {
  const [carryForward, { isLoading }] = useCarryForwardMutation();
  const [problem, setProblem] = useState<ParsedProblem | null>(null);

  if (!can('CARRY_FORWARD', commitment.allowedActions)) {
    return null;
  }

  async function handleClick() {
    setProblem(null);
    try {
      await carryForward({ id: commitment.id, planId }).unwrap();
    } catch (err) {
      setProblem(err as ParsedProblem);
    }
  }

  return (
    <div className="flex flex-col items-end gap-2">
      <button
        type="button"
        disabled={isLoading}
        onClick={handleClick}
        className="rounded-md border border-border-strong bg-surface-raised px-3 py-1 text-label text-ink-primary hover:bg-surface-hover focus:outline-none focus:ring-2 focus:ring-brand-ring disabled:cursor-not-allowed disabled:opacity-50"
      >
        Carry forward
      </button>
      {problem ? (
        <div
          data-cy="carry-forward-error"
          role="alert"
          className="rounded-md border border-tone-failure-border bg-tone-failure-bg p-3 text-tone-failure-fg"
        >
          <p data-cy="carry-forward-error-message">{problem.safeMessage}</p>
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
