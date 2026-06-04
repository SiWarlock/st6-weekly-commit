import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { CommandCenterSummary } from './CommandCenterSummary';
import { summarizeRows } from './commandCenterRow';
import type { ManagerCommandCenterRowDto } from '../../shared/lib/dtos';

// ST.8b — the "At a glance" summary strip (D-1). Client-computed from the loaded
// command-center rows (lead/user-approved; the production-correct path defers to
// the backend §9 `summary` field — a documented follow-up, not built here). The
// derivation is a PURE function over the rows — the testable core.

function row(
  p: Partial<ManagerCommandCenterRowDto> = {},
): ManagerCommandCenterRowDto {
  return {
    managerEmployeeId: 'mgr-1',
    employeeId: 'e1',
    employeeDisplayName: 'Ivy Chen',
    weeklyPlanId: 'plan-1',
    weekStartDate: '2026-06-01',
    planState: 'LOCKED',
    isReviewOverdue: false,
    plannedCount: 1,
    unplannedCount: 0,
    misalignedCount: 0,
    needsReviewCount: 0,
    blockedCount: 0,
    carryForwardCount: 0,
    unresolvedDisputeCount: 0,
    updatedAt: '2026-06-06T18:00:00Z',
    ...p,
  };
}

// A representative 6-report week (one per signal) — mirrors the mockup's GLANCE.
const ROWS: ManagerCommandCenterRowDto[] = [
  row({ employeeId: 'a', planState: 'DRAFT' }), // not locked
  row({
    employeeId: 'b',
    planState: 'RECONCILING',
    reviewStatus: 'NOT_REVIEWED',
    reviewDueAt: '2026-06-04T17:00:00Z',
    isReviewOverdue: true, // review overdue
    unresolvedDisputeCount: 1, // open dispute
  }),
  row({ employeeId: 'c', planState: 'RECONCILING' }), // reconciling (2 total)
  row({
    employeeId: 'd',
    planState: 'RECONCILED',
    reviewStatus: 'REVIEWED', // reviewed clean
  }),
  row({
    employeeId: 'e',
    planState: 'LOCKED',
    reviewStatus: 'REVIEWED_WITH_DISPUTES', // reviewed, NOT clean
  }),
  row({ employeeId: 'f', planState: 'LOCKED', reviewStatus: 'NOT_REVIEWED' }),
];

describe('ST.8b CommandCenterSummary — at-a-glance derivation (D-1)', () => {
  it('summarize_rows_counts: summarizeRows() returns the correct per-signal counts over the loaded rows', () => {
    const g = summarizeRows(ROWS);
    expect(g.reports).toBe(6);
    expect(g.overdue).toBe(1); // row b (isReviewOverdue)
    expect(g.disputes).toBe(1); // sum of unresolvedDisputeCount (row b)
    expect(g.reconciling).toBe(2); // rows b + c
    expect(g.notLocked).toBe(1); // row a (DRAFT)
    expect(g.reviewedClean).toBe(1); // row d (REVIEWED; WITH_DISPUTES is not clean)
  });

  it('reviewed_with_disputes_not_clean_but_feeds_disputes: a REVIEWED_WITH_DISPUTES row is EXCLUDED from reviewedClean (only REVIEWED is clean) yet its unresolvedDisputeCount DOES feed the disputes total — the green-vs-amber discrimination the strip exists for (reachable on a live row via the 9.15 CC overlay)', () => {
    const g = summarizeRows([
      row({
        employeeId: 'clean',
        reviewStatus: 'REVIEWED',
        unresolvedDisputeCount: 0,
      }),
      row({
        employeeId: 'wd',
        reviewStatus: 'REVIEWED_WITH_DISPUTES',
        unresolvedDisputeCount: 2,
      }),
    ]);
    expect(g.reviewedClean).toBe(1); // only the REVIEWED row, NOT the disputed one
    expect(g.disputes).toBe(2); // the REVIEWED_WITH_DISPUTES row's count feeds disputes
  });

  it('summarize_rows_empty: an empty row set yields all-zero counts (reports 0)', () => {
    expect(summarizeRows([])).toEqual({
      reports: 0,
      overdue: 0,
      disputes: 0,
      reconciling: 0,
      notLocked: 0,
      reviewedClean: 0,
    });
  });

  it('summary_strip_renders_counts: the strip renders the "At a glance" lead + each derived count', () => {
    render(<CommandCenterSummary rows={ROWS} />);
    expect(screen.getByText(/at a glance/i)).toBeInTheDocument();
    expect(screen.getByText(/6 reports/i)).toBeInTheDocument();
    expect(screen.getByText(/1 review overdue/i)).toBeInTheDocument();
    expect(screen.getByText(/1 open disputes/i)).toBeInTheDocument();
    expect(screen.getByText(/1 reviewed clean/i)).toBeInTheDocument();
  });
});
