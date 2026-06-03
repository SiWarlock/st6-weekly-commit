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

// ST.6a — neutral volume-fill keyed on commitmentCount, decoupled from risk.
describe('HeatmapGrid → volume-fill (ST.6a)', () => {
  const heatmap = (cells: HeatmapCellDto[]): HeatmapResponseDto => ({
    weekStart: '2026-06-01',
    cells,
  });
  const cellBtn = (doTitle: string) =>
    screen.getByRole('button', { name: `Ivy Chen — ${doTitle}` });

  it('heatmap_cell_fill_scales_with_commitment_count: 0→none, 1→light, 2-3→normal, ≥4→heavy (Cadence volMeta breakpoints)', () => {
    mockHeatmap({
      data: heatmap([
        cell({
          definingObjectiveId: 'd0',
          definingObjectiveTitle: 'Zero',
          commitmentCount: 0,
          riskBadges: [],
        }),
        cell({
          definingObjectiveId: 'd1',
          definingObjectiveTitle: 'One',
          commitmentCount: 1,
          riskBadges: [],
        }),
        cell({
          definingObjectiveId: 'd3',
          definingObjectiveTitle: 'Three',
          commitmentCount: 3,
          riskBadges: [],
        }),
        cell({
          definingObjectiveId: 'd5',
          definingObjectiveTitle: 'Five',
          commitmentCount: 5,
          riskBadges: [],
        }),
      ]),
    });
    render(<HeatmapGrid />);

    expect(cellBtn('Zero')).toHaveAttribute('data-volume', 'none');
    expect(cellBtn('One')).toHaveAttribute('data-volume', 'light');
    expect(cellBtn('Three')).toHaveAttribute('data-volume', 'normal');
    expect(cellBtn('Five')).toHaveAttribute('data-volume', 'heavy');
  });

  it('heatmap_volume_decoupled_from_risk: a heavy cell with no riskBadges shows no RiskBadge; a light cell with riskBadges still renders them (volume ≠ risk)', () => {
    mockHeatmap({
      data: heatmap([
        cell({
          definingObjectiveId: 'dh',
          definingObjectiveTitle: 'Heavy no risk',
          commitmentCount: 6,
          riskBadges: [],
        }),
        cell({
          definingObjectiveId: 'dl',
          definingObjectiveTitle: 'Light with risk',
          commitmentCount: 1,
          riskBadges: ['BLOCKED'],
        }),
      ]),
    });
    render(<HeatmapGrid />);

    const heavy = cellBtn('Heavy no risk');
    expect(heavy).toHaveAttribute('data-volume', 'heavy');
    expect(within(heavy).queryByText(/blocked|misaligned/i)).toBeNull();

    const light = cellBtn('Light with risk');
    expect(light).toHaveAttribute('data-volume', 'light');
    expect(within(light).getByText(/blocked/i)).toBeInTheDocument();
  });

  it('heatmap_cell_keeps_count_text_and_badges: the count text + riskBadges still render (volume is additive — load is the number AND the fill, never color alone)', () => {
    mockHeatmap({
      data: heatmap([
        cell({
          definingObjectiveTitle: 'Loaded',
          commitmentCount: 4,
          riskBadges: ['MISALIGNED'],
        }),
      ]),
    });
    render(<HeatmapGrid />);

    const btn = cellBtn('Loaded');
    expect(
      btn.querySelector('[data-cy="cell-commitmentCount"]'),
    ).toHaveTextContent('4');
    expect(within(btn).getByText(/misaligned/i)).toBeInTheDocument();
  });
});

// ST.6c — the cell drilldown opens in a themed Flowbite Drawer (right-slide +
// scrim) instead of the inline panel. Render-only container swap: the existing
// HeatmapCellDrilldown body + groups + pager + view-states are unchanged.
describe('HeatmapGrid → drilldown Drawer (ST.6c)', () => {
  it('heatmap_drilldown_opens_in_drawer: selecting a cell opens the drilldown inside a [data-cy="drilldown-drawer"] Drawer (not the inline panel); onClose clears selectedCellId', async () => {
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
    // Closed: no drilldown content mounted yet (skip-until-selected).
    expect(document.querySelector('[data-cy="heatmap-drilldown"]')).toBeNull();

    await user.click(
      screen.getByRole('button', { name: /ivy chen.*grow activation/i }),
    );

    // The drilldown content renders INSIDE the Drawer, not the inline panel.
    const drawer = document.querySelector(
      '[data-cy="drilldown-drawer"]',
    ) as HTMLElement;
    expect(drawer).not.toBeNull();
    expect(
      drawer.querySelector('[data-cy="heatmap-drilldown"]'),
    ).not.toBeNull();
    expect(
      within(drawer).getByText('Streamline onboarding'),
    ).toBeInTheDocument();
    // Header identifies the cell (report × DO).
    expect(within(drawer).getByText(/ivy chen/i)).toBeInTheDocument();
    expect(within(drawer).getByText(/grow activation/i)).toBeInTheDocument();

    // onClose clears the selection → the drilldown content unmounts.
    await user.click(within(drawer).getByRole('button', { name: /close/i }));
    expect(document.querySelector('[data-cy="heatmap-drilldown"]')).toBeNull();
  });

  it('drilldown_drawer_preserves_view_states: a 404 ErrorState(safeMessage) renders inside the Drawer body — IDOR-safe, no regression from the container swap (§6/§7)', async () => {
    const user = userEvent.setup();
    mockHeatmap({ data: { weekStart: '2026-06-01', cells: [cell()] } });
    vi.mocked(useGetHeatmapDrilldownQuery).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: { safeMessage: 'That cell is not available.' },
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useGetHeatmapDrilldownQuery>);

    render(<HeatmapGrid />);
    await user.click(
      screen.getByRole('button', { name: /ivy chen.*grow activation/i }),
    );

    const drawer = document.querySelector(
      '[data-cy="drilldown-drawer"]',
    ) as HTMLElement;
    expect(drawer).not.toBeNull();
    expect(
      within(drawer).getByText('That cell is not available.'),
    ).toBeInTheDocument();
    expect(drawer.querySelector('[data-cy="error-state"]')).not.toBeNull();
  });
});
