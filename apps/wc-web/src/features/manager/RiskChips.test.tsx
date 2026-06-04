import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { RiskChips } from './RiskChips';
import type { ManagerCommandCenterRowDto } from '../../shared/lib/dtos';

// ST.8b — the labeled, hide-zero risk chips + the always-shown P/U count pills
// (§B.3). Tone/icon/label come from the statusTaxonomy render map (no inline
// re-map, LESSONS §7); chips render only when count > 0. B.11 carries
// misaligned/needsReview/blocked/carryForward/unresolvedDispute counts (the 5
// renderable kinds; resolved/unlinked have no DTO field — flagged).

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

describe('ST.8b RiskChips — labeled + hide-zero + P/U pills (§B.3)', () => {
  it('risk_chips_hide_zero_and_label: nonzero counts render labeled chips; zero counts are omitted', () => {
    render(
      <RiskChips
        row={row({ misalignedCount: 1, blockedCount: 0, unresolvedDisputeCount: 2 })}
      />,
    );
    // Nonzero → labeled chips present.
    expect(screen.getByText(/1 misaligned/i)).toBeInTheDocument();
    expect(screen.getByText(/2 dispute/i)).toBeInTheDocument();
    // Zero → omitted.
    expect(screen.queryByText(/blocked/i)).toBeNull();
    expect(screen.queryByText(/needs-review/i)).toBeNull();
    expect(screen.queryByText(/carry-fwd/i)).toBeNull();
  });

  it('pu_count_pills_always_render: the planned/unplanned count pills render even at the zero/default counts', () => {
    render(<RiskChips row={row({ plannedCount: 3, unplannedCount: 1 })} />);
    expect(screen.getByText('3 P')).toBeInTheDocument();
    expect(screen.getByText('1 U')).toBeInTheDocument();
  });

  it('all_risk_kinds_render_when_nonzero: misaligned/needs-review/blocked/carry-fwd/dispute all render labeled when > 0', () => {
    render(
      <RiskChips
        row={row({
          misalignedCount: 1,
          needsReviewCount: 2,
          blockedCount: 3,
          carryForwardCount: 4,
          unresolvedDisputeCount: 5,
        })}
      />,
    );
    expect(screen.getByText(/1 misaligned/i)).toBeInTheDocument();
    expect(screen.getByText(/2 needs-review/i)).toBeInTheDocument();
    expect(screen.getByText(/3 blocked/i)).toBeInTheDocument();
    expect(screen.getByText(/4 carry-fwd/i)).toBeInTheDocument();
    expect(screen.getByText(/5 dispute/i)).toBeInTheDocument();
  });
});
