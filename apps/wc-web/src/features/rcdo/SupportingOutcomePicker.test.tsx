import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { SupportingOutcomePicker } from './SupportingOutcomePicker';
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

function mockRcdo() {
  vi.mocked(useGetRcdoQuery).mockReturnValue({
    data: TREE,
    isLoading: false,
    isError: false,
    error: undefined,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useGetRcdoQuery>);
}

function breadcrumb(): HTMLElement {
  return document.querySelector('[data-cy="so-breadcrumb"]') as HTMLElement;
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('SupportingOutcomePicker (controlled SO selection + RC→DO→SO breadcrumb, REQ-UX-001)', () => {
  it('so_picker_emits_id_and_breadcrumb: selecting an outcome emits its supportingOutcomeId and renders the RC→DO→SO breadcrumb', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    mockRcdo();

    const { rerender } = render(
      <SupportingOutcomePicker value={null} onChange={onChange} />,
    );

    // Selecting an outcome emits exactly its id (the value 9.6's form will own).
    await user.click(screen.getByRole('button', { name: 'Ship onboarding' }));
    expect(onChange).toHaveBeenCalledWith('so-1-1');

    // Once the controlled value reflects the selection, the breadcrumb shows the
    // full Rally Cry → Defining Objective → Supporting Outcome path.
    rerender(<SupportingOutcomePicker value="so-1-1" onChange={onChange} />);
    const bc = breadcrumb();
    expect(within(bc).getByText('Win the quarter')).toBeInTheDocument();
    expect(within(bc).getByText('Objective One')).toBeInTheDocument();
    expect(within(bc).getByText('Ship onboarding')).toBeInTheDocument();
  });

  it('so_picker_is_controlled: the rendered selection follows the value prop (no internal source of truth)', () => {
    mockRcdo();
    const onChange = vi.fn();

    const { rerender } = render(
      <SupportingOutcomePicker value="so-2-1" onChange={onChange} />,
    );
    let bc = breadcrumb();
    expect(within(bc).getByText('Objective Two')).toBeInTheDocument();
    expect(within(bc).getByText('Grow revenue')).toBeInTheDocument();

    // Change ONLY the prop → the displayed selection changes accordingly.
    rerender(<SupportingOutcomePicker value="so-1-2" onChange={onChange} />);
    bc = breadcrumb();
    expect(within(bc).getByText('Objective One')).toBeInTheDocument();
    expect(within(bc).getByText('Reduce churn')).toBeInTheDocument();
    expect(within(bc).queryByText('Grow revenue')).toBeNull();
  });
});
