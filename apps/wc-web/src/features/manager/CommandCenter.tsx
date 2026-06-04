import { useState } from 'react';
import type { IconType } from 'react-icons';
import { HiFlag, HiX } from 'react-icons/hi';
import { Drawer } from 'flowbite-react';
import {
  useGetCommandCenterQuery,
  type CommandCenterParams,
} from './managerApi';
import { CommandCenterFilters } from './CommandCenterFilters';
import { useGetPlanByIdQuery } from '../plan/plansApi';
import { MarkReviewedAction } from '../review/MarkReviewedAction';
import { CommitmentList } from '../commitment/CommitmentList';
import { LoadingState } from '../../shared/components/LoadingState';
import { ErrorState } from '../../shared/components/ErrorState';
import { EmptyState } from '../../shared/components/EmptyState';
import { StatusBadge } from '../../shared/components/StatusBadge';
import { Pagination } from '../../shared/components/Pagination';
import { WeekRangeLabel } from '../../shared/components/WeekRangeLabel';
import { RISK_TAXONOMY, type Tone } from '../../shared/lib/statusTaxonomy';
import type { ManagerCommandCenterRowDto } from '../../shared/lib/dtos';

/**
 * Alignment-count pill metadata (ST.6b). The four risk counts reuse the §7 RISK
 * taxonomy tones/icons VERBATIM (single-source; never re-mapped here). The dispute
 * count has no RISK entry → pinned to failure + a distinct flag glyph
 * (accent=UNPLANNED in this app, so failure not accent).
 */
type CountMeta = { tone: Tone; icon: IconType };
const DISPUTE_META: CountMeta = { tone: 'failure', icon: HiFlag };
const COUNT_RISK_KEY: Record<string, string> = {
  misaligned: 'MISALIGNED',
  needsReview: 'NEEDS_REVIEW',
  blocked: 'BLOCKED',
  carryForward: 'CARRY_FORWARD',
};

function countMeta(kind: string): CountMeta {
  const riskKey = COUNT_RISK_KEY[kind];
  const entry = riskKey ? RISK_TAXONOMY[riskKey] : undefined;
  return entry ? { tone: entry.tone, icon: entry.icon } : DISPUTE_META;
}

// Full literal token-utility strings (Tailwind JIT).
const TONE_PILL: Record<Tone, string> = {
  neutral: 'border-tone-neutral-border bg-tone-neutral-bg text-tone-neutral-fg',
  info: 'border-tone-info-border bg-tone-info-bg text-tone-info-fg',
  success: 'border-tone-success-border bg-tone-success-bg text-tone-success-fg',
  warning: 'border-tone-warning-border bg-tone-warning-bg text-tone-warning-fg',
  failure: 'border-tone-failure-border bg-tone-failure-bg text-tone-failure-fg',
  accent: 'border-tone-accent-border bg-tone-accent-bg text-tone-accent-fg',
};

/** ISO date (yyyy-mm-dd) of the current week's Monday — the default `weekStart`. */
function currentWeekStartIso(): string {
  const now = new Date();
  const isoDow = (now.getUTCDay() + 6) % 7; // 0 = Monday
  const monday = new Date(
    Date.UTC(
      now.getUTCFullYear(),
      now.getUTCMonth(),
      now.getUTCDate() - isoDow,
    ),
  );
  return monday.toISOString().slice(0, 10);
}

/**
 * The per-row review + plan-detail surface (9.9 + 9.14). B.11 rows carry no
 * `reviewId`/`allowedActions[]`, so this lazily fetches the report's plan (E4 —
 * authorizes the direct manager) to obtain `managerReview` (B.7) + the report's
 * `commitments` with their per-actor `allowedActions`. Renders the §7 partial
 * view-states: LoadingState while pending, an IDOR-safe ErrorState(safeMessage)
 * on a `404` (never a crash/existence leak, §6). The review section
 * (`MarkReviewedAction`, self-gated on `MARK_REVIEWED`) renders FIRST (the
 * primary action), then the report's commitments via the SAME
 * `allowedActions`-gated `CommitmentList` the IC view uses — so the manager's
 * `OPEN_DISPUTE`/`RESOLVE_DISPUTE`/`COMMENT` controls (emitted on E4 per viewing
 * actor by backend 5.5b) light up through the portable `DisputePanel` with zero
 * per-role branching (§11). Read-only: no `onEdit`; IC-authoring controls
 * (edit/delete/reconciliation/carry-forward) gate themselves out (their
 * `allowedActions`/DRAFT state are absent on a manager read). The review section
 * + the commitments render INDEPENDENTLY (a no-review plan still shows the list).
 */
