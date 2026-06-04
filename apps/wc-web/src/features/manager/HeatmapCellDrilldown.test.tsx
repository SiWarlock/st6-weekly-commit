import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { HeatmapCellDrilldown } from './HeatmapCellDrilldown';
import { useGetHeatmapDrilldownQuery } from './managerApi';
import type {
  HeatmapDrilldownDto,
  WeeklyCommitmentDto,
  PageEnvelope,
} from '../../shared/lib/dtos';

vi.mock('./managerApi');

function commitment(id: string): WeeklyCommitmentDto {
  return {
    id,
    weeklyPlanId: 'plan-1',
    commitmentKind: 'PLANNED',
    title: `Commitment ${id}`,
    priority: 'P1',
    workType: 'STRATEGIC',
    confidence: 'HIGH',
    alignmentStatus: 'MISALIGNED',
    allowedActions: [],
    version: 0,
  };
}

function commitmentsEnvelope(
  ids: string[],
  page: Partial<PageEnvelope<WeeklyCommitmentDto>['page']> = {},
): PageEnvelope<WeeklyCommitmentDto> {
  return {
    content: ids.map(commitment),
    page: {
      number: 0,
      size: 25,
      totalElements: ids.length,
      totalPages: 1,
      ...page,
    },
    sort: [{ property: 'priority', direction: 'ASC' }],
  };
}

function drilldown(
  groups: HeatmapDrilldownDto['supportingOutcomes'],
): HeatmapDrilldownDto {
  return {
    cellId: 'cell-1',
    employeeId: 'emp-1',
    definingObjectiveId: 'do-1',
    supportingOutcomes: groups,
  };
}

function mockDrilldown(value: {
  data?: HeatmapDrilldownDto;
  isLoading?: boolean;
  isError?: boolean;
  error?: unknown;
}) {
  vi.mocked(useGetHeatmapDrilldownQuery).mockReturnValue({
    data: value.data,
    isLoading: value.isLoading ?? false,
    isError: value.isError ?? false,
    error: value.error,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useGetHeatmapDrilldownQuery>);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('HeatmapCellDrilldown (E15 SO → commitment breakdown)', () => {
  it('renders_outcome_groups_with_commitments: renders each DrilldownOutcomeGroup (SO title + its commitments from the B.20 envelope) explaining the cell (REQ-UX-003)', () => {
    mockDrilldown({
      data: drilldown([
        {
          supportingOutcomeId: 'so-1',
          supportingOutcomeTitle: 'Streamline onboarding',
          commitments: commitmentsEnvelope(['c-1', 'c-2']),
        },
        {
          supportingOutcomeId: 'so-2',
          supportingOutcomeTitle: 'Reduce churn',
          commitments: commitmentsEnvelope(['c-3']),
        },
      ]),
    });

    render(<HeatmapCellDrilldown cellId="cell-1" />);

    expect(screen.getByText('Streamline onboarding')).toBeInTheDocument();
    expect(screen.getByText('Reduce churn')).toBeInTheDocument();
    expect(screen.getByText('Commitment c-1')).toBeInTheDocument();
    expect(screen.getByText('Commitment c-2')).toBeInTheDocument();
    expect(screen.getByText('Commitment c-3')).toBeInTheDocument();
  });

  it('drilldown_loading_empty_error_states: loading → LoadingState; empty groups → EmptyState; a 404 → ErrorState(safeMessage) (IDOR-safe, no crash/leak)', () => {
    mockDrilldown({ isLoading: true });
    const { rerender } = render(<HeatmapCellDrilldown cellId="cell-1" />);
    expect(screen.getByRole('status')).toBeInTheDocument();

    mockDrilldown({
      isError: true,
      error: { safeMessage: 'That cell is not available.' },
    });
    rerender(<HeatmapCellDrilldown cellId="cell-9" />);
    expect(document.querySelector('[data-cy="error-state"]')).not.toBeNull();
    expect(screen.getByText('That cell is not available.')).toBeInTheDocument();

    mockDrilldown({ data: drilldown([]) });
    rerender(<HeatmapCellDrilldown cellId="cell-1" />);
    expect(document.querySelector('[data-cy="empty-state"]')).not.toBeNull();
  });

  it('drilldown_paginates_commitments: the single pager reflects the MAX totalPages across groups (so a deeper non-first group is reachable, not hidden); clicking next re-queries with the incremented page (server-side; no client slice, F.5 sort server-applied)', async () => {
    const user = userEvent.setup();
    // Uneven groups: the FIRST has 1 page, a LATER one has 3. The single E15 page
    // param paginates every group together, so the pager must use the max (3) —
    // driving it off the first group (1) would hide the deeper group's commitments.
    mockDrilldown({
      data: drilldown([
        {
          supportingOutcomeId: 'so-1',
          supportingOutcomeTitle: 'Streamline onboarding',
          commitments: commitmentsEnvelope(['c-1'], { totalPages: 1 }),
        },
        {
          supportingOutcomeId: 'so-2',
          supportingOutcomeTitle: 'Reduce churn',
          commitments: commitmentsEnvelope(['c-2', 'c-3'], { totalPages: 3 }),
        },
      ]),
    });

    render(<HeatmapCellDrilldown cellId="cell-1" />);
    // Max across groups = 3 (not the first group's 1) → Next stays enabled.
    expect(screen.getByText(/page 1 of 3/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /next page/i }));

    const calls = vi.mocked(useGetHeatmapDrilldownQuery).mock.calls;
    const lastArg = calls[calls.length - 1]?.[0] as { page?: number };
    expect(lastArg.page).toBe(1);
  });
});
