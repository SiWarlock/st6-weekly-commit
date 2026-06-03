import { Fragment, useState } from 'react';
import {
  useGetCommandCenterQuery,
  type CommandCenterParams,
} from './managerApi';
import { CommandCenterFilters } from './CommandCenterFilters';
import { useGetPlanByIdQuery } from '../plan/plansApi';
import { MarkReviewedAction } from '../review/MarkReviewedAction';
import { LoadingState } from '../../shared/components/LoadingState';
import { ErrorState } from '../../shared/components/ErrorState';
import { EmptyState } from '../../shared/components/EmptyState';
import { StatusBadge } from '../../shared/components/StatusBadge';
import { Pagination } from '../../shared/components/Pagination';
import type { ManagerCommandCenterRowDto } from '../../shared/lib/dtos';

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
 * The per-row review surface (Q2 wiring). B.11 rows carry no `reviewId`/
 * `allowedActions[]`, so acting on a review lazily fetches the report's plan
 * (E4 — authorizes the direct manager) to obtain `managerReview` (B.7) + its
 * `allowedActions`. Renders the §7 partial view-states: LoadingState while
 * pending, an IDOR-safe ErrorState(safeMessage) on a `404` (never a crash or
 * existence leak, §6). `MarkReviewedAction` self-gates on `MARK_REVIEWED`.
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
  if (!review) {
    return (
      <p className="text-meta text-ink-secondary">No review record yet.</p>
    );
  }
  return <MarkReviewedAction review={review} />;
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
  return (
    <section
      data-cy="command-center"
      className="mx-auto max-w-6xl space-y-4 p-6"
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
          <thead>
            <tr className="text-meta text-ink-secondary">
              <th className="py-2">Report</th>
              <th className="py-2">Plan</th>
              <th className="py-2">Review</th>
              <th className="py-2">Alignment</th>
              <th className="py-2" />
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => {
              const expanded =
                expandedPlanId !== null && expandedPlanId === r.weeklyPlanId;
              return (
                <Fragment key={r.employeeId}>
                  <tr
                    data-cy="cc-row"
                    className="border-t border-border align-top"
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
                      <div className="flex flex-wrap gap-2 text-meta text-ink-secondary">
                        {COUNTS.map((c) => (
                          <span key={c.kind}>
                            {c.label}:{' '}
                            <span
                              data-cy={`cc-${c.kind}`}
                              className="font-semibold text-ink-primary"
                            >
                              {r[c.key]}
                            </span>
                          </span>
                        ))}
                      </div>
                    </td>
                    <td className="py-3">
                      {r.weeklyPlanId ? (
                        <button
                          type="button"
                          onClick={() =>
                            setExpandedPlanId(
                              expanded ? null : (r.weeklyPlanId ?? null),
                            )
                          }
                          className="rounded-md border border-border-strong bg-surface-raised px-3 py-1 text-label text-ink-primary hover:bg-surface-hover focus:outline-none focus:ring-2 focus:ring-brand-ring"
                        >
                          Review
                        </button>
                      ) : null}
                    </td>
                  </tr>
                  {expanded && r.weeklyPlanId ? (
                    <tr
                      data-cy="cc-row-detail"
                      className="border-t border-border"
                    >
                      <td colSpan={5} className="py-3">
                        <ManagerRowReview planId={r.weeklyPlanId} />
                      </td>
                    </tr>
                  ) : null}
                </Fragment>
              );
            })}
          </tbody>
        </table>
      )}

      <Pagination
        page={data.page}
        onPageChange={(n) => setParams((p) => ({ ...p, page: n }))}
      />
    </section>
  );
}
