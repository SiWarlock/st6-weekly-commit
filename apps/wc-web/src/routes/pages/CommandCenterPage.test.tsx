import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import CommandCenterPage from './CommandCenterPage';
import { useGetCommandCenterQuery } from '../../features/manager/managerApi';
import type {
  ManagerCommandCenterRowDto,
  PageEnvelope,
} from '../../shared/lib/dtos';

vi.mock('../../features/manager/managerApi');

function env(): PageEnvelope<ManagerCommandCenterRowDto> {
  return {
    content: [
      {
        managerEmployeeId: 'mgr-1',
        employeeId: 'emp-1',
        employeeDisplayName: 'Ivy Chen',
        weeklyPlanId: 'plan-1',
        weekStartDate: '2026-06-01',
        planState: 'LOCKED',
        reviewStatus: 'NOT_REVIEWED',
        isReviewOverdue: false,
        plannedCount: 1,
        unplannedCount: 0,
        misalignedCount: 0,
        needsReviewCount: 0,
        blockedCount: 0,
        carryForwardCount: 0,
        unresolvedDisputeCount: 0,
        updatedAt: '2026-06-02T10:00:00Z',
      },
    ],
    page: { number: 0, size: 25, totalElements: 1, totalPages: 1 },
    sort: [{ property: 'weekStartDate', direction: 'DESC' }],
  };
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('CommandCenterPage (/manager/command-center route module)', () => {
  it('CommandCenterPage_renders_command_center: the page module renders the real CommandCenter (replaces the 9.4 placeholder)', () => {
    vi.mocked(useGetCommandCenterQuery).mockReturnValue({
      data: env(),
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useGetCommandCenterQuery>);

    render(<CommandCenterPage />);

    expect(document.querySelector('[data-cy="command-center"]')).not.toBeNull();
    expect(screen.getByText('Ivy Chen')).toBeInTheDocument();
    // The 9.4 placeholder copy is gone.
    expect(screen.queryByText(/coming in 9\.9/i)).toBeNull();
  });
});
