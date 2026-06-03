import { render, screen, within } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { CommitmentList } from './CommitmentList';
import type { WeeklyCommitmentDto, AllowedAction } from '../../shared/lib/dtos';

// The per-row reconciliation controls are unit-tested in their own files; here we
// stub them to assert CommitmentList mounts the right one per commitment (the
// either/or gating), without pulling in the RTK Query hooks they own.
vi.mock('./CarryForwardButton', () => ({
  CarryForwardButton: ({ commitment }: { commitment: WeeklyCommitmentDto }) => (
    <div data-testid="cf-stub" data-id={commitment.id} />
  ),
}));
vi.mock('./ReconciliationOutcomeForm', () => ({
  ReconciliationOutcomeForm: ({
    commitment,
  }: {
    commitment: WeeklyCommitmentDto;
  }) => <div data-testid="outcome-stub" data-id={commitment.id} />,
}));

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

describe('CommitmentList (badges + reconciliation row controls, REQ-UX-002)', () => {
  it('commitment_list_badges_planned_vs_unplanned_vs_carryforward: an UNPLANNED row shows the kind badge; a carried-forward row shows the CARRY_FORWARD risk badge; a plain planned row shows neither', () => {
    render(
      <CommitmentList
        planState="DRAFT"
        planId="plan-1"
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
        planId="plan-1"
        commitments={[commitment({ id: 'c-1', title: xss })]}
      />,
    );
    // React default-escaping: the payload is literal text, never a live <img>.
    expect(container.querySelector('img')).toBeNull();
    expect(screen.getByText(xss)).toBeInTheDocument();
  });

  it('reconciling_unresolved_row_mounts_both_outcome_form_and_carry_control: an unresolved RECONCILING commitment WITH CARRY_FORWARD mounts BOTH the outcome form and the carry control (the mutually-exclusive choice set: 4 PATCH outcomes + E12); one WITHOUT CARRY_FORWARD mounts the outcome form only; DRAFT mounts neither — gates are independent (no cross-condition)', () => {
    const withActions = (id: string, actions: AllowedAction[]) =>
      commitment({ id, title: `Row ${id}`, allowedActions: actions });

    const { rerender } = render(
      <CommitmentList
        planState="RECONCILING"
        planId="plan-1"
        commitments={[
          withActions('c-1', ['CARRY_FORWARD']),
          withActions('c-2', []),
        ]}
      />,
    );
    const rowOf = (id: string) =>
      screen
        .getByText(`Row ${id}`)
        .closest('[data-cy="commitment-row"]') as HTMLElement;

    // Unresolved + CARRY_FORWARD → BOTH controls coexist (record OR carry forward;
    // recording either resolves the commitment server-side → both vanish on refetch).
    expect(within(rowOf('c-1')).getByTestId('cf-stub')).toBeInTheDocument();
    expect(
      within(rowOf('c-1')).getByTestId('outcome-stub'),
    ).toBeInTheDocument();

    // Unresolved, no CARRY_FORWARD → the outcome form only (carry control absent).
    expect(
      within(rowOf('c-2')).getByTestId('outcome-stub'),
    ).toBeInTheDocument();
    expect(within(rowOf('c-2')).queryByTestId('cf-stub')).toBeNull();

    // DRAFT → no reconciliation controls at all.
    rerender(
      <CommitmentList
        planState="DRAFT"
        planId="plan-1"
        commitments={[withActions('c-1', ['CARRY_FORWARD'])]}
      />,
    );
    expect(screen.queryByTestId('cf-stub')).toBeNull();
    expect(screen.queryByTestId('outcome-stub')).toBeNull();
  });

  it('reconciling_row_with_recorded_outcome_mounts_neither: a commitment that already has a reconciliationOutcome shows no outcome form (and no carry control absent CARRY_FORWARD)', () => {
    render(
      <CommitmentList
        planState="RECONCILING"
        planId="plan-1"
        commitments={[
          commitment({
            id: 'c-1',
            title: 'Already done',
            reconciliationOutcome: 'COMPLETED',
            allowedActions: [],
          }),
        ]}
      />,
    );
    expect(screen.queryByTestId('outcome-stub')).toBeNull();
    expect(screen.queryByTestId('cf-stub')).toBeNull();
  });
});
