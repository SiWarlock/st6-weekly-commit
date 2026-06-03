import { useState } from 'react';
import { useGetHeatmapQuery } from './managerApi';
import { HeatmapCellDrilldown } from './HeatmapCellDrilldown';
import { RiskBadge } from '../../shared/components/RiskBadge';
import { LoadingState } from '../../shared/components/LoadingState';
import { ErrorState } from '../../shared/components/ErrorState';
import { EmptyState } from '../../shared/components/EmptyState';
import type { HeatmapCellDto } from '../../shared/lib/dtos';

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

interface Axis {
  id: string;
  label: string;
}

interface Pivot {
  reports: Axis[];
  objectives: Axis[];
  cellAt: (
    employeeId: string,
    definingObjectiveId: string,
  ) => HeatmapCellDto | undefined;
}

/** Pivot the flat `cells[]` into a report(row) × Defining-Objective(col) matrix. */
function pivot(cells: HeatmapCellDto[]): Pivot {
  const reports: Axis[] = [];
  const objectives: Axis[] = [];
  const seenReport = new Set<string>();
  const seenObjective = new Set<string>();
  const index = new Map<string, HeatmapCellDto>();
  for (const c of cells) {
    if (!seenReport.has(c.employeeId)) {
      seenReport.add(c.employeeId);
      reports.push({ id: c.employeeId, label: c.employeeDisplayName });
    }
    if (!seenObjective.has(c.definingObjectiveId)) {
      seenObjective.add(c.definingObjectiveId);
      objectives.push({
        id: c.definingObjectiveId,
        label: c.definingObjectiveTitle,
      });
    }
    index.set(`${c.employeeId}|${c.definingObjectiveId}`, c);
  }
  return {
    reports,
    objectives,
    cellAt: (e, d) => index.get(`${e}|${d}`),
  };
}

/**
 * Neutral load intensity per cell (ST.6a) — the Cadence `volMeta()` breakpoints,
 * **decoupled from risk** (risk is the badges; this is the cell background).
 * 0 → none (transparent gap), 1 → light, 2–3 → normal, ≥4 → heavy.
 */
type Volume = 'none' | 'light' | 'normal' | 'heavy';

function cellVolume(count: number): Volume {
  if (count <= 0) return 'none';
  if (count === 1) return 'light';
  if (count <= 3) return 'normal';
  return 'heavy';
}

const VOLUME_BG: Record<Volume, string> = {
  none: 'bg-transparent',
  light: 'bg-vol-light',
  normal: 'bg-vol-normal',
  heavy: 'bg-vol-heavy',
};

/**
 * The manager heatmap (E14) — a report × Defining-Objective grid of alignment
 * counts + enumerated `riskBadges[]` (via `RiskBadge`, glyph+text+color, no opaque
 * score; unknown → nothing, LESSONS §7). Explicit loading/empty/error states.
 * Selecting a cell opens the E15 drilldown (skip-until-selected). Visual polish
 * (color intensity, layout) is the ST.6 design pass; this is the deterministic
 * scaffold (the `cells[]`→matrix pivot + count/badge rendering).
 */
export function HeatmapGrid() {
  const [weekStart] = useState(currentWeekStartIso);
  const [selectedCellId, setSelectedCellId] = useState<string | null>(null);
  const { data, isLoading, isError, error } = useGetHeatmapQuery({ weekStart });

  if (isError) {
    const message =
      (error as { safeMessage?: string } | undefined)?.safeMessage ??
      'Could not load the heatmap.';
    return <ErrorState message={message} />;
  }
  if (isLoading || !data) {
    return <LoadingState variant="table" delayMs={0} />;
  }

  const { reports, objectives, cellAt } = pivot(data.cells);

  return (
    <section
      data-cy="heatmap-grid"
      className="mx-auto max-w-content-max space-y-4 p-6"
    >
      <header>
        <h1 className="text-h2 font-semibold text-ink-primary">
          Alignment heatmap
        </h1>
        <p className="text-meta text-ink-secondary">
          Risk by report and Defining Objective.
        </p>
      </header>

      {data.cells.length === 0 ? (
        <EmptyState
          title="No heatmap cells"
          message="No alignment cells for your reports this week."
        />
      ) : (
        <table data-cy="heatmap-table" className="w-full text-left">
          <thead>
            <tr className="text-meta text-ink-secondary">
              <th className="py-2" scope="col" />
              {objectives.map((o) => (
                <th key={o.id} scope="col" className="py-2">
                  {o.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {reports.map((r) => (
              <tr key={r.id} className="border-t border-border">
                <th
                  scope="row"
                  className="py-3 text-body font-medium text-ink-primary"
                >
                  {r.label}
                </th>
                {objectives.map((o) => {
                  const cell = cellAt(r.id, o.id);
                  // Neutral volume-fill keyed on load (ST.6a), decoupled from risk.
                  const vol: Volume = cell
                    ? cellVolume(cell.commitmentCount)
                    : 'none';
                  return (
                    <td key={o.id} data-cy="heatmap-cell" className="py-3">
                      {cell ? (
                        <button
                          type="button"
                          aria-label={`${r.label} — ${o.label}`}
                          data-volume={vol}
                          onClick={() => setSelectedCellId(cell.cellId)}
                          className={`flex w-full flex-col items-start gap-1 rounded-md border border-border ${VOLUME_BG[vol]} px-3 py-2 text-left hover:bg-surface-hover focus:outline-none focus:ring-2 focus:ring-brand-ring`}
                        >
                          <span
                            data-cy="cell-commitmentCount"
                            className="text-body font-semibold text-ink-primary"
                          >
                            {cell.commitmentCount}
                          </span>
                          {cell.riskBadges.length > 0 ? (
                            <span className="flex flex-wrap gap-1">
                              {cell.riskBadges.map((b, i) => (
                                <RiskBadge key={`${b}-${i}`} value={b} />
                              ))}
                            </span>
                          ) : null}
                        </button>
                      ) : (
                        <span className="text-meta text-ink-muted">—</span>
                      )}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {selectedCellId ? <HeatmapCellDrilldown cellId={selectedCellId} /> : null}
    </section>
  );
}
