import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { HeatmapGrid } from './HeatmapGrid';
import { useGetHeatmapQuery, useGetHeatmapDrilldownQuery } from './managerApi';
import type {
  HeatmapCellDto,
  HeatmapResponseDto,
  HeatmapDrilldownDto,
  RiskBadge,
} from '../../shared/lib/dtos';

vi.mock('./managerApi');

function cell(overrides: Partial<HeatmapCellDto> = {}): HeatmapCellDto {
  return {
    cellId: 'cell-1',
    managerEmployeeId: 'mgr-1',
    employeeId: 'emp-1',
    employeeDisplayName: 'Ivy Chen',
    weekStartDate: '2026-06-01',
    definingObjectiveId: 'do-1',
    definingObjectiveTitle: 'Grow activation',
    commitmentCount: 3,
    plannedCount: 2,
    unplannedCount: 1,
    misalignedCount: 1,
    needsReviewCount: 0,
    blockedCount: 0,
    carryForwardCount: 0,
    unresolvedDisputeCount: 0,
    riskBadges: ['MISALIGNED'],
    ...overrides,
  };
}

function mockHeatmap(value: {
  data?: HeatmapResponseDto;
  isLoading?: boolean;
  isError?: boolean;
  error?: unknown;
}) {
  vi.mocked(useGetHeatmapQuery).mockReturnValue({
    data: value.data,
    isLoading: value.isLoading ?? false,
    isError: value.isError ?? false,
    error: value.error,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useGetHeatmapQuery>);
}

function mockDrilldown(data: HeatmapDrilldownDto) {
  vi.mocked(useGetHeatmapDrilldownQuery).mockReturnValue({
    data,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useGetHeatmapDrilldownQuery>);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('HeatmapGrid (E14 report × DO grid — counts + enumerated riskBadges)', () => {
  it('renders_cells_with_counts_and_enumerated_riskBadges: each cell shows its commitment count + a RiskBadge for each enumerated value; an unknown badge renders nothing (LESSONS §7)', () => {
    mockHeatmap({
      data: {
        weekStart: '2026-06-01',
        // 'BOGUS' is not a RiskBadge — cast to prove the grid renders nothing for it.
        cells: [
          cell({
            riskBadges: ['MISALIGNED', 'BLOCKED', 'BOGUS'] as RiskBadge[],
          }),
        ],
      },
    });

    render(<HeatmapGrid />);
    const c = screen
      .getByRole('button', { name: /ivy chen.*grow activation/i })
      .closest('[data-cy="heatmap-cell"]') as HTMLElement;

    expect(
      c.querySelector('[data-cy="cell-commitmentCount"]'),
    ).toHaveTextContent('3');
    // Enumerated badges render via the §4.2 taxonomy.
    expect(within(c).getByText('Misaligned')).toBeInTheDocument();
    expect(within(c).getByText('Blocked')).toBeInTheDocument();
    // Unknown badge → nothing (RiskBadge tolerant value:string, §7).
    expect(within(c).queryByText(/bogus/i)).toBeNull();
  });

  it('renders_loading_empty_error_states: loading → LoadingState; zero cells → EmptyState; query error → ErrorState(safeMessage) (§7)', () => {
    mockHeatmap({ isLoading: true });
    const { rerender } = render(<HeatmapGrid />);
    expect(screen.getByRole('status')).toBeInTheDocument();

    mockHeatmap({
      isError: true,
      error: { safeMessage: 'You do not have access to this heatmap.' },
    });
    rerender(<HeatmapGrid />);
    expect(document.querySelector('[data-cy="error-state"]')).not.toBeNull();
    expect(
      screen.getByText('You do not have access to this heatmap.'),
    ).toBeInTheDocument();

    mockHeatmap({ data: { weekStart: '2026-06-01', cells: [] } });
    rerender(<HeatmapGrid />);
    expect(document.querySelector('[data-cy="empty-state"]')).not.toBeNull();
  });

  it('selecting_a_cell_opens_drilldown: clicking a cell sets the selected cellId → mounts HeatmapCellDrilldown for that cell (skip-until-selected). Pins Step-7.5 E15 reach', async () => {
    const user = userEvent.setup();
    mockHeatmap({ data: { weekStart: '2026-06-01', cells: [cell()] } });
    mockDrilldown({
      cellId: 'cell-1',
      employeeId: 'emp-1',
      definingObjectiveId: 'do-1',
      supportingOutcomes: [
        {
          supportingOutcomeId: 'so-1',
          supportingOutcomeTitle: 'Streamline onboarding',
          commitments: {
            content: [],
            page: { number: 0, size: 25, totalElements: 0, totalPages: 0 },
            sort: [],
          },
        },
      ],
    });

    render(<HeatmapGrid />);
    expect(document.querySelector('[data-cy="heatmap-drilldown"]')).toBeNull();

    await user.click(
      screen.getByRole('button', { name: /ivy chen.*grow activation/i }),
    );

    expect(
      document.querySelector('[data-cy="heatmap-drilldown"]'),
    ).not.toBeNull();
    expect(screen.getByText('Streamline onboarding')).toBeInTheDocument();
  });
});
