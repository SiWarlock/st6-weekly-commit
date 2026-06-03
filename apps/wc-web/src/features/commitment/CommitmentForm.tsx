import { useState } from 'react';
import {
  useCreateCommitmentMutation,
  useAddUnplannedCommitmentMutation,
  useUpdateCommitmentMutation,
} from './commitmentsApi';
import { SupportingOutcomePicker } from '../rcdo/SupportingOutcomePicker';
import { ChessLayerFields, type ChessValue } from './ChessLayerFields';
import type {
  PlanState,
  CommitmentKind,
  WeeklyCommitmentDto,
  CreateCommitmentRequest,
  CreateUnplannedCommitmentRequest,
  PatchCommitmentRequest,
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
 * Controlled commitment form with three modes (driven by props):
 * - **create** (default) — E5 `createCommitment` (title/description, the 9.5
 *   SupportingOutcomePicker link, the chess layer).
 * - **unplanned** (`kind='UNPLANNED'`, E11 ADD_UNPLANNED during RECONCILING) —
 *   hides WorkType, submits via `addUnplannedCommitment` (server forces UNPLANNED).
 * - **edit** (`commitment` given, 9.7b) — pre-fills from the existing DRAFT
 *   baseline, submits via `updateCommitment` (E6 PATCH), then calls `onDone`.
 * All paths invalidate→refetch — **no optimistic write**. Client-side required
 * checks are UX-only; the SERVER is authoritative, its `409` `safeMessage` +
 * per-field `fieldErrors[]` render verbatim (e.g. a post-lock edit →
 * `409 LOCKED_BASELINE_EDIT`). Title is React-escaped where shown.
 */
export function CommitmentForm({
  planId,
  planState,
  kind = 'PLANNED',
  commitment,
  onDone,
}: {
  planId: string;
  planState: PlanState;
  kind?: CommitmentKind;
  /** Edit target (9.7b) — present → edit mode (pre-fill + `updateCommitment`). */
  commitment?: WeeklyCommitmentDto;
  /** Called after a successful edit (e.g. to close the edit form). */
  onDone?: () => void;
}) {
  const isEdit = Boolean(commitment);
  const [title, setTitle] = useState(commitment?.title ?? '');
  const [description, setDescription] = useState(commitment?.description ?? '');
  const [supportingOutcomeId, setSupportingOutcomeId] = useState<string | null>(
    commitment?.supportingOutcomeId ?? null,
  );
  const [chess, setChess] = useState<ChessValue>(() =>
    commitment
      ? {
          priority: commitment.priority,
          workType: commitment.workType,
          confidence: commitment.confidence,
          alignmentStatus: commitment.alignmentStatus,
        }
      : DEFAULT_CHESS,
  );
  const [problem, setProblem] = useState<ParsedProblem | null>(null);
  const [create, { isLoading: creating }] = useCreateCommitmentMutation();
  const [addUnplanned, { isLoading: addingUnplanned }] =
    useAddUnplannedCommitmentMutation();
  const [update, { isLoading: updating }] = useUpdateCommitmentMutation();
  const isUnplanned = kind === 'UNPLANNED' && !isEdit;
  const isLoading = isEdit
    ? updating
    : isUnplanned
      ? addingUnplanned
      : creating;

  function resetFields() {
    setTitle('');
    setDescription('');
    setSupportingOutcomeId(null);
    setChess(DEFAULT_CHESS);
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setProblem(null);
    try {
      if (isEdit && commitment) {
        // E6 PATCH — the DRAFT baseline edit (server rejects post-lock with 409).
        const patch: PatchCommitmentRequest = {
          title,
          ...(description ? { description } : {}),
          ...(supportingOutcomeId ? { supportingOutcomeId } : {}),
          priority: chess.priority,
          workType: chess.workType,
          confidence: chess.confidence,
          alignmentStatus: chess.alignmentStatus,
        };
        await update({ id: commitment.id, planId, patch }).unwrap();
        onDone?.();
      } else if (isUnplanned) {
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
        resetFields();
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
        resetFields();
      }
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
        {isEdit
          ? 'Save changes'
          : isUnplanned
            ? 'Add unplanned commitment'
            : 'Create commitment'}
      </button>
    </form>
  );
}
