import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { RcdoBrowser } from './RcdoBrowser';
import { useGetRcdoQuery } from './rcdoApi';
import type { RcdoTreeDto } from './rcdoApi';

vi.mock('./rcdoApi');

const TREE: RcdoTreeDto = {
  rallyCries: [
    {
      id: 'rc-1',
      title: 'Win the quarter',
      active: true,
      definingObjectives: [
        {
          id: 'do-1',
          rallyCryId: 'rc-1',
          title: 'Objective One',
          active: true,
          supportingOutcomes: [
            {
              id: 'so-1-1',
              definingObjectiveId: 'do-1',
              title: 'Ship onboarding',
              active: true,
            },
            {
              id: 'so-1-2',
              definingObjectiveId: 'do-1',
              title: 'Reduce churn',
              active: true,
            },
          ],
        },
        {
          id: 'do-2',
          rallyCryId: 'rc-1',
          title: 'Objective Two',
          active: true,
          supportingOutcomes: [
            {
              id: 'so-2-1',
              definingObjectiveId: 'do-2',
              title: 'Grow revenue',
              active: true,
            },
          ],
        },
      ],
    },
  ],
};

function mockRcdo(value: {
  data?: RcdoTreeDto;
  isLoading?: boolean;
  isError?: boolean;
  error?: unknown;
}) {
  vi.mocked(useGetRcdoQuery).mockReturnValue({
    data: value.data,
    isLoading: value.isLoading ?? false,
    isError: value.isError ?? false,
    error: value.error,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useGetRcdoQuery>);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('RcdoBrowser (browse + client-side search the RC→DO→SO tree)', () => {
  it('rcdo_browser_renders_tree: renders the rally-cry / defining-objective / supporting-outcome titles from the fetched tree', () => {
    mockRcdo({ data: TREE });
    render(<RcdoBrowser />);

    expect(screen.getByText('Win the quarter')).toBeInTheDocument();
    expect(screen.getByText('Objective One')).toBeInTheDocument();
    expect(screen.getByText('Objective Two')).toBeInTheDocument();
    expect(screen.getByText('Ship onboarding')).toBeInTheDocument();
    expect(screen.getByText('Grow revenue')).toBeInTheDocument();
  });

  it('rcdo_browser_search_narrows: a client-side query filters the visible outcomes', async () => {
    const user = userEvent.setup();
    mockRcdo({ data: TREE });
    render(<RcdoBrowser />);

    await user.type(screen.getByRole('searchbox'), 'churn');

    expect(screen.getByText('Reduce churn')).toBeInTheDocument();
    // Non-matching outcomes drop out of the filtered view.
    expect(screen.queryByText('Ship onboarding')).toBeNull();
    expect(screen.queryByText('Grow revenue')).toBeNull();
  });

  it('rcdo_browser_loading_renders_loadingstate: a pending query renders the shared LoadingState', () => {
    mockRcdo({ isLoading: true });
    render(<RcdoBrowser />);
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('rcdo_browser_empty_renders_emptystate: an empty tree renders the shared EmptyState, not a blank list', () => {
    mockRcdo({ data: { rallyCries: [] } });
    render(<RcdoBrowser />);
    expect(
      screen.getByText(/no .*outcomes|nothing to show|empty/i),
    ).toBeInTheDocument();
  });

  it('rcdo_browser_error_renders_errorstate: a failed query renders the shared ErrorState with the safe message', () => {
    mockRcdo({
      isError: true,
      error: { safeMessage: 'Could not load outcomes.' },
    });
    render(<RcdoBrowser />);
    expect(document.querySelector('[data-cy="error-state"]')).not.toBeNull();
    expect(screen.getByText('Could not load outcomes.')).toBeInTheDocument();
  });
});
