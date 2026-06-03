import { HiLightningBolt } from 'react-icons/hi';
import { Badge } from '../../shared/components/Badge';
import { RiskBadge } from '../../shared/components/RiskBadge';
import { can } from '../../shared/lib/allowedActions';
import { CarryForwardButton } from './CarryForwardButton';
import { ReconciliationOutcomeForm } from './ReconciliationOutcomeForm';
import { DeleteCommitmentButton } from './DeleteCommitmentButton';
import { CommentThread } from '../comment/CommentThread';
import { PriorityTag } from './PriorityTag';
import { WorkTypeTag } from './WorkTypeTag';
import { ConfidenceMeter } from './ConfidenceMeter';
import { AlignmentChip } from './AlignmentChip';
import type { PlanState, WeeklyCommitmentDto } from '../../shared/lib/dtos';

export interface CommitmentListProps {
  commitments: WeeklyCommitmentDto[];
  planState: PlanState;
  /** The owning plan id — threaded to the per-row reconciliation mutations. */
  planId: string;
  /**
   * Opens the edit form for a commitment (9.7b). Wired only in DRAFT; the owning
   * view holds the "editing" state — this list stays presentational.
   */
  onEdit?: (commitment: WeeklyCommitmentDto) => void;
}

/** Risk badge a commitment surfaces from its alignment self-assessment, if any. */
function alignmentRisk(c: WeeklyCommitmentDto): string | null {
  if (c.alignmentStatus === 'MISALIGNED') return 'MISALIGNED';
  if (c.alignmentStatus === 'NEEDS_REVIEW') return 'NEEDS_REVIEW';
  return null;
}

/**
 * Renders the plan's commitments (REQ-UX-002): PLANNED vs UNPLANNED (kind badge)
 * vs carried-forward (`RiskBadge CARRY_FORWARD`) + alignment risk. During
 * `RECONCILING` each unresolved row surfaces the per-commitment reconciliation
 * choice set — the outcome form (`!reconciliationOutcome`) and, when the server
 * permits it, the carry-forward control (`CARRY_FORWARD ∈ allowedActions[]`). The
 * two gates are **independent** (both can coexist for one unresolved commitment;
 * recording either resolves it server-side → both vanish on refetch). Every gate
 * reads server `allowedActions[]`/`plan.state` — never re-derived client-side.
 * Titles render React-escaped.
 */
export function CommitmentList({
  commitments,
  planState,
  planId,
  onEdit,
}: CommitmentListProps) {
  const reconciling = planState === 'RECONCILING';
  // DRAFT baseline is editable/deletable (server-authoritative — a post-lock
  // attempt is rejected 409; the affordance is hidden, not the only guard).
  const draft = planState === 'DRAFT';
  return (
    <ul data-cy="commitment-list" className="space-y-2">
      {commitments.map((c) => {
        const carriedForward = Boolean(c.carryForwardSourceCommitmentId);
        const risk = alignmentRisk(c);
        const showCarry = reconciling && can('CARRY_FORWARD', c.allowedActions);
        const showOutcomeForm = reconciling && !c.reconciliationOutcome;
        return (
          <li
            key={c.id}
            data-cy="commitment-row"
            className="flex flex-col gap-2 rounded-lg border border-border bg-surface px-4 py-3"
          >
            <div className="flex items-center justify-between gap-3">
              <div className="flex min-w-0 flex-wrap items-center gap-2">
                <span className="truncate text-body text-ink-primary">
                  {c.title}
                </span>
                {c.commitmentKind === 'UNPLANNED' ? (
                  <Badge
                    tone="accent"
                    icon={HiLightningBolt}
                    label="Unplanned"
                    size="xs"
                    dataCy="kind-badge"
                  />
                ) : null}
                {carriedForward ? <RiskBadge value="CARRY_FORWARD" /> : null}
                {risk ? <RiskBadge value={risk} /> : null}
              </div>
              <div className="flex flex-none items-center gap-2">
                {showCarry ? (
                  <CarryForwardButton commitment={c} planId={planId} />
                ) : null}
                {draft ? (
                  <>
                    <button
                      type="button"
                      onClick={() => onEdit?.(c)}
                      className="rounded-md border border-border-strong bg-surface-raised px-3 py-1 text-label text-ink-primary hover:bg-surface-hover focus:outline-none focus:ring-2 focus:ring-brand-ring"
                    >
                      Edit
                    </button>
                    <DeleteCommitmentButton commitment={c} planId={planId} />
                  </>
                ) : null}
              </div>
            </div>
            {/* Chess-layer atoms (ST.3) — priority / workType / confidence /
                alignment, skinned per the Cadence enum→tone maps. */}
            <div className="flex flex-wrap items-center gap-2">
              <PriorityTag value={c.priority} />
              <WorkTypeTag value={c.workType} />
              <ConfidenceMeter value={c.confidence} />
              <AlignmentChip value={c.alignmentStatus} />
            </div>
            {showOutcomeForm ? (
              <ReconciliationOutcomeForm commitment={c} planId={planId} />
            ) : null}
            {/* COMMENT-gated, lazy comment thread (9.11b) — renders nothing
                unless the server permits COMMENT on this commitment. */}
            <CommentThread
              targetType="COMMITMENT"
              targetId={c.id}
              allowedActions={c.allowedActions}
            />
          </li>
        );
      })}
    </ul>
  );
}
