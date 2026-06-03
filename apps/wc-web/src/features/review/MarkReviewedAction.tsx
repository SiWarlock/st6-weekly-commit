import { useState } from 'react';
import { useMarkReviewedMutation } from './reviewApi';
import { can } from '../../shared/lib/allowedActions';
import type {
  ManagerReviewDto,
  MarkReviewedRequest,
} from '../../shared/lib/dtos';
import type { ParsedProblem } from '../../shared/lib/problemDetails';

/**
 * Reusable mark-reviewed affordance (E16). Renders **iff**
 * `can('MARK_REVIEWED', review.allowedActions)` — never re-derived client-side
 * (server-authoritative, LESSONS §11). Submits the optional `summaryNote` only
 * (the server derives the status); invalidate→refetch, no optimistic flip; a
 * rejection renders the server `safeMessage`/`fieldErrors[]` verbatim. `reviewId`
 * + `planId` are derived from the review (id + weeklyPlanId).
 */
export function MarkReviewedAction({ review }: { review: ManagerReviewDto }) {
  const [markReviewed, { isLoading }] = useMarkReviewedMutation();
  const [summaryNote, setSummaryNote] = useState('');
  const [problem, setProblem] = useState<ParsedProblem | null>(null);

  if (!can('MARK_REVIEWED', review.allowedActions)) {
    return null;
  }

  const summaryId = `review-summary-${review.id}`;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setProblem(null);
    const body: MarkReviewedRequest = summaryNote ? { summaryNote } : {};
    try {
      await markReviewed({
        reviewId: review.id,
        planId: review.weeklyPlanId,
        body,
      }).unwrap();
      setSummaryNote('');
    } catch (err) {
      setProblem(err as ParsedProblem);
    }
  }

  return (
    <form
      data-cy="mark-reviewed-form"
      onSubmit={handleSubmit}
      className="space-y-3 rounded-md border border-border bg-surface-raised p-3"
    >
      <label htmlFor={summaryId} className="flex flex-col gap-1">
        <span className="text-label text-ink-secondary">Summary note</span>
        <textarea
          id={summaryId}
          value={summaryNote}
          onChange={(e) => setSummaryNote(e.target.value)}
          rows={2}
          className="rounded-md border border-border bg-surface px-3 py-2 text-body text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
        />
      </label>

      {problem ? (
        <div
          data-cy="mark-reviewed-error"
          role="alert"
          className="rounded-md border border-tone-failure-border bg-tone-failure-bg p-3 text-tone-failure-fg"
        >
          <p data-cy="mark-reviewed-error-message">{problem.safeMessage}</p>
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
        Mark reviewed
      </button>
    </form>
  );
}