function ManagerRowReview({ planId }: { planId: string }) {
  const { data, isLoading, isError, error } = useGetPlanByIdQuery(planId);
  if (isError) {
    const message =
      (error as { safeMessage?: string } | undefined)?.safeMessage ??
      'This plan is not available.';
    return <ErrorState message={message} />;
  }
  if (isLoading || !data) {
    return <LoadingState variant="inline" delayMs={0} />;
  }
  const review = data.managerReview;
  return (
    <div className="space-y-4">
      {review ? (
        <MarkReviewedAction review={review} />
      ) : (
        <p className="text-meta text-ink-secondary">No review record yet.</p>
      )}
      <CommitmentList
        commitments={data.commitments}
        planState={data.state}
        planId={data.id}
      />
    </div>
  );
}

const COUNTS: {
  kind: string;
  label: string;
  key: keyof ManagerCommandCenterRowDto;
}[] = [
  { kind: 'misaligned', label: 'Misaligned', key: 'misalignedCount' },
  { kind: 'needsReview', label: 'Needs review', key: 'needsReviewCount' },
  { kind: 'blocked', label: 'Blocked', key: 'blockedCount' },
  { kind: 'carryForward', label: 'Carry-forward', key: 'carryForwardCount' },
  {
    kind: 'unresolvedDispute',
    label: 'Disputes',
    key: 'unresolvedDisputeCount',
  },
];

/**
 * The manager direct-report command center (E13). Renders each report's plan/
 * review state + alignment counts as at-a-glance actionable signals WITHOUT
 * opening each plan (REQ-UX-003); explicit loading/empty/error states; server-
 * side pagination via the B.20 envelope (no client-side slicing, REQ-NF-002).
 * Filters drive the E13 params (a filter change resets to page 0). Acting on a
 * review uses a per-row expand (Q2) — see `ManagerRowReview`.
 */
