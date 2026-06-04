import { HiLightningBolt, HiLockClosed } from 'react-icons/hi';
import { Badge } from '../../shared/components/Badge';
import { RiskBadge } from '../../shared/components/RiskBadge';
import { can } from '../../shared/lib/allowedActions';
import { CarryForwardButton } from './CarryForwardButton';
import { ReconciliationOutcomeForm } from './ReconciliationOutcomeForm';
import { DeleteCommitmentButton } from './DeleteCommitmentButton';
import { CommentThread } from '../comment/CommentThread';
import { DisputePanel } from '../dispute/DisputePanel';
import { PriorityTag } from './PriorityTag';
import { WorkTypeTag } from './WorkTypeTag';
import { ConfidenceMeter } from './ConfidenceMeter';
import { AlignmentChip } from './AlignmentChip';
import { RcdoBreadcrumb } from './RcdoBreadcrumb';
import { OutcomePill } from './OutcomePill';
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
 * The EARNED left-accent for a commitment card (Cadence `.wc-card--accent-*`,
 * ST.4): disputed → failure (red) takes precedence over unplanned → accent
 * (violet) — a dispute is the higher-priority signal (9.11a). Keyed on the
 * `dispute` nest's presence, NOT `alignmentStatus=MISALIGNED` (distinct concept;
 * already a RiskBadge). Returns '' when no accent is earned.
 */
function cardAccent(c: WeeklyCommitmentDto): string {
  if (c.dispute) {
    return 'border-l-2 border-l-tone-failure-solid';
  }
  if (c.commitmentKind === 'UNPLANNED') {
    return 'border-l-2 border-l-tone-accent-solid';
  }
  return '';
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
  // RECONCILED = the plan's read-only terminal — mute the card (ST.5b).
  const reconciled = planState === 'RECONCILED';
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
            data-readonly={reconciled ? 'true' : undefined}
            className={`flex flex-col gap-2 rounded-lg border border-border bg-surface px-4 py-3 shadow-hairline ${cardAccent(c)}${reconciled ? ' opacity-75' : ''}`}
          >
            <div className="flex items-center justify-between gap-3">
              <div className="flex min-w-0 flex-wrap items-center gap-2">
                {/* Lock glyph once the baseline is committed (planState ≠ DRAFT),
                    §3 baseline-immutability surfaced visually (server-authoritative). */}
                {!draft ? (
                  <HiLockClosed
                    data-cy="lock-glyph"
                    aria-hidden
                    className="h-3.5 w-3.5 flex-none text-ink-muted"
                  />
                ) : null}
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
                {/* Reconciliation outcome pill (ST.5b) — shown once recorded
                    (read-only on the RECONCILED card). */}
                {c.reconciliationOutcome ? (
                  <OutcomePill value={c.reconciliationOutcome} />
                ) : null}
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
            {/* Read-only RC→DO→SO breadcrumb (ST.5b) — DO › SO when linked, or
                the missing-SO warning when unlinked. */}
            <RcdoBreadcrumb breadcrumb={c.supportingOutcomeBreadcrumb} />
            {/* Chess-layer atoms (ST.3) — priority / workType / confidence /
                alignment, skinned per the Cadence enum→tone maps. */}
            <div className="flex flex-wrap items-center gap-2">
              <PriorityTag value={c.priority} />
              {/* Suppress the WorkTypeTag for UNPLANNED — the accent kind-badge
                  above already carries "Unplanned" (ST.5a redundancy fix). */}
              {c.workType !== 'UNPLANNED' ? (
                <WorkTypeTag value={c.workType} />
              ) : null}
              <ConfidenceMeter value={c.confidence} />
              <AlignmentChip value={c.alignmentStatus} />
            </div>
            {/* Reconciliation note (ST.5b) — React-escaped free text when present. */}
            {c.outcomeNote ? (
              <p
                data-cy="outcome-note"
                className="text-meta text-ink-secondary"
              >
                {c.outcomeNote}
              </p>
            ) : null}
            {showOutcomeForm ? (
              <ReconciliationOutcomeForm commitment={c} planId={planId} />
            ) : null}
            {/* Alignment-dispute surface (9.11a) — self-gated: the open form,
                the lifecycle stepper + display, and the respond/resolve controls
                all render off the server's dispute + allowedActions (dormant
                until backend 5.5b emits the affordances). */}
            <DisputePanel commitment={c} />
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
