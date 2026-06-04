import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { DeleteCommitmentButton } from './DeleteCommitmentButton';
import { useDeleteCommitmentMutation } from './commitmentsApi';
import type { WeeklyCommitmentDto } from '../../shared/lib/dtos';

vi.mock('./commitmentsApi');

function commitment(): WeeklyCommitmentDto {
  return {
    id: 'c-1',
    weeklyPlanId: 'plan-1',
    commitmentKind: 'PLANNED',
    title: 'Ship onboarding',
    priority: 'P1',
    workType: 'STRATEGIC',
    confidence: 'HIGH',
    alignmentStatus: 'NEEDS_REVIEW',
    allowedActions: [],
    version: 0,
  };
}

function mockDelete(trigger: ReturnType<typeof vi.fn>) {
  vi.mocked(useDeleteCommitmentMutation).mockReturnValue([
    trigger,
    { isLoading: false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useDeleteCommitmentMutation>);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('DeleteCommitmentButton (E7 delete with a non-blocking in-app confirm)', () => {
  it('delete_invokes_deleteCommitment_after_confirm: the first click asks for confirmation (does NOT delete); confirming dispatches deleteCommitment({id, planId}) (E7) — no window.confirm, no optimistic removal', async () => {
    const user = userEvent.setup();
    const trigger = vi.fn(() => ({ unwrap: () => Promise.resolve(undefined) }));
    mockDelete(trigger);

    render(
      <DeleteCommitmentButton commitment={commitment()} planId="plan-1" />,
    );

    // First click → confirm step, NOT a delete.
    await user.click(screen.getByRole('button', { name: /^delete$/i }));
    expect(trigger).not.toHaveBeenCalled();

    // Confirm → E7 fires.
    await user.click(screen.getByRole('button', { name: /confirm delete/i }));
    expect(trigger).toHaveBeenCalledWith({ id: 'c-1', planId: 'plan-1' });
  });

  it('cancel_aborts_the_delete_and_re_arms: Delete → Cancel does NOT call deleteCommitment and returns the control to its initial "Delete" state (the two-step escape hatch)', async () => {
    const user = userEvent.setup();
    const trigger = vi.fn(() => ({ unwrap: () => Promise.resolve(undefined) }));
    mockDelete(trigger);

    render(
      <DeleteCommitmentButton commitment={commitment()} planId="plan-1" />,
    );

    await user.click(screen.getByRole('button', { name: /^delete$/i }));
    // Confirm step is showing; cancel it.
    await user.click(screen.getByRole('button', { name: /cancel/i }));

    expect(trigger).not.toHaveBeenCalled();
    // Back to the initial, re-armable Delete control (confirm step gone).
    expect(
      screen.getByRole('button', { name: /^delete$/i }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /confirm delete/i }),
    ).toBeNull();
  });

  it('delete_post_lock_renders_LOCKED_BASELINE_EDIT_verbatim: a 409 from delete renders the server safeMessage verbatim (server-authoritative, LESSONS §11)', async () => {
    const user = userEvent.setup();
    const trigger = vi.fn(() => ({
      unwrap: () =>
        Promise.reject({
          safeMessage: 'Only draft commitments can be removed.',
          code: 'LOCKED_BASELINE_EDIT',
          fieldErrors: [],
        }),
    }));
    mockDelete(trigger);

    render(
      <DeleteCommitmentButton commitment={commitment()} planId="plan-1" />,
    );
    await user.click(screen.getByRole('button', { name: /^delete$/i }));
    await user.click(screen.getByRole('button', { name: /confirm delete/i }));

    expect(
      await screen.findByText('Only draft commitments can be removed.'),
    ).toBeInTheDocument();
  });
});
