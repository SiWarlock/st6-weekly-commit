import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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
vi.mock('./DeleteCommitmentButton', () => ({
  DeleteCommitmentButton: ({
    commitment,
  }: {
    commitment: WeeklyCommitmentDto;
  }) => <div data-testid="delete-stub" data-id={commitment.id} />,
}));
vi.mock('../dispute/DisputePanel', () => ({
  DisputePanel: ({ commitment }: { commitment: WeeklyCommitmentDto }) => (
    <div data-testid="dispute-panel-stub" data-id={commitment.id} />
  ),
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
    // Scoped to the kind-badge specifically — the ST.3 WorkTypeTag also renders
    // "Unplanned" for workType=UNPLANNED, so a bare getByText would be ambiguous.
    expect(
      rowOf('Hotfix the build').querySelector('[data-cy="kind-badge"]'),
    ).toHaveTextContent(/unplanned/i);

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

  it('edit_delete_controls_only_in_DRAFT: per-row Edit + Delete render only when planState===DRAFT; absent in LOCKED/RECONCILING/RECONCILED (server-authoritative state gate, §3, 9.7b)', () => {
    const onEdit = vi.fn();
    const rows = [commitment({ id: 'c-1', title: 'Editable' })];

    const { rerender } = render(
      <CommitmentList
        planState="DRAFT"
        planId="plan-1"
        commitments={rows}
        onEdit={onEdit}
      />,
    );
    expect(screen.getByRole('button', { name: /edit/i })).toBeInTheDocument();
    expect(screen.getByTestId('delete-stub')).toBeInTheDocument();

    for (const state of ['LOCKED', 'RECONCILING', 'RECONCILED'] as const) {
      rerender(
        <CommitmentList
          planState={state}
          planId="plan-1"
          commitments={rows}
          onEdit={onEdit}
        />,
      );
      expect(screen.queryByRole('button', { name: /edit/i })).toBeNull();
      expect(screen.queryByTestId('delete-stub')).toBeNull();
    }
  });

  it('edit_button_invokes_onEdit_with_the_commitment: clicking a DRAFT row Edit calls onEdit(commitment) (WeeklyPlanView owns the editing state)', async () => {
    const user = userEvent.setup();
    const onEdit = vi.fn();
    render(
      <CommitmentList
        planState="DRAFT"
        planId="plan-1"
        commitments={[commitment({ id: 'c-1', title: 'Editable' })]}
        onEdit={onEdit}
      />,
    );
    await user.click(screen.getByRole('button', { name: /edit/i }));
    expect(onEdit).toHaveBeenCalledWith(expect.objectContaining({ id: 'c-1' }));
  });
});

// Step-7.5 reachability (9.11b): each commitment row mounts a COMMENT-gated
// comment thread wired to {COMMITMENT, commitmentId}. The thread is collapsed by
// default (no query fires at render), so this renders the real CommentThread.
describe('CommitmentList → CommentThread wiring (9.11b reachability)', () => {
  it('comment_thread_reachable_per_commitment_gated: a row whose commitment allows COMMENT mounts a CommentThread for {COMMITMENT, commitmentId}; a row without COMMENT mounts none', () => {
    const { container } = render(
      <CommitmentList
        planState="LOCKED"
        planId="plan-1"
        commitments={[
          commitment({
            id: 'c-1',
            title: 'Has comments',
            allowedActions: ['COMMENT'],
          }),
          commitment({ id: 'c-2', title: 'No comments', allowedActions: [] }),
        ]}
      />,
    );

    expect(
      container.querySelector(
        '[data-cy="comment-thread"][data-target-type="COMMITMENT"][data-target-id="c-1"]',
      ),
    ).not.toBeNull();
    expect(
      container.querySelector(
        '[data-cy="comment-thread"][data-target-id="c-2"]',
      ),
    ).toBeNull();
  });
});

// Step-7.5 reachability (ST.3): each commitment row renders the four chess atoms
// (priority / workType / confidence / alignment) skinned per the commitment.
describe('CommitmentList → chess atom wiring (ST.3 reachability)', () => {
  it('commitment_list_renders_chess_atoms_per_row: a row renders PriorityTag + WorkTypeTag + ConfidenceMeter + AlignmentChip with the commitment values', () => {
    render(
      <CommitmentList
        planState="DRAFT"
        planId="plan-1"
        commitments={[
          commitment({
            id: 'c-1',
            title: 'Strategic bet',
            priority: 'P0',
            workType: 'BLOCKER',
            confidence: 'LOW',
            alignmentStatus: 'MISALIGNED',
          }),
        ]}
      />,
    );

    const row = screen
      .getByText('Strategic bet')
      .closest('[data-cy="commitment-row"]') as HTMLElement;

    const priority = row.querySelector('[data-cy="priority-tag"]');
    expect(priority).toHaveTextContent('P0');
    const workType = row.querySelector('[data-cy="worktype-tag"]');
    expect(workType).toHaveTextContent('Blocker');
    const confidence = row.querySelector('[data-cy="confidence-meter"]');
    expect(confidence).toHaveTextContent('Low');
    const alignment = row.querySelector('[data-cy="alignment-chip"]');
    expect(alignment).toHaveTextContent('Misaligned');
  });
});

// ST.4 surface skin: the commitment card carries the Cadence `.wc-card` skin
// (hairline inset elevation) + an EARNED left-accent (unplanned → accent).
describe('CommitmentList → card surface skin (ST.4)', () => {
  const rowOf = (title: string) =>
    screen
      .getByText(title)
      .closest('[data-cy="commitment-row"]') as HTMLElement;

  it('commitment_card_has_cadence_surface_skin: a card carries shadow-hairline + rounded-lg + border-border + bg-surface (token-native)', () => {
    render(
      <CommitmentList
        planState="LOCKED"
        planId="plan-1"
        commitments={[commitment({ id: 'c-1', title: 'Skinned card' })]}
      />,
    );
    const card = rowOf('Skinned card');
    expect(card.className).toContain('shadow-hairline');
    expect(card.className).toContain('rounded-lg');
    expect(card.className).toContain('border-border');
    expect(card.className).toContain('bg-surface');
  });

  it('unplanned_commitment_earns_accent_left_border: an UNPLANNED card has border-l-2 border-l-tone-accent-solid; a PLANNED card has no left-accent (disputed→failure is deferred to the B.6 edit)', () => {
    render(
      <CommitmentList
        planState="LOCKED"
        planId="plan-1"
        commitments={[
          commitment({
            id: 'c-1',
            title: 'Unplanned work',
            commitmentKind: 'UNPLANNED',
            workType: 'UNPLANNED',
          }),
          commitment({ id: 'c-2', title: 'Planned work' }),
        ]}
      />,
    );
    const unplanned = rowOf('Unplanned work');
    expect(unplanned.className).toContain('border-l-2');
    expect(unplanned.className).toContain('border-l-tone-accent-solid');

    // PLANNED rows earn no left-accent.
    const planned = rowOf('Planned work');
    expect(planned.className).not.toMatch(/border-l-/);
  });
});

// ST.5a IC composition: lock glyph on locked titles + UNPLANNED redundancy fix.
describe('CommitmentList → ST.5a composition (lock glyph, UNPLANNED redundancy)', () => {
  const rowOf = (title: string) =>
    screen
      .getByText(title)
      .closest('[data-cy="commitment-row"]') as HTMLElement;

  it('commitment_card_shows_lock_glyph_when_not_draft: a LOCKED plan prepends a lock glyph to each commitment title; a DRAFT plan shows none (§3 baseline-immutability, communicated visually)', () => {
    const { unmount } = render(
      <CommitmentList
        planState="LOCKED"
        planId="plan-1"
        commitments={[commitment({ id: 'c-1', title: 'Locked work' })]}
      />,
    );
    expect(
      rowOf('Locked work').querySelector('[data-cy="lock-glyph"]'),
    ).not.toBeNull();
    unmount();

    render(
      <CommitmentList
        planState="DRAFT"
        planId="plan-1"
        commitments={[commitment({ id: 'c-1', title: 'Draft work' })]}
      />,
    );
    expect(
      rowOf('Draft work').querySelector('[data-cy="lock-glyph"]'),
    ).toBeNull();
  });

  it('unplanned_commitment_suppresses_worktype_tag: an UNPLANNED commitment renders the accent kind-badge and NO WorkTypeTag (no second "Unplanned"); a non-UNPLANNED commitment renders its WorkTypeTag', () => {
    render(
      <CommitmentList
        planState="LOCKED"
        planId="plan-1"
        commitments={[
          commitment({
            id: 'c-1',
            title: 'Unplanned work',
            commitmentKind: 'UNPLANNED',
            workType: 'UNPLANNED',
          }),
          commitment({
            id: 'c-2',
            title: 'Strategic work',
            workType: 'STRATEGIC',
          }),
        ]}
      />,
    );

    const unplanned = rowOf('Unplanned work');
    expect(unplanned.querySelector('[data-cy="kind-badge"]')).not.toBeNull();
    expect(unplanned.querySelector('[data-cy="worktype-tag"]')).toBeNull();

    // A non-UNPLANNED commitment keeps its WorkTypeTag.
    const strategic = rowOf('Strategic work');
    expect(strategic.querySelector('[data-cy="worktype-tag"]')).not.toBeNull();
  });
});

// ST.5b card-display completion: read-only RcdoBreadcrumb per card + the
// RECONCILED OutcomePill + muting.
describe('CommitmentList → ST.5b card display (RcdoBreadcrumb, OutcomePill, muting)', () => {
  const rowOf = (title: string) =>
    screen
      .getByText(title)
      .closest('[data-cy="commitment-row"]') as HTMLElement;

  it('commitment_list_mounts_breadcrumb_per_card: a linked commitment renders its RC › DO › SO breadcrumb; an unlinked commitment renders the missing-SO warning', () => {
    render(
      <CommitmentList
        planState="DRAFT"
        planId="plan-1"
        commitments={[
          commitment({
            id: 'c-1',
            title: 'Linked work',
            supportingOutcomeBreadcrumb: {
              rallyCryId: 'rc-1',
              rallyCryTitle: 'Win the quarter',
              definingObjectiveId: 'do-1',
              definingObjectiveTitle: 'Ship v2',
              supportingOutcomeId: 'so-1',
              supportingOutcomeTitle: 'Onboarding flow',
            },
          }),
          commitment({ id: 'c-2', title: 'Unlinked work' }),
        ]}
      />,
    );

    expect(
      rowOf('Linked work').querySelector('[data-cy="rcdo-breadcrumb"]'),
    ).not.toBeNull();
    expect(
      rowOf('Unlinked work').querySelector(
        '[data-cy="rcdo-breadcrumb-missing"]',
      ),
    ).not.toBeNull();
  });

  it('reconciled_card_is_muted_and_shows_outcome_pill: a RECONCILED commitment with a reconciliationOutcome renders the OutcomePill + a readOnly-muted card; a DRAFT card has neither', () => {
    const { unmount } = render(
      <CommitmentList
        planState="RECONCILED"
        planId="plan-1"
        commitments={[
          commitment({
            id: 'c-1',
            title: 'Done work',
            reconciliationOutcome: 'COMPLETED',
          }),
        ]}
      />,
    );
    const reconciled = rowOf('Done work');
    expect(reconciled.querySelector('[data-cy="outcome-pill"]')).not.toBeNull();
    expect(reconciled).toHaveAttribute('data-readonly', 'true');
    unmount();

    render(
      <CommitmentList
        planState="DRAFT"
        planId="plan-1"
        commitments={[commitment({ id: 'c-1', title: 'Draft work' })]}
      />,
    );
    const draftCard = rowOf('Draft work');
    expect(draftCard.querySelector('[data-cy="outcome-pill"]')).toBeNull();
    expect(draftCard).not.toHaveAttribute('data-readonly');
  });
});

// 9.11a — disputes: the failure left-accent + the per-row DisputePanel mount.
describe('CommitmentList → disputes (9.11a)', () => {
  const rowOf = (title: string) =>
    screen
      .getByText(title)
      .closest('[data-cy="commitment-row"]') as HTMLElement;

  function disputed(
    overrides: Partial<WeeklyCommitmentDto> & { id: string },
  ): WeeklyCommitmentDto {
    return commitment({
      ...overrides,
      dispute: {
        id: `disp-${overrides.id}`,
        commitmentId: overrides.id,
        managerEmployeeId: 'mgr-1',
        status: 'OPEN',
        flagType: 'MISALIGNED',
        managerNote: 'Off-strategy.',
        allowedActions: [],
        version: 0,
      },
    });
  }

  it('disputed_row_gets_failure_left_accent: a commitment WITH a dispute renders the failure left-accent; precedence — dispute (failure) wins over UNPLANNED (accent)', () => {
    render(
      <CommitmentList
        planState="LOCKED"
        planId="plan-1"
        commitments={[
          disputed({ id: 'c-1', title: 'Disputed planned' }),
          disputed({
            id: 'c-2',
            title: 'Disputed unplanned',
            commitmentKind: 'UNPLANNED',
            workType: 'UNPLANNED',
          }),
          commitment({ id: 'c-3', title: 'Plain planned' }),
        ]}
      />,
    );
    // dispute → failure left-accent.
    expect(rowOf('Disputed planned').className).toContain(
      'border-l-tone-failure-solid',
    );
    // dispute wins over unplanned (the disputed-unplanned row is failure, not accent).
    expect(rowOf('Disputed unplanned').className).toContain(
      'border-l-tone-failure-solid',
    );
    expect(rowOf('Disputed unplanned').className).not.toContain(
      'border-l-tone-accent-solid',
    );
    // no dispute, planned → no left-accent.
    expect(rowOf('Plain planned').className).not.toContain('border-l-');
  });

  it('dispute_panel_mounts_per_row: every row mounts the DisputePanel (the open-form/stepper container, server-gated within)', () => {
    render(
      <CommitmentList
        planState="LOCKED"
        planId="plan-1"
        commitments={[
          commitment({ id: 'c-1', title: 'Row one' }),
          disputed({ id: 'c-2', title: 'Row two' }),
        ]}
      />,
    );
    const panels = document.querySelectorAll(
      '[data-testid="dispute-panel-stub"]',
    );
    expect(panels).toHaveLength(2);
    expect(
      rowOf('Row two').querySelector('[data-testid="dispute-panel-stub"]'),
    ).not.toBeNull();
  });
});

describe('CommitmentList → ST.8c card fidelity (description + chips-above-SO)', () => {
  const linked: WeeklyCommitmentDto['supportingOutcomeBreadcrumb'] = {
    rallyCryId: 'rc-1',
    rallyCryTitle: 'Win the quarter',
    definingObjectiveId: 'do-1',
    definingObjectiveTitle: 'Ship v2',
    supportingOutcomeId: 'so-1',
    supportingOutcomeTitle: 'Onboarding flow',
  };

  it('commitment_card_renders_description: a commitment WITH a description renders the plain description line (React-escaped, below the title)', () => {
    render(
      <CommitmentList
        planState="DRAFT"
        planId="plan-1"
        commitments={[
          commitment({
            id: 'c-1',
            description: 'Cut time-to-first-value below a week.',
          }),
        ]}
      />,
    );
    expect(
      screen.getByText('Cut time-to-first-value below a week.'),
    ).toBeInTheDocument();
  });

  it('commitment_card_omits_empty_description: a commitment WITHOUT a description renders no description line (clean omit)', () => {
    const { container } = render(
      <CommitmentList
        planState="DRAFT"
        planId="plan-1"
        commitments={[commitment({ id: 'c-1' })]}
      />,
    );
    expect(container.querySelector('[data-cy="commitment-desc"]')).toBeNull();
  });

  it('chips_render_above_so_box: the chess-chips row precedes the RcdoBreadcrumb SO box in the card DOM (§C.3 — the prior order was reversed)', () => {
    const { container } = render(
      <CommitmentList
        planState="DRAFT"
        planId="plan-1"
        commitments={[
          commitment({ id: 'c-1', supportingOutcomeBreadcrumb: linked }),
        ]}
      />,
    );
    const chips = container.querySelector('[data-cy="chess-chips"]');
    const so = container.querySelector('[data-cy="rcdo-breadcrumb"]');
    expect(chips).not.toBeNull();
    expect(so).not.toBeNull();
    expect(
      chips!.compareDocumentPosition(so!) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });
});
