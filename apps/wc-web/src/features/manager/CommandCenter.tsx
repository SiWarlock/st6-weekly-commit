import { useState } from 'react';
import { HiX, HiChevronLeft, HiChevronRight, HiRefresh } from 'react-icons/hi';
import { Drawer } from 'flowbite-react';
import {
  useGetCommandCenterQuery,
  type CommandCenterParams,
} from './managerApi';
import { CommandCenterFilters } from './CommandCenterFilters';
import { CommandCenterSummary } from './CommandCenterSummary';
import { RiskChips } from './RiskChips';
import { reviewSubline, reconcileText, actionLabel } from './commandCenterRow';
import { useGetPlanByIdQuery } from '../plan/plansApi';
import { MarkReviewedAction } from '../review/MarkReviewedAction';
import { CommitmentList } from '../commitment/CommitmentList';
import { LoadingState } from '../../shared/components/LoadingState';
import { ErrorState } from '../../shared/components/ErrorState';
import { EmptyState } from '../../shared/components/EmptyState';
import { StatusBadge } from '../../shared/components/StatusBadge';
import { Avatar } from '../../shared/components/Avatar';
import { Pagination } from '../../shared/components/Pagination';
import { WeekRangeLabel } from '../../shared/components/WeekRangeLabel';
import { formatWeekRange } from '../../shared/lib/formatWeek';

/** ISO date (yyyy-mm-dd) of the current week's Monday — the default `weekStart`. */
function currentWeekStartIso(): string {
  const now = new Date();
  const isoDow = (now.getUTCDay() + 6) % 7; // 0 = Monday
  const monday = new Date(
    Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - isoDow),
  );
  return monday.toISOString().slice(0, 10);
}

/** Format the plan lock time as "Jun 1, 3:00 PM" (UTC — deterministic, canon dhead). */
function formatLockedAt(iso: string): string {
  return new Intl.DateTimeFormat('en-US', {
    timeZone: 'UTC',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(iso));
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
 * `OPEN_DISPUTE`/`RESOLVE_DISPUTE`/`COMMENT` controls light up through the
 * portable `DisputePanel` with zero per-role branching (§11).
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
      {/* Lock-timestamp (canon dhead) — plan-sourced (B.5 lockedAt), so it lives
          at the body top; the row-sourced avatar + pills are in the Drawer header. */}
      {data.lockedAt ? (
        <p className="text-meta text-ink-muted">
          Locked {formatLockedAt(data.lockedAt)}
        </p>
      ) : null}
      {review ? (
        <>
          <MarkReviewedAction review={review} />
          <p className="text-meta text-ink-muted">
            Status is derived from the unresolved-dispute count.
          </p>
        </>
      ) : (
        <p className="text-meta text-ink-secondary">No review record yet.</p>
      )}
      <p className="text-meta uppercase tracking-wide text-ink-muted">
        Commitments ({data.plannedCount} planned)
      </p>
      <CommitmentList
        commitments={data.commitments}
        planState={data.state}
        planId={data.id}
      />
    </div>
  );
}

/**
 * The manager direct-report command center (E13). Renders each report's plan/
 * review state + alignment counts as at-a-glance actionable signals WITHOUT
 * opening each plan (REQ-UX-003); explicit loading/empty/error states; server-
 * side pagination via the B.20 envelope (no client-side slicing, REQ-NF-002).
 * ST.8b brings it to mockup fidelity: the client-computed "At a glance" strip
 * (D-1), labeled hide-zero risk chips + report avatars + review-timestamp
 * sub-lines + a RECONCILE column + a (static) week pager with a functional
 * refresh + section caption/footer legend + the Review/Open action. Filters drive
 * the E13 params (a filter change resets to page 0). Acting on a review uses a
 * per-row expand (Q2) — see `ManagerRowReview`.
 */
