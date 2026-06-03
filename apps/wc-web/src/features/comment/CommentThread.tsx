import { useState } from 'react';
import { can } from '../../shared/lib/allowedActions';
import { CommentList } from './CommentList';
import { CommentForm } from './CommentForm';
import type { AllowedAction, CommentTargetType } from '../../shared/lib/dtos';

export interface CommentThreadProps {
  targetType: CommentTargetType;
  targetId: string;
  allowedActions: readonly AllowedAction[];
}

/**
 * The COMMENT-gated, lazy/collapsible comment affordance for a plan or commitment.
 * Renders nothing unless the server permits `COMMENT` on the target
 * (`allowedActions[]` is the only gate — never re-derived client-side, §6/§11).
 * Collapsed by default; `CommentList` (and its `getComments` query) + `CommentForm`
 * mount only when opened — so a plan with N commitments fires no comment queries
 * until the IC/manager opens a specific thread.
 */
export function CommentThread({
  targetType,
  targetId,
  allowedActions,
}: CommentThreadProps) {
  const [open, setOpen] = useState(false);

  if (!can('COMMENT', allowedActions)) {
    return null;
  }

  return (
    <div
      data-cy="comment-thread"
      data-target-type={targetType}
      data-target-id={targetId}
      className="mt-2"
    >
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        className="text-label font-semibold text-ink-secondary hover:text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
      >
        {open ? 'Hide comments' : 'Comments'}
      </button>
      {open ? (
        <div className="mt-2 space-y-3">
          <CommentList targetType={targetType} targetId={targetId} />
          <CommentForm targetType={targetType} targetId={targetId} />
        </div>
      ) : null}
    </div>
  );
}