export function CommandCenter() {
  const [params, setParams] = useState<CommandCenterParams>(() => ({
    weekStart: currentWeekStartIso(),
    page: 0,
  }));
  const [expandedPlanId, setExpandedPlanId] = useState<string | null>(null);
  const { data, isLoading, isError, error } = useGetCommandCenterQuery(params);

  if (isError) {
    const message =
      (error as { safeMessage?: string } | undefined)?.safeMessage ??
      'Could not load the command center.';
    return <ErrorState message={message} />;
  }
  if (isLoading || !data) {
    return <LoadingState variant="table" delayMs={0} />;
  }

  const rows = data.content;
  const expandedRow =
    expandedPlanId !== null
      ? rows.find((r) => r.weeklyPlanId === expandedPlanId)
      : undefined;
  return (
    <section
      data-cy="command-center"
      className="mx-auto max-w-content-max space-y-4 p-6"
    >
      <header>
        <h1 className="text-h2 font-semibold text-ink-primary">
          Command center
        </h1>
        <p className="text-meta text-ink-secondary">
          Direct-report alignment at a glance.
        </p>
      </header>

      <CommandCenterFilters
        value={params}
        onChange={(patch) =>
          setParams((p) => ({ ...p, ...patch, page: patch.page ?? 0 }))
        }
      />

      {rows.length === 0 ? (
        <EmptyState
          title="No direct reports to review"
          message="No reports match these filters for the selected week."
        />
      ) : (
        <table data-cy="cc-table" className="w-full text-left">
          <thead className="sticky top-0 z-10 bg-surface">
            <tr className="text-meta text-ink-secondary">
              <th className="py-2">Report</th>
              <th className="py-2">Plan</th>
              <th className="py-2">Review</th>
              <th className="py-2">Alignment</th>
              <th className="py-2" />
            </tr>
          </thead>
          <tbody>
            {rows.map((r, i) => {
              // Zebra striping by data-row index (NOT CSS nth-child — kept stable
              // and explicit; the review surface is now an overlay Drawer, not an
              // interleaved detail row).
              const parity = i % 2 === 0 ? 'even' : 'odd';
              return (
                <tr
                  key={r.employeeId}
                  data-cy="cc-row"
                  data-row-parity={parity}
                  className={`border-t border-border align-top hover:bg-surface-hover ${parity === 'odd' ? 'bg-surface-raised' : ''}`}
                >
                  <td className="py-3 text-body text-ink-primary">
                    {r.employeeDisplayName}
                  </td>
                  <td className="py-3">
                    <StatusBadge kind="plan" value={r.planState} />
                  </td>
                  <td className="py-3">
                    {r.reviewStatus ? (
                      <StatusBadge
                        kind="review"
                        value={r.reviewStatus}
                        derivedOverdue={r.isReviewOverdue}
                      />
                    ) : (
                      <span className="text-meta text-ink-muted">—</span>
                    )}
                  </td>
                  <td className="py-3">
                    <div className="flex flex-wrap gap-1.5">
                      {COUNTS.map((c) => {
                        const meta = countMeta(c.kind);
                        const n = r[c.key] as number;
                        const Icon = meta.icon;
                        return (
                          <span
                            key={c.kind}
                            data-cy={`cc-${c.kind}`}
                            data-tone={meta.tone}
                            title={`${c.label}: ${n}`}
                            aria-label={`${c.label}: ${n}`}
                            className={`inline-flex items-center gap-1 rounded-md border px-1.5 py-0.5 font-mono text-meta ${n === 0 ? 'border-border bg-transparent text-ink-muted' : TONE_PILL[meta.tone]}`}
                          >
                            <Icon aria-hidden className="h-3 w-3" />
                            {n}
                          </span>
                        );
                      })}
                    </div>
                  </td>
                  <td className="py-3">
                    {r.weeklyPlanId ? (
                      <button
                        type="button"
                        onClick={() =>
                          setExpandedPlanId(r.weeklyPlanId ?? null)
                        }
                        className="rounded-md border border-border-strong bg-surface-raised px-3 py-1 text-label text-ink-primary hover:bg-surface-hover focus:outline-none focus:ring-2 focus:ring-brand-ring"
                      >
                        Review
                      </button>
                    ) : null}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}

      <Pagination
        page={data.page}
        onPageChange={(n) => setParams((p) => ({ ...p, page: n }))}
      />

      {/*
       * The per-row review surface opens in a themed Flowbite Drawer (right-slide
       * + scrim, raised surface — ST.6c). The Drawer always renders its children
       * (open = off-screen translate), so the review body is mounted ONLY while a
       * row is expanded — preserving the lazy `getPlanById` fetch (skip-until-
       * open). Server-authoritative gating / view-states are unchanged: they now
       * render inside the Drawer body via `ManagerRowReview`.
       */}
      <Drawer
        open={expandedPlanId !== null}
        onClose={() => setExpandedPlanId(null)}
        position="right"
        data-cy="review-drawer"
      >
        {expandedPlanId !== null && expandedRow ? (
          <>
            <div className="flex items-start justify-between gap-4 border-b border-border px-4 py-3">
              <div>
                <h2 className="text-h3 font-semibold text-ink-primary">
                  {expandedRow.employeeDisplayName}
                </h2>
                <WeekRangeLabel weekStart={expandedRow.weekStartDate} />
              </div>
              <button
                type="button"
                aria-label="Close"
                onClick={() => setExpandedPlanId(null)}
                className="rounded-md p-1 text-ink-secondary hover:bg-surface-hover focus:outline-none focus:ring-2 focus:ring-brand-ring"
              >
                <HiX aria-hidden className="h-5 w-5" />
              </button>
            </div>
            <Drawer.Items className="p-4">
              <ManagerRowReview planId={expandedPlanId} />
            </Drawer.Items>
          </>
        ) : null}
      </Drawer>
    </section>
  );
}
