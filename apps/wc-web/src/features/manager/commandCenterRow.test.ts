import { describe, it, expect } from 'vitest';
import { reviewSubline, reconcileText, actionLabel } from './commandCenterRow';
import type { ManagerCommandCenterRowDto } from '../../shared/lib/dtos';

// ST.8b — the pure per-row derivations for the command-center table (the review
// timestamp sub-line §B.5, the RECONCILE column §B.6, the Review/Open action
// §B.9). Deterministic over the existing B.11 row fields (no new endpoint); the
// pure functions are the testable core — the table render wires them.

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
    plannedCount: 3,
    unplannedCount: 1,
    misalignedCount: 0,
    needsReviewCount: 0,
    blockedCount: 0,
    carryForwardCount: 0,
    unresolvedDisputeCount: 0,
    updatedAt: '2026-06-06T18:00:00Z',
    ...p,
  };
}

describe('ST.8b commandCenterRow — review sub-line (§B.5)', () => {
  it('review_subline_due_when_not_reviewed_and_not_overdue: a NOT_REVIEWED, not-overdue row reads "Due <date>" from reviewDueAt', () => {
    const s = reviewSubline(
      row({
        reviewStatus: 'NOT_REVIEWED',
        reviewDueAt: '2026-06-02T17:00:00Z',
        isReviewOverdue: false,
      }),
    );
    expect(s).toBe('Due Jun 2, 5:00 PM');
  });

  it('review_subline_was_due_when_overdue: a NOT_REVIEWED + overdue row reads "Was due <date>" (the derived OVERDUE)', () => {
    const s = reviewSubline(
      row({
        reviewStatus: 'NOT_REVIEWED',
        reviewDueAt: '2026-05-29T17:00:00Z',
        isReviewOverdue: true,
      }),
    );
    expect(s).toBe('Was due May 29, 5:00 PM');
  });

  it('review_subline_reviewed_status: a REVIEWED row reads "Reviewed" (B.11 carries no reviewedAt — timestamp is a flagged gap)', () => {
    expect(reviewSubline(row({ reviewStatus: 'REVIEWED' }))).toBe('Reviewed');
    expect(reviewSubline(row({ reviewStatus: 'REVIEWED_WITH_DISPUTES' }))).toBe(
      'Reviewed',
    );
  });

  it('review_subline_empty_when_no_review_record: a row with no reviewStatus has no sub-line (the cell renders "—")', () => {
    expect(reviewSubline(row({ planState: 'DRAFT' }))).toBe('');
  });
});

describe('ST.8b commandCenterRow — reconcile column (§B.6)', () => {
  it('reconcile_text_in_progress_with_carry_forward: a RECONCILING row with a carry-forward reads "In progress · 1 carry-fwd"', () => {
    expect(
      reconcileText(row({ planState: 'RECONCILING', carryForwardCount: 1 })),
    ).toBe('In progress · 1 carry-fwd');
  });

  it('reconcile_text_in_progress_without_carry_forward: a RECONCILING row with no carry-forward reads "In progress"', () => {
    expect(
      reconcileText(row({ planState: 'RECONCILING', carryForwardCount: 0 })),
    ).toBe('In progress');
  });

  it('reconcile_text_dash_when_not_reconciling: a non-reconciling row reads "—"', () => {
    expect(reconcileText(row({ planState: 'LOCKED' }))).toBe('—');
    expect(reconcileText(row({ planState: 'DRAFT' }))).toBe('—');
  });
});

describe('ST.8b commandCenterRow — action label (§B.9)', () => {
  it('action_label_review_vs_open: a not-reviewed row reads "Review"; a reviewed row reads "Open"', () => {
    expect(actionLabel(row({ reviewStatus: 'NOT_REVIEWED' }))).toBe('Review');
    expect(actionLabel(row({ planState: 'DRAFT' }))).toBe('Review'); // no review record
    expect(actionLabel(row({ reviewStatus: 'REVIEWED' }))).toBe('Open');
    expect(actionLabel(row({ reviewStatus: 'REVIEWED_WITH_DISPUTES' }))).toBe(
      'Open',
    );
  });
});
