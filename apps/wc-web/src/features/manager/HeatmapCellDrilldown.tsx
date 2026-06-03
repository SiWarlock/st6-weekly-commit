import { useState, type ReactNode } from 'react';
import { useGetHeatmapDrilldownQuery } from './managerApi';
import { LoadingState } from '../../shared/components/LoadingState';
import { ErrorState } from '../../shared/components/ErrorState';
import { EmptyState } from '../../shared/components/EmptyState';
import { Pagination, type PageInfo } from '../../shared/components/Pagination';

function DrilldownPanel({ children }: { children: ReactNode }) {
  return (
    <section
      data-cy="heatmap-drilldown"
      className="mt-4 space-y-3 rounded-lg border border-border bg-surface p-4"
    >
      {children}
    </section>
  );
}

/**
 * The E15 cell drilldown — the Supporting-Outcome → commitment breakdown
 * explaining why a heatmap cell is risky (REQ-UX-003). Bounded + paginated
 * (B.20, §14). The single `page` param paginates EVERY group together, so the
 * pager reflects the **max `totalPages` across groups** (driving it off the first
 * group alone would hide a deeper group's commitments). A `404` (cell not the
 * manager's own) renders `ErrorState(safeMessage)` — IDOR-safe, never a leak (§6).
 */
export function HeatmapCellDrilldown({ cellId }: { cellId: string }) {
  const [page, setPage] = useState(0);
  const { data, isLoading, isError, error } = useGetHeatmapDrilldownQuery({
    cellId,
    page,
  });

  if (isError) {
    const message =
      (error as { safeMessage?: string } | undefined)?.safeMessage ??
      'That cell is not available.';
    return (
      <DrilldownPanel>
        <ErrorState message={message} />
      </DrilldownPanel>
    );
  }
  if (isLoading || !data) {
    return (
      <DrilldownPanel>
        <LoadingState variant="cards" delayMs={0} />
      </DrilldownPanel>
    );
  }

  const groups = data.supportingOutcomes;
  if (groups.length === 0) {
    return (
      <DrilldownPanel>
        <EmptyState
          title="No commitments"
          message="This cell has no commitments to explain."
        />
      </DrilldownPanel>
    );
  }

  // The pager spans every group (one E15 page param) → use the max across groups
  // so a deeper group's later commitments stay reachable, not hidden.
  const totalPages = Math.max(
    ...groups.map((g) => g.commitments.page.totalPages),
  );
  const size = groups[0]?.commitments.page.size ?? 25;
  const totalElements = groups.reduce(
    (sum, g) => sum + g.commitments.page.totalElements,
    0,
  );
  const pageInfo: PageInfo = { number: page, size, totalElements, totalPages };

  return (
    <DrilldownPanel>
      {groups.map((g) => (
        <div key={g.supportingOutcomeId} data-cy="drilldown-outcome-group">
          <h4 className="text-label font-semibold text-ink-primary">
            {g.supportingOutcomeTitle}
          </h4>
          {g.commitments.content.length === 0 ? (
            <p className="text-meta text-ink-muted">
              No commitments on this page.
            </p>
          ) : (
            <ul className="mt-1 space-y-1">
              {g.commitments.content.map((c) => (
                <li key={c.id} className="text-body text-ink-secondary">
                  {c.title}
                </li>
              ))}
            </ul>
          )}
        </div>
      ))}
      <Pagination page={pageInfo} onPageChange={setPage} />
    </DrilldownPanel>
  );
}
