import { useState } from 'react';
import {
  useCreateCommitmentMutation,
  useAddUnplannedCommitmentMutation,
} from './commitmentsApi';
import { SupportingOutcomePicker } from '../rcdo/SupportingOutcomePicker';
import { ChessLayerFields, type ChessValue } from './ChessLayerFields';
import type {
  PlanState,
  CommitmentKind,
  CreateCommitmentRequest,
  CreateUnplannedCommitmentRequest,
} from '../../shared/lib/dtos';
import type { ParsedProblem } from '../../shared/lib/problemDetails';

const DEFAULT_CHESS: ChessValue = {
  priority: 'P1',
  workType: 'STRATEGIC',
  confidence: 'MEDIUM',
  // §3 default for a new IC commitment.
  alignmentStatus: 'NEEDS_REVIEW',
};

/**
 * Controlled commitment form. In the default `PLANNED` mode it is the E5 create
 * form (title/description, the 9.5 SupportingOutcomePicker link, the chess layer)
 * → `createCommitment`. In `UNPLANNED` mode (E11, ADD_UNPLANNED during RECONCILING)
 * it hides the WorkType field and submits via `addUnplannedCommitment` — the server
 * forces `commitmentKind/workType=UNPLANNED`; `supportingOutcomeId` is optional at
 * create. Both paths invalidate→refetch — **no optimistic write**. Client-side
 * required checks are UX-only; the SERVER is authoritative, its `409` `safeMessage`
 * + per-field `fieldErrors[]` render verbatim. Title is React-escaped where shown.
 */
export function CommitmentForm({
  planId,
  planState,
  kind = 'PLANNED',
}: {
  planId: string;
  planState: PlanState;
  kind?: CommitmentKind;
}) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [supportingOutcomeId, setSupportingOutcomeId] = useState<string | null>(
    null,
  );
  const [chess, setChess] = useState<ChessValue>(DEFAULT_CHESS);
  const [problem, setProblem] = useState<ParsedProblem | null>(null);
  const [create, { isLoading: creating }] = useCreateCommitmentMutation();
  const [addUnplanned, { isLoading: addingUnplanned }] =
    useAddUnplannedCommitmentMutation();
  const isUnplanned = kind === 'UNPLANNED';
  const isLoading = isUnplanned ? addingUnplanned : creating;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setProblem(null);
    try {
      if (isUnplanned) {
        // E11 — no `workType` (the server forces UNPLANNED); SO optional.
        const body: CreateUnplannedCommitmentRequest = {
          title,
          ...(description ? { description } : {}),
          ...(supportingOutcomeId ? { supportingOutcomeId } : {}),
          priority: chess.priority,
          confidence: chess.confidence,
          alignmentStatus: chess.alignmentStatus,
        };
        await addUnplanned({ planId, body }).unwrap();
      } else {
        const body: CreateCommitmentRequest = {
          title,
          ...(description ? { description } : {}),
          ...(supportingOutcomeId ? { supportingOutcomeId } : {}),
          priority: chess.priority,
          workType: chess.workType,
          confidence: chess.confidence,
          alignmentStatus: chess.alignmentStatus,
        };
        await create({ planId, body }).unwrap();
      }
      setTitle('');
      setDescription('');
      setSupportingOutcomeId(null);
      setChess(DEFAULT_CHESS);
    } catch (err) {
      // The transformErrorResponse output (parsed RFC-7807). Surface verbatim.
      setProblem(err as ParsedProblem);
    }
  }

  return (
    <form
      data-cy="commitment-form"
      onSubmit={handleSubmit}
      className="space-y-4 rounded-lg border border-border bg-surface p-4"
    >
      <label htmlFor="commitment-title" className="flex flex-col gap-1">
        <span className="text-label text-ink-secondary">Title</span>
        <input
          id="commitment-title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          className="rounded-md border border-border bg-surface-raised px-3 py-2 text-body text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
        />
      </label>

      <label htmlFor="commitment-description" className="flex flex-col gap-1">
        <span className="text-label text-ink-secondary">Description</span>
        <textarea
          id="commitment-description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={2}
          className="rounded-md border border-border bg-surface-raised px-3 py-2 text-body text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
        />
      </label>

      <div className="flex flex-col gap-1">
        <span className="text-label text-ink-secondary">
          Supporting Outcome
        </span>
        <SupportingOutcomePicker
          value={supportingOutcomeId}
          onChange={setSupportingOutcomeId}
        />
      </div>

      <ChessLayerFields
        value={chess}
        onChange={(patch) => setChess((c) => ({ ...c, ...patch }))}
        planState={planState}
        hideWorkType={isUnplanned}
      />

      {problem ? (
        <div
          data-cy="form-error"
          role="alert"
          className="rounded-md border border-tone-failure-border bg-tone-failure-bg p-3 text-tone-failure-fg"
        >
          <p data-cy="form-error-message">{problem.safeMessage}</p>
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
        className="rounded-md bg-brand-600 px-4 py-2 text-label font-semibold text-white hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-ring disabled:opacity-60"
      >
        {isUnplanned ? 'Add unplanned commitment' : 'Create commitment'}
      </button>
    </form>
  );
}
