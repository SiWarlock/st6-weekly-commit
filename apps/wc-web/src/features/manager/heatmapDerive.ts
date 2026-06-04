import type { HeatmapCellDto } from '../../shared/lib/dtos';

/**
 * Pure heatmap derivations (ST.8e) — the canon `Heatmap.jsx` `volMeta`/`rowTotalText`
 * computed over the existing B.12 `HeatmapCellDto` counts (no backend dep; mirrors
 * `commandCenterRow.ts`). Volume = load, decoupled from risk (LESSONS §25).
 */

/** Number of filled volume bars (0–3) for a cell's load — canon `volMeta` tiers. */
export function volBars(commitmentCount: number): 0 | 1 | 2 | 3 {
  if (commitmentCount <= 0) return 0;
  if (commitmentCount === 1) return 1;
  if (commitmentCount <= 3) return 2;
  return 3;
}

/** A cell with no projected commitments → the dashed "no coverage" cell. */
export function isNoCoverage(commitmentCount: number): boolean {
  return commitmentCount <= 0;
}

export type RowTotalTone = 'neutral' | 'success' | 'warning' | 'failure';
export interface RowTotalSummary {
  text: string;
  tone: RowTotalTone;
}

/**
 * The Row-total column badge (canon `rowTotalText`) aggregated over a report's
 * row cells. `draftOnly` is proxied by `total===0`: a LOCKED plan always has ≥1
 * projected cell (rule #1 — every locked plan has ≥1 commitment; DRAFT isn't
 * projected, §9), so an all-zero row means the report hasn't locked.
 */
export function rowTotal(cells: HeatmapCellDto[]): RowTotalSummary {
  const total = cells.reduce((n, c) => n + c.commitmentCount, 0);
  const risky = cells.filter((c) => c.riskBadges.length > 0).length;
  const overload = cells.some((c) => c.commitmentCount >= 4);
  if (total === 0) {
    return { text: 'Not locked', tone: 'neutral' };
  }
  if (risky === 0) {
    return { text: `${total} clean`, tone: 'success' };
  }
  return {
    text: `${total} · ${risky} at risk`,
    tone: overload ? 'warning' : 'failure',
  };
}
