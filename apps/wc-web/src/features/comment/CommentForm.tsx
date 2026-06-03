import { useState } from 'react';
import { useCreateCommentMutation } from './commentsApi';
import { ErrorState } from '../../shared/components/ErrorState';
import type { CommentTargetType } from '../../shared/lib/dtos';

export interface CommentFormProps {
  targetType: CommentTargetType;
  targetId: string;
}

/**
 * Posts a flat comment (E21). The empty-body block is UX-only — the server stays
 * authoritative (it validates + authorizes; an unseeable target → `404`). On
 * success the input clears and the thread refetches via cache invalidation — no
 * optimistic insert (§7). A server rejection surfaces the parsed `safeMessage`
 * verbatim (B.21) while the body is retained for retry.
 */
export function CommentForm({ targetType, targetId }: CommentFormProps) {
  const [body, setBody] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [createComment, { isLoading }] = useCreateCommentMutation();

  const trimmed = body.trim();

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (trimmed.length === 0) {
      return;
    }
    setErrorMessage(null);
    try {
      await createComment({ targetType, targetId, body }).unwrap();
      setBody('');
    } catch (err) {
      setErrorMessage(
        (err as { safeMessage?: string } | undefined)?.safeMessage ??
          'Something went wrong. Please try again.',
      );
    }
  }

  return (
    <form data-cy="comment-form" onSubmit={onSubmit} className="space-y-2">
      <textarea
        aria-label="Add a comment"
        value={body}
        onChange={(e) => setBody(e.target.value)}
        rows={2}
        className="w-full rounded-lg border border-border-strong bg-surface px-3 py-2 text-body text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
        placeholder="Add a comment…"
      />
      {errorMessage ? <ErrorState message={errorMessage} /> : null}
      <div className="flex justify-end">
        <button
          type="submit"
          disabled={trimmed.length === 0 || isLoading}
          className="rounded-md border border-border-strong bg-surface-raised px-3 py-1 text-label font-semibold text-ink-primary hover:bg-surface-hover disabled:cursor-not-allowed disabled:opacity-40 focus:outline-none focus:ring-2 focus:ring-brand-ring"
        >
          Comment
        </button>
      </div>
    </form>
  );
}
