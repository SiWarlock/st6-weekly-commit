import { describe, it, expect } from 'vitest';
import { volBars, isNoCoverage, rowTotal } from './heatmapDerive';
import type { HeatmapCellDto } from '../../shared/lib/dtos';

function cell(overrides: Partial<HeatmapCellDto> = {}): HeatmapCellDto {
  return {
    cellId: 'c',
    managerEmployeeId: 'm',
    employeeId: 'e',
    employeeDisplayName: 'E',
    weekStartDate: '2026-06-01',
    definingObjectiveId: 'do',
    definingObjectiveTitle: 'DO',
    commitmentCount: 0,
    plannedCount: 0,
    unplannedCount: 0,
    misalignedCount: 0,
    needsReviewCount: 0,
    blockedCount: 0,
    carryForwardCount: 0,
    unresolvedDisputeCount: 0,
    riskBadges: [],
    ...overrides,
  };
}

describe('heatmapDerive — pure heatmap derivations (canon volMeta/rowTotalText over B.12 counts)', () => {
  it('vol_bars_from_count: volBars maps commitmentCount to the canon volMeta bar tiers — 0→0, 1→1, 2/3→2, 4+→3 (volume = load, decoupled from risk)', () => {
    expect(volBars(0)).toBe(0);
    expect(volBars(1)).toBe(1);
    expect(volBars(2)).toBe(2);
    expect(volBars(3)).toBe(2);
    expect(volBars(4)).toBe(3);
    expect(volBars(9)).toBe(3);
  });

  it('no_coverage_predicate: isNoCoverage is true iff commitmentCount is 0 — the dashed no-coverage cell branch, distinct from a populated 0-risk cell (count>0)', () => {
    expect(isNoCoverage(0)).toBe(true);
    expect(isNoCoverage(1)).toBe(false);
    expect(isNoCoverage(5)).toBe(false);
  });

  it('row_total_tone_and_text: rowTotal aggregates a row’s cells (canon rowTotalText) — all-clean→"N clean"/success; a risky cell→"N · M at risk"/failure; an overloaded (count≥4) cell→warning; all-zero→"Not locked"/neutral (draftOnly proxied by total===0 since B.12 carries no draft flag)', () => {
    // All clean (no riskBadges anywhere), total 4 → "4 clean" / success.
    expect(
      rowTotal([
        cell({ commitmentCount: 3, riskBadges: [] }),
        cell({ commitmentCount: 1, riskBadges: [] }),
      ]),
    ).toEqual({ text: '4 clean', tone: 'success' });

    // One risky cell, none overloaded → "3 · 1 at risk" / failure.
    expect(
      rowTotal([
        cell({ commitmentCount: 2, riskBadges: ['MISALIGNED'] }),
        cell({ commitmentCount: 1, riskBadges: [] }),
      ]),
    ).toEqual({ text: '3 · 1 at risk', tone: 'failure' });

    // Risky AND an overloaded (count≥4) cell → still "at risk" but warning tone.
    expect(
      rowTotal([
        cell({ commitmentCount: 5, riskBadges: ['BLOCKED'] }),
        cell({ commitmentCount: 1, riskBadges: [] }),
      ]),
    ).toEqual({ text: '6 · 1 at risk', tone: 'warning' });

    // All-zero row (no projected commitments) → "Not locked" / neutral.
    expect(
      rowTotal([cell({ commitmentCount: 0 }), cell({ commitmentCount: 0 })]),
    ).toEqual({ text: 'Not locked', tone: 'neutral' });
  });
});
