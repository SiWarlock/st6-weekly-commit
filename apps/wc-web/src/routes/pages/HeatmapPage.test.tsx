import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import HeatmapPage from './HeatmapPage';
import { useGetHeatmapQuery } from '../../features/manager/managerApi';
import type { HeatmapResponseDto } from '../../shared/lib/dtos';

vi.mock('../../features/manager/managerApi');
// HeatmapGrid (ST.8e) sources its Rally-Cry line from the RCDO read — mock it so
// this store-free page-module test doesn't need a Provider.
vi.mock('../../features/rcdo/rcdoApi', () => ({
  useGetRcdoQuery: () => ({
    data: undefined,
    isLoading: false,
    isError: false,
  }),
}));

afterEach(() => {
  vi.restoreAllMocks();
});

describe('HeatmapPage (/manager/heatmap route module)', () => {
  it('HeatmapPage_renders_heatmap_grid: the page module renders the real HeatmapGrid (replaces the 9.4 placeholder)', () => {
    vi.mocked(useGetHeatmapQuery).mockReturnValue({
      data: { weekStart: '2026-06-01', cells: [] } as HeatmapResponseDto,
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useGetHeatmapQuery>);

    render(<HeatmapPage />);

    expect(document.querySelector('[data-cy="heatmap-grid"]')).not.toBeNull();
    // The 9.4 placeholder copy is gone.
    expect(screen.queryByText(/coming in 9\.10/i)).toBeNull();
  });
});
