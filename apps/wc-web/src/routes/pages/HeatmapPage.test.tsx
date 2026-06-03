import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import HeatmapPage from './HeatmapPage';
import { useGetHeatmapQuery } from '../../features/manager/managerApi';
import type { HeatmapResponseDto } from '../../shared/lib/dtos';

vi.mock('../../features/manager/managerApi');

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
