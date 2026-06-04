import { useState } from 'react';
import { HiX, HiMinusCircle } from 'react-icons/hi';
import { Drawer } from 'flowbite-react';
import { useGetHeatmapQuery } from './managerApi';
import { useGetRcdoQuery } from '../rcdo/rcdoApi';
import { HeatmapCellDrilldown } from './HeatmapCellDrilldown';
import { RiskBadge } from '../../shared/components/RiskBadge';
import { Avatar } from '../../shared/components/Avatar';
import { LoadingState } from '../../shared/components/LoadingState';
import { ErrorState } from '../../shared/components/ErrorState';
import { EmptyState } from '../../shared/components/EmptyState';
import {
  volBars,
  isNoCoverage,
  rowTotal,
  type RowTotalTone,
} from './heatmapDerive';
import type {
  HeatmapCellDto,
  RiskBadge as RiskBadgeValue,
} from '../../shared/lib/dtos';

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
 * 1 → light, 2–3 → normal, ≥4 → heavy (0 is the no-coverage cell, not a fill).
 */
type Volume = 'light' | 'normal' | 'heavy';

function cellVolume(count: number): Volume {
  if (count === 1) return 'light';
  if (count <= 3) return 'normal';
  return 'heavy';
}

const VOLUME_BG: Record<Volume, string> = {
  light: 'bg-vol-light',
  normal: 'bg-vol-normal',
  heavy: 'bg-vol-heavy',
};

/** Bar heights (canon `BAR_H` [8,11,14]). Three rising bars, filled to `volBars`. */
const BAR_HEIGHTS = ['h-2', 'h-2.5', 'h-3.5'];

/** ST.8e — three rising load bars, filled to `n` (canon `VolBars`); decoupled from risk. */
function VolBars({ n }: { n: number }) {
  return (
    <span aria-hidden className="flex items-end gap-0.5">
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          className={`w-1 rounded-sm ${BAR_HEIGHTS[i]} ${i < n ? 'bg-ink-secondary' : 'bg-border-strong'}`}
        />
      ))}
    </span>
  );
}

/** Row-total badge tone → Cadence tone tokens (canon `rowTotalText`). */
const ROW_TOTAL_TONE: Record<RowTotalTone, string> = {
  neutral: 'bg-tone-neutral-bg text-tone-neutral-fg',
  success: 'bg-tone-success-bg text-tone-success-fg',
  warning: 'bg-tone-warning-bg text-tone-warning-fg',
  failure: 'bg-tone-failure-bg text-tone-failure-fg',
};

/** Legend — the six risk badges + meanings (canon `heat-legend`). */
const RISK_LEGEND: { value: RiskBadgeValue; meaning: string }[] = [
  { value: 'MISALIGNED', meaning: 'strategic conflict' },
  { value: 'BLOCKED', meaning: 'work is blocked' },
  { value: 'OVERDUE_REVIEW', meaning: 'review SLA missed' },
  { value: 'NEEDS_REVIEW', meaning: 'IC self-flag' },
  { value: 'CARRY_FORWARD', meaning: 'unfinished, moved forward' },
  { value: 'UNREVIEWED', meaning: 'locked, not reviewed' },
];

/**
 * The manager heatmap (E14) — a report × Defining-Objective grid of alignment
 * counts + enumerated `riskBadges[]` (via `RiskBadge`, glyph+text+color, no opaque
 * score; unknown → nothing, LESSONS §7). Explicit loading/empty/error states.
 * Selecting a populated cell opens the E15 drilldown (skip-until-selected).
 *
 * ST.8e brings it to mockup fidelity: the "RCDO Coverage Heatmap" title + the
 * Rally-Cry line (from the RCDO read) + DO-id column prefixes; per-cell volume
 * bars (`volBars`) decoupled from risk; the dashed no-coverage 0-cell; a Row-total
 * column (`rowTotal`, client-derived); the risk/volume legend; and the manager
 * self-plan footnote. All render over the existing B.12 counts (no backend dep).
 */
