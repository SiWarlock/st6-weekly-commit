import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { CommandCenterFilters } from './CommandCenterFilters';
import type { CommandCenterParams } from './managerApi';

// ST.8b-2 — the compact dropdown-chip filter row (mockup §B.2). A PRESENTATION
// rebuild of the 2-row <select> grid: each filter is a hand-rolled dropdown that
// applies the SAME query param (the filter plumbing is preserved verbatim), plus
// removable active-filter chips, Clear-all, and a "Showing N of M reports" count.
// The global week affordance moved to the ST.8a app-bar + ST.8b pager (Q3) — the
// in-filter week-of control is dropped. Option labels come from statusTaxonomy.

// The Defining-objective dropdown sources its options from the existing RCDO read
// (9.5) — mock it so the presentation test needs no store/network.
vi.mock('../rcdo/rcdoApi', () => ({
  useGetRcdoQuery: () => ({
    data: {
      rallyCries: [
        {
          id: 'rc-1',
          title: 'RC',
          active: true,
          definingObjectives: [
            {
              id: 'do-1',
              rallyCryId: 'rc-1',
              title: 'Grow activation',
              active: true,
              supportingOutcomes: [],
            },
            {
              id: 'do-2',
              rallyCryId: 'rc-1',
              title: 'Operational excellence',
              active: true,
              supportingOutcomes: [],
            },
          ],
        },
      ],
    },
    isLoading: false,
    isError: false,
  }),
}));

function value(
  overrides: Partial<CommandCenterParams> = {},
): CommandCenterParams {
  return { weekStart: '2026-06-01', ...overrides };
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('CommandCenterFilters (compact dropdown-chip row — drives the E13 query params, REQ-F-023)', () => {
  it('filter_dropdown_opens_and_applies: clicking a filter chip opens its options (labeled via the taxonomy); selecting one applies the existing query param — incl. the dynamic Person + Defining-objective dropdowns; reviewState=OVERDUE passes through verbatim', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <CommandCenterFilters
        value={value()}
        onChange={onChange}
        reports={[{ id: 'e1', name: 'Ivy Chen' }]}
      />,
    );

    // Enum filter — options labeled via the taxonomy ("Overdue", not "OVERDUE").
    await user.click(screen.getByRole('button', { name: /review state/i }));
    await user.click(screen.getByRole('button', { name: 'Overdue' }));
    expect(onChange).toHaveBeenCalledWith({ reviewState: 'OVERDUE' });

    await user.click(screen.getByRole('button', { name: /plan state/i }));
    await user.click(screen.getByRole('button', { name: 'Locked' }));
    expect(onChange).toHaveBeenCalledWith({ planState: 'LOCKED' });

    await user.click(screen.getByRole('button', { name: /priority/i }));
    await user.click(screen.getByRole('button', { name: 'P0' }));
    expect(onChange).toHaveBeenCalledWith({ priority: 'P0' });

    // Dynamic Defining-objective dropdown (sourced from the RCDO read).
    await user.click(
      screen.getByRole('button', { name: /defining objective/i }),
    );
    await user.click(screen.getByRole('button', { name: 'Grow activation' }));
    expect(onChange).toHaveBeenCalledWith({ definingObjectiveId: 'do-1' });

    // Dynamic Person dropdown (sourced from the loaded direct reports).
    await user.click(screen.getByRole('button', { name: /person/i }));
    await user.click(screen.getByRole('button', { name: 'Ivy Chen' }));
    expect(onChange).toHaveBeenCalledWith({ employeeId: 'e1' });
  });

  it('all_filter_chips_present: all 7 filter dropdown triggers render — no dropped filter (the rebuild keeps the full set incl. Alignment status)', () => {
    render(
      <CommandCenterFilters value={value()} onChange={vi.fn()} reports={[]} />,
    );
    for (const name of [
      /person/i,
      /plan state/i,
      /review state/i,
      /defining objective/i,
      /priority/i,
      /work type/i,
      /alignment status/i,
    ]) {
      expect(screen.getByRole('button', { name })).toBeInTheDocument();
    }
  });

  it('active_filter_chip_renders_and_removes: an applied filter renders a removable chip; its ✕ clears that one filter; other active filters persist (weekStart is never a chip)', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();

    const { rerender } = render(
      <CommandCenterFilters
        value={value({ planState: 'LOCKED', priority: 'P0' })}
        onChange={onChange}
      />,
    );

    expect(
      document.querySelector('[data-cy="filter-chip-planState"]'),
    ).not.toBeNull();
    expect(
      document.querySelector('[data-cy="filter-chip-priority"]'),
    ).not.toBeNull();
    expect(
      document.querySelector('[data-cy="filter-chip-weekStart"]'),
    ).toBeNull();

    await user.click(
      screen.getByRole('button', { name: /remove plan state filter/i }),
    );
    expect(onChange).toHaveBeenCalledWith({ planState: undefined });

    rerender(
      <CommandCenterFilters
        value={value({ priority: 'P0' })}
        onChange={onChange}
      />,
    );
    expect(
      document.querySelector('[data-cy="filter-chip-planState"]'),
    ).toBeNull();
    expect(
      document.querySelector('[data-cy="filter-chip-priority"]'),
    ).not.toBeNull();
  });

  it('clear_all_resets_filters: Clear all clears every chip-able filter key in one patch (weekStart, the required week, is never cleared)', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <CommandCenterFilters
        value={value({ planState: 'LOCKED', priority: 'P0' })}
        onChange={onChange}
      />,
    );

    await user.click(screen.getByRole('button', { name: /clear all/i }));
    const clearPatch = onChange.mock.calls.at(-1)![0];
    expect(clearPatch).toMatchObject({
      planState: undefined,
      priority: undefined,
    });
    expect(clearPatch).not.toHaveProperty('weekStart');
  });

  it('result_count_reflects_filtered_total: renders "Showing N of M reports" from the shown/total props (N returned, M total)', () => {
    render(
      <CommandCenterFilters
        value={value()}
        onChange={vi.fn()}
        shown={4}
        total={6}
      />,
    );
    expect(screen.getByText(/showing 4 of 6 reports/i)).toBeInTheDocument();
  });
});
