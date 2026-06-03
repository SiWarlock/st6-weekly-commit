import { render, screen, within } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { CommitmentList } from './CommitmentList';
import type { WeeklyCommitmentDto, AllowedAction } from '../../shared/lib/dtos';

function commitment(
  overrides: Partial<WeeklyCommitmentDto> & { id: string },
): WeeklyCommitmentDto {
  return {
    weeklyPlanId: 'plan-1',
    commitmentKind: 'PLANNED',
    title: `Commitment ${overrides.id}`,
    priority: 'P1',
    workType: 'STRATEGIC',
    confidence: 'HIGH',
    alignmentStatus: 'ALIGNED',
    hasUnresolvedDispute: false,
    allowedActions: [],
    version: 0,
    ...overrides,
  };
}

describe('CommitmentList (badges + allowedActions-driven row controls, REQ-UX-002)', () => {
  it('commitment_list_badges_planned_vs_unplanned_vs_carryforward: an UNPLANNED row shows the kind badge; a carried-forward row shows the CARRY_FORWARD risk badge; a plain planned row shows neither', () => {
    render(
      <CommitmentList
        planState="DRAFT"
        commitments={[
          commitment({ id: 'c-1', title: 'Plain planned' }),
          commitment({
            id: 'c-2',
            title: 'Hotfix the build',
            commitmentKind: 'UNPLANNED',
            workType: 'UNPLANNED',
          }),
          commitment({
            id: 'c-3',
            title: 'Carried over',
            carryForwardSourceCommitmentId: 'prev-week-c',
          }),
        ]}
      />,
    );

    const rowOf = (title: string) =>
      screen
        .getByText(title)
        .closest('[data-cy="commitment-row"]') as HTMLElement;

    // Plain planned: no kind badge, no carry-forward badge.
    expect(within(rowOf('Plain planned')).queryByText(/unplanned/i)).toBeNull();
    expect(
      within(rowOf('Plain planned')).queryByText(/carry-forward/i),
    ).toBeNull();

    // UNPLANNED: a kind badge labelled Unplanned (generic Badge, accent tone).
    expect(
      within(rowOf('Hotfix the build')).getByText(/unplanned/i),
    ).toBeInTheDocument();

    // Carried-forward: the CARRY_FORWARD risk badge.
    expect(
      within(rowOf('Carried over')).getByText(/carry-forward/i),
    ).toBeInTheDocument();
  });

  it('commitment_title_renders_xss_escaped: a title containing HTML renders as inert escaped text, never as a DOM element (REQ-S-005)', () => {
    const xss = '<img src=x onerror=alert(1)>';
    const { container } = render(
      <CommitmentList
        planState="DRAFT"
        commitments={[commitment({ id: 'c-1', title: xss })]}
      />,
    );
    // React default-escaping: the payload is literal text, never a live <img>.
    expect(container.querySelector('img')).toBeNull();
    expect(screen.getByText(xss)).toBeInTheDocument();
  });

  it('row_controls_follow_allowedActions: a row control renders ONLY when its action is in that commitment allowedActions[] AND a handler is provided (never re-derived)', () => {
    const onCarryForward = vi.fn();
    const withAction = (actions: AllowedAction[]) =>
      commitment({ id: 'c-1', title: 'Gated row', allowedActions: actions });

    // CARRY_FORWARD present → the control renders.
    const { rerender } = render(
      <CommitmentList
        planState="RECONCILING"
        commitments={[withAction(['CARRY_FORWARD'])]}
        onCarryForward={onCarryForward}
      />,
    );
    expect(
      screen.getByRole('button', { name: /carry forward/i }),
    ).toBeInTheDocument();

    // CARRY_FORWARD absent → the control is NOT rendered (adversarial).
    rerender(
      <CommitmentList
        planState="RECONCILING"
        commitments={[withAction(['COMMENT'])]}
        onCarryForward={onCarryForward}
      />,
    );
    expect(screen.queryByRole('button', { name: /carry forward/i })).toBeNull();
  });
});