export function HeatmapGrid() {
  const [weekStart] = useState(currentWeekStartIso);
  const [selectedCellId, setSelectedCellId] = useState<string | null>(null);
  const { data, isLoading, isError, error } = useGetHeatmapQuery({ weekStart });
  const { data: rcdo } = useGetRcdoQuery();
  const rallyCry = rcdo?.rallyCries[0]?.title;

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
  const selectedCell =
    selectedCellId !== null
      ? data.cells.find((c) => c.cellId === selectedCellId)
      : undefined;
  const rowCells = (reportId: string): HeatmapCellDto[] =>
    objectives
      .map((o) => cellAt(reportId, o.id))
      .filter((c): c is HeatmapCellDto => c !== undefined);

  return (
    <section
      data-cy="heatmap-grid"
      className="mx-auto max-w-content-max space-y-4 p-6"
    >
      <header className="space-y-1">
        <h1 className="text-h2 font-semibold text-ink-primary">
          RCDO Coverage Heatmap
        </h1>
        {rallyCry ? (
          <p
            data-cy="heatmap-rally-cry"
            className="text-meta text-ink-secondary"
          >
            Rally Cry:{' '}
            <span className="font-medium text-ink-primary">
              &ldquo;{rallyCry}&rdquo;
            </span>
          </p>
        ) : (
          <p className="text-meta text-ink-secondary">
            Risk by report and Defining Objective.
          </p>
        )}
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
                <th key={o.id} scope="col" className="py-2 align-bottom">
                  <span className="block font-mono text-meta uppercase text-ink-muted">
                    {o.id.toUpperCase()}
                  </span>
                  {o.label}
                </th>
              ))}
              <th scope="col" className="py-2 align-bottom">
                Row total
              </th>
            </tr>
          </thead>
          <tbody>
            {reports.map((r) => {
              const rt = rowTotal(rowCells(r.id));
              return (
                <tr key={r.id} className="border-t border-border">
                  <th
                    scope="row"
                    className="py-3 text-body font-medium text-ink-primary"
                  >
                    <span className="flex items-center gap-2">
                      <Avatar name={r.label} />
                      {r.label}
                    </span>
                  </th>
                  {objectives.map((o) => {
                    const cell = cellAt(r.id, o.id);
                    const count = cell?.commitmentCount ?? 0;
                    // No-coverage 0-cell — dashed, non-clickable (nothing to
                    // drill into); distinct from a populated 0-risk cell.
                    if (!cell || isNoCoverage(count)) {
                      return (
                        <td key={o.id} data-cy="heatmap-cell" className="py-3">
                          <div
                            data-cy="no-coverage"
                            className="flex items-center gap-1 rounded-md border border-dashed border-border-strong bg-vol-gap px-3 py-2 text-meta text-ink-muted"
                          >
                            <HiMinusCircle
                              aria-hidden
                              className="h-3.5 w-3.5"
                            />
                            no coverage
                          </div>
                        </td>
                      );
                    }
                    const vol = cellVolume(count);
                    return (
                      <td key={o.id} data-cy="heatmap-cell" className="py-3">
                        <button
                          type="button"
                          aria-label={`${r.label} — ${o.label}`}
                          data-volume={vol}
                          onClick={() => setSelectedCellId(cell.cellId)}
                          className={`flex w-full flex-col items-start gap-1 rounded-md border border-border ${VOLUME_BG[vol]} px-3 py-2 text-left hover:bg-surface-hover focus:outline-none focus:ring-2 focus:ring-brand-ring`}
                        >
                          <span className="flex items-center gap-2">
                            <VolBars n={volBars(count)} />
                            <span
                              data-cy="cell-commitmentCount"
                              className="text-body font-semibold text-ink-primary"
                            >
                              {count}
                            </span>
                          </span>
                          {cell.riskBadges.length > 0 ? (
                            <span className="flex flex-wrap gap-1">
                              {cell.riskBadges.map((b, i) => (
                                <RiskBadge key={`${b}-${i}`} value={b} />
                              ))}
                            </span>
                          ) : null}
                        </button>
                      </td>
                    );
                  })}
                  <td data-cy="row-total" className="py-3">
                    <span
                      className={`inline-flex rounded-full px-2 py-0.5 text-meta font-medium ${ROW_TOTAL_TONE[rt.tone]}`}
                    >
                      {rt.text}
                    </span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}

      {/* Manager self-plan footnote (Q2 — static; the manager's own plan is not in
          the team heatmap response, so it's excluded from the roll-up). */}
      <p data-cy="heatmap-self-plan" className="text-meta text-ink-muted">
        Your own self-plan is excluded from the team roll-up.{' '}
        <a
          href="/weekly-commit"
          className="text-brand-400 hover:underline focus:outline-none focus:ring-2 focus:ring-brand-ring"
        >
          View my plan ›
        </a>
      </p>

      {/* Legend — risk badges + meanings, and the volume scale (canon heat-legend);
          volume = load, risk = badges, no single health score. */}
      <div
        data-cy="heatmap-legend"
        className="flex flex-col gap-3 rounded-lg border border-border bg-surface-raised p-4 text-meta text-ink-secondary sm:flex-row sm:flex-wrap sm:gap-x-8"
      >
        <div className="space-y-2">
          <span className="block font-medium uppercase text-ink-muted">
            Risk badges
          </span>
          <div className="grid grid-cols-1 gap-1 sm:grid-cols-2 sm:gap-x-6">
            {RISK_LEGEND.map(({ value, meaning }) => (
              <span key={value} className="inline-flex items-center gap-2">
                <RiskBadge value={value} />
                <span>{meaning}</span>
              </span>
            ))}
          </div>
        </div>
        <div className="space-y-2">
          <span className="block font-medium uppercase text-ink-muted">
            Volume (commitment count)
          </span>
          <span className="block">1 light · 2–3 normal · 4+ high load</span>
          <span className="block text-ink-muted">
            Volume = load · Risk = badges · no single health score.
          </span>
        </div>
      </div>

      {/*
       * The cell drilldown opens in a themed Flowbite Drawer (right-slide +
       * scrim, raised surface — ST.6c). The Drawer always renders its children
       * (open = off-screen translate), so the drilldown body is mounted ONLY
       * while a cell is selected — preserving skip-until-selected (the E15 query
       * fires only when open).
       */}
      <Drawer
        open={selectedCellId !== null}
        onClose={() => setSelectedCellId(null)}
        position="right"
        data-cy="drilldown-drawer"
      >
        {selectedCellId !== null && selectedCell ? (
          <>
            <div className="flex items-start justify-between gap-4 border-b border-border px-4 py-3">
              <div>
                <h2 className="text-h3 font-semibold text-ink-primary">
                  {selectedCell.employeeDisplayName}
                </h2>
                <p className="text-meta text-ink-secondary">
                  {selectedCell.definingObjectiveTitle}
                </p>
              </div>
              <button
                type="button"
                aria-label="Close"
                onClick={() => setSelectedCellId(null)}
                className="rounded-md p-1 text-ink-secondary hover:bg-surface-hover focus:outline-none focus:ring-2 focus:ring-brand-ring"
              >
                <HiX aria-hidden className="h-5 w-5" />
              </button>
            </div>
            <Drawer.Items className="p-4">
              <HeatmapCellDrilldown cellId={selectedCellId} />
            </Drawer.Items>
          </>
        ) : null}
      </Drawer>
    </section>
  );
}
