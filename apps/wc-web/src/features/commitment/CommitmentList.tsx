import { HiLightningBolt } from 'react-icons/hi';
import { Badge } from '../../shared/components/Badge';
import { RiskBadge } from '../../shared/components/RiskBadge';
import { can } from '../../shared/lib/allowedActions';
import type { PlanState, WeeklyCommitmentDto } from '../../shared/lib/dtos';

export interface CommitmentListProps {
  commitments: WeeklyCommitmentDto[];
  planState: PlanState;
  /** Optional carry-forward handler — the control renders only when provided AND
   * the commitment's allowedActions[] permit it (wired in 9.8). */
  onCarryForward?: (commitmentId: string) => void;
}

/** Risk badge a commitment surfaces from its alignment self-assessment, if any. */
function alignmentRisk(c: WeeklyCommitmentDto): string | null {
  if (c.alignmentStatus === 'MISALIGNED') return 'MISALIGNED';
  if (c.alignmentStatus === 'NEEDS_REVIEW') return 'NEEDS_REVIEW';
  return null;
}

/**
 * Renders the plan's commitments (REQ-UX-002): PLANNED vs UNPLANNED (kind badge)
 * vs carried-forward (`RiskBadge CARRY_FORWARD`) + alignment risk, with each
 * row's action controls driven ONLY by that commitment's server `allowedActions[]`
 * via `can()` — never re-derived client-side. Titles render React-escaped.
 */
export function CommitmentList({
  commitments,
  onCarryForward,
}: CommitmentListProps) {
  return (
    <ul data-cy="commitment-list" className="space-y-2">
      {commitments.map((c) => {
        const carriedForward = Boolean(c.carryForwardSourceCommitmentId);
        const risk = alignmentRisk(c);
        return (
          <li
            key={c.id}
            data-cy="commitment-row"
            className="flex items-center justify-between gap-3 rounded-lg border border-border bg-surface px-4 py-3"
          >
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
              {onCarryForward && can('CARRY_FORWARD', c.allowedActions) ? (
                <button
                  type="button"
                  onClick={() => onCarryForward(c.id)}
                  className="rounded-md border border-border-strong bg-surface-raised px-3 py-1 text-label text-ink-primary hover:bg-surface-hover focus:outline-none focus:ring-2 focus:ring-brand-ring"
                >
                  Carry forward
                </button>
              ) : null}
            </div>
          </li>
        );
      })}
    </ul>
  );
}
