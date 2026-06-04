import { HiClock, HiFlag, HiCheckCircle } from 'react-icons/hi';
import { summarizeRows } from './commandCenterRow';
import type { ManagerCommandCenterRowDto } from '../../shared/lib/dtos';

/**
 * The "At a glance" summary strip (§B.1, D-1). Client-computed from the loaded
 * command-center rows via the pure `summarizeRows` (in `commandCenterRow.ts`).
 */
export function CommandCenterSummary({
  rows,
}: {
  rows: ManagerCommandCenterRowDto[];
}) {
  const g = summarizeRows(rows);
  return (
    <div
      data-cy="cc-glance"
      className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-md border border-border bg-surface-raised px-4 py-2 text-meta text-ink-secondary"
    >
      <span className="font-semibold uppercase tracking-wide text-ink-muted">
        At a glance
      </span>
      <span className="text-ink-primary">{`${g.reports} reports`}</span>
      <span className="inline-flex items-center gap-1 text-tone-failure-fg">
        <HiClock aria-hidden className="h-3 w-3" />
        {`${g.overdue} review overdue`}
      </span>
      <span className="inline-flex items-center gap-1 text-tone-warning-fg">
        <HiFlag aria-hidden className="h-3 w-3" />
        {`${g.disputes} open disputes`}
      </span>
      <span>{`${g.reconciling} reconciling`}</span>
      <span>{`${g.notLocked} not locked`}</span>
      <span className="inline-flex items-center gap-1 text-tone-success-fg">
        <HiCheckCircle aria-hidden className="h-3 w-3" />
        {`${g.reviewedClean} reviewed clean`}
      </span>
    </div>
  );
}
