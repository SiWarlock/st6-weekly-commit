import { useState } from 'react';
import { useGetCommentsQuery } from './commentsApi';
import { LoadingState } from '../../shared/components/LoadingState';
import { ErrorState } from '../../shared/components/ErrorState';
import { EmptyState } from '../../shared/components/EmptyState';
import { Pagination } from '../../shared/components/Pagination';
import type { CommentTargetType } from '../../shared/lib/dtos';

export interface CommentListProps {
  targetType: CommentTargetType;
  targetId: string;
}

/**
 * The flat (one-level) comment thread for a plan or commitment (§11, B.9). Reads
 * the B.20 paginated envelope (E20, default sort createdAt ASC) and renders the
 * §7 view-states — loading / empty / error / success — with server-side
 * pagination (no client slicing). `parentCommentId`/`depth` are ignored for
 * layout (always null/0 in MVP); every comment renders as a flat sibling. The
 * `body` renders React-escaped — never `dangerouslySetInnerHTML` (REQ-S-005, §16).
 */
export function CommentList({ targetType, targetId }: CommentListProps) {
  const [page, setPage] = useState(0);
  const { data, isLoading, isError, error } = useGetCommentsQuery({
    targetType,
    targetId,
    page,
  });

  if (isError) {
    const message =
      (error as { safeMessage?: string } | undefined)?.safeMessage ??
      'These comments are not available.';
    return <ErrorState message={message} />;
  }
  if (isLoading || !data) {
    return <LoadingState variant="inline" delayMs={0} />;
  }

  const comments = data.content;
  if (comments.length === 0) {
    return (
      <EmptyState
        title="No comments yet"
        message="Be the first to add a comment."
      />
    );
  }

  return (
    <div data-cy="comment-thread-list" className="space-y-3">
      <ul className="space-y-2">
        {comments.map((c) => (
          <li
            key={c.id}
            data-cy="comment-row"
            className="rounded-lg border border-border bg-surface px-3 py-2"
          >
            <div className="flex items-baseline justify-between gap-3">
              <span className="text-label font-semibold text-ink-primary">
                {c.authorDisplayName}
              </span>
              <time
                data-cy="comment-time"
                dateTime={c.createdAt}
                className="text-meta text-ink-muted"
              >
                {c.createdAt}
              </time>
            </div>
            {/* React default escaping makes any HTML payload inert (REQ-S-005). */}
            <p className="whitespace-pre-wrap break-words text-body text-ink-secondary">
              {c.body}
            </p>
          </li>
        ))}
      </ul>
      <Pagination page={data.page} onPageChange={setPage} />
    </div>
  );
}