export function CommandCenter() {
  const [params, setParams] = useState<CommandCenterParams>(() => ({
    weekStart: currentWeekStartIso(),
    page: 0,
  }));
  const [expandedPlanId, setExpandedPlanId] = useState<string | null>(null);
  const { data, isLoading, isError, error, refetch } =
    useGetCommandCenterQuery(params);

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
  const weekLabel = formatWeekRange(params.weekStart);
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
          Alignment Command Center
        </h1>
        <p className="text-meta text-ink-secondary">
          Your direct reports&apos; weekly alignment at a glance.
        </p>
      </header>

      {/* Week-range pager (static demo affordance) + functional refresh. */}
      <div
        data-cy="cc-week-controls"
        className="flex items-center gap-2 text-label text-ink-secondary"
      >
        <button
          type="button"
          disabled
          aria-label="Previous week"
          className="inline-flex items-center rounded-md border border-border px-1.5 py-1 text-ink-muted"
        >
          <HiChevronLeft aria-hidden className="h-4 w-4" />
        </button>
        <span className="text-ink-primary">{weekLabel}</span>
        <button
          type="button"
          disabled
          aria-label="Next week"
          className="inline-flex items-center rounded-md border border-border px-1.5 py-1 text-ink-muted"
        >
          <HiChevronRight aria-hidden className="h-4 w-4" />
        </button>
        <span className="ml-auto text-meta text-ink-muted">Updated just now</span>
        <button
          type="button"
          aria-label="Refresh"
          onClick={() => refetch()}
          className="inline-flex items-center rounded-md border border-border-strong bg-surface-raised px-2 py-1 text-ink-secondary hover:bg-surface-hover hover:text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
        >
          <HiRefresh aria-hidden className="h-4 w-4" />
        </button>
      </div>

      <CommandCenterSummary rows={rows} />

      <CommandCenterFilters
        value={params}
        onChange={(patch) =>
          setParams((p) => ({ ...p, ...patch, page: patch.page ?? 0 }))
        }
        reports={rows.map((r) => ({
          id: r.employeeId,
          name: r.employeeDisplayName,
        }))}
        shown={rows.length}
        total={data.page.totalElements}
      />

      {rows.length === 0 ? (
        <EmptyState
          title="No direct reports to review"
          message="No reports match these filters for the selected week."
        />
      ) : (
        <div className="space-y-2">
          <div className="flex items-center gap-2 text-meta uppercase tracking-wide text-ink-muted">
            <span>Direct reports — {weekLabel}</span>
            <span className="ml-auto normal-case tracking-normal">
              Sort: Week ▼ · Name ▲
            </span>
          </div>
          <table data-cy="cc-table" className="w-full text-left">
            <thead className="sticky top-0 z-10 bg-surface">
              <tr className="text-meta text-ink-secondary">
                <th className="py-2">Report</th>
                <th className="py-2">Plan state</th>
                <th className="py-2">Review status</th>
                <th className="py-2">Reconcile</th>
                <th className="py-2">Risk chips</th>
                <th className="py-2" />
              </tr>
            </thead>
            <tbody>
              {rows.map((r, i) => {
                // Zebra striping by data-row index (NOT CSS nth-child — kept
                // stable and explicit; the review surface is an overlay Drawer).
                const parity = i % 2 === 0 ? 'even' : 'odd';
                return (
                  <tr
                    key={r.employeeId}
                    data-cy="cc-row"
                    data-row-parity={parity}
                    className={`border-t border-border align-top hover:bg-surface-hover ${parity === 'odd' ? 'bg-surface-raised' : ''}`}
                  >
                    <td className="py-3">
                      <div className="flex items-center gap-2">
                        <Avatar name={r.employeeDisplayName} />
                        <span className="text-body text-ink-primary">
                          {r.employeeDisplayName}
                        </span>
                      </div>
                    </td>
                    <td className="py-3">
                      <StatusBadge kind="plan" value={r.planState} />
                    </td>
                    <td className="py-3">
                      {r.reviewStatus ? (
                        <div className="space-y-0.5">
                          <StatusBadge
                            kind="review"
                            value={r.reviewStatus}
                            derivedOverdue={r.isReviewOverdue}
                          />
                          <div
                            data-cy="cc-review-subline"
                            className="text-meta text-ink-muted"
                          >
                            {reviewSubline(r)}
                          </div>
                        </div>
                      ) : (
                        <span className="text-meta text-ink-muted">—</span>
                      )}
                    </td>
                    <td
                      data-cy="cc-reconcile"
                      className="py-3 text-meta text-ink-secondary"
                    >
                      {reconcileText(r)}
                    </td>
                    <td className="py-3">
                      <RiskChips row={r} />
                    </td>
                    <td className="py-3">
                      {r.weeklyPlanId ? (
                        <button
                          type="button"
                          data-cy="cc-row-action"
                          onClick={() =>
                            setExpandedPlanId(r.weeklyPlanId ?? null)
                          }
                          className="inline-flex items-center gap-1 rounded-md border border-border-strong bg-surface-raised px-3 py-1 text-label text-ink-primary hover:bg-surface-hover focus:outline-none focus:ring-2 focus:ring-brand-ring"
                        >
                          {actionLabel(r)}
                          <HiChevronRight aria-hidden className="h-4 w-4" />
                        </button>
                      ) : null}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <div className="flex items-center gap-2 text-meta text-ink-muted">
            <span>P = planned · U = unplanned</span>
            <span className="ml-auto">25 / page</span>
          </div>
        </div>
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
       * open). Server-authoritative gating / view-states are unchanged.
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
              <div className="flex items-center gap-3">
                <Avatar name={expandedRow.employeeDisplayName} />
                <div>
                  <h2 className="text-h3 font-semibold text-ink-primary">
                    {expandedRow.employeeDisplayName}
                  </h2>
                  <div className="flex flex-wrap items-center gap-2 text-meta text-ink-secondary">
                    <WeekRangeLabel weekStart={expandedRow.weekStartDate} />
                    <StatusBadge kind="plan" value={expandedRow.planState} />
                    {expandedRow.reviewStatus ? (
                      <StatusBadge
                        kind="review"
                        value={expandedRow.reviewStatus}
                        derivedOverdue={expandedRow.isReviewOverdue}
                      />
                    ) : null}
                  </div>
                </div>
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
