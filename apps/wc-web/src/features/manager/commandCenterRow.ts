import type { ManagerCommandCenterRowDto } from '../../shared/lib/dtos';

/**
 * ST.8b — pure per-row derivations for the command-center table (§B.5 review
 * sub-line, §B.6 reconcile column, §B.9 action label). All deterministic over the
 * existing B.11 row fields — no new endpoint. The table render wires these; the
 * functions are the testable core.
 */

/** Format an ISO timestamp as "Jun 2, 5:00 PM" (UTC — deterministic). */
function formatDateTime(iso: string): string {
  return new Intl.DateTimeFormat('en-US', {
    timeZone: 'UTC',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(iso));
}

/**
 * The review-status sub-line (§B.5). NOT_REVIEWED → "Due …"/"Was due …" from
 * `reviewDueAt` + the derived `isReviewOverdue`. REVIEWED(_WITH_DISPUTES) → just
 * "Reviewed" — B.11 carries no `reviewedAt` (it lives on B.7), so the reviewed-time
 * sub-line is a flagged backend follow-up. No review record → "" (the cell shows "—").
 */
export function reviewSubline(row: ManagerCommandCenterRowDto): string {
  if (!row.reviewStatus) {
    return '';
  }
  if (
    row.reviewStatus === 'REVIEWED' ||
    row.reviewStatus === 'REVIEWED_WITH_DISPUTES'
  ) {
    return 'Reviewed';
  }
  // NOT_REVIEWED.
  if (!row.reviewDueAt) {
    return '';
  }
  return row.isReviewOverdue
    ? `Was due ${formatDateTime(row.reviewDueAt)}`
    : `Due ${formatDateTime(row.reviewDueAt)}`;
}

/** The RECONCILE column text (§B.6) — only RECONCILING plans show progress. */
export function reconcileText(row: ManagerCommandCenterRowDto): string {
  if (row.planState !== 'RECONCILING') {
    return '—';
  }
  return row.carryForwardCount > 0
    ? `In progress · ${row.carryForwardCount} carry-fwd`
    : 'In progress';
}

/** The row action label (§B.9) — "Open" once reviewed, else "Review". */
export function actionLabel(
  row: ManagerCommandCenterRowDto,
): 'Review' | 'Open' {
  return row.reviewStatus === 'REVIEWED' ||
    row.reviewStatus === 'REVIEWED_WITH_DISPUTES'
    ? 'Open'
    : 'Review';
}

/** The at-a-glance counts (§B.1, D-1) derived client-side from the loaded rows. */
export interface GlanceCounts {
  reports: number;
  overdue: number;
  disputes: number;
  reconciling: number;
  notLocked: number;
  reviewedClean: number;
}

/**
 * Client-side at-a-glance derivation (D-1, lead/user-approved). PURE over the
 * LOADED command-center rows (`data.content`) — not a sliced view. The
 * production-correct path defers to the backend §9 `summary` field (a queued
 * follow-up): with server pagination this rolls up the current page only.
 *
 * `reviewedClean` counts ONLY `REVIEWED` (a `REVIEWED_WITH_DISPUTES` row is NOT
 * clean); `disputes` sums every row's `unresolvedDisputeCount` — so a
 * reviewed-but-disputed plan reads amber (disputes), never green (clean).
 */
export function summarizeRows(
  rows: ManagerCommandCenterRowDto[],
): GlanceCounts {
  return rows.reduce<GlanceCounts>(
    (acc, r) => ({
      reports: acc.reports + 1,
      overdue: acc.overdue + (r.isReviewOverdue ? 1 : 0),
      disputes: acc.disputes + r.unresolvedDisputeCount,
      reconciling: acc.reconciling + (r.planState === 'RECONCILING' ? 1 : 0),
      notLocked: acc.notLocked + (r.planState === 'DRAFT' ? 1 : 0),
      reviewedClean:
        acc.reviewedClean + (r.reviewStatus === 'REVIEWED' ? 1 : 0),
    }),
    {
      reports: 0,
      overdue: 0,
      disputes: 0,
      reconciling: 0,
      notLocked: 0,
      reviewedClean: 0,
    },
  );
}
