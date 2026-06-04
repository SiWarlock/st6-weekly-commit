import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { CarryForwardButton } from './CarryForwardButton';
import { useCarryForwardMutation } from './commitmentsApi';
import type { WeeklyCommitmentDto, AllowedAction } from '../../shared/lib/dtos';

vi.mock('./commitmentsApi');

function commitment(allowedActions: AllowedAction[]): WeeklyCommitmentDto {
  return {
    id: 'c-1',
    weeklyPlanId: 'plan-1',
    commitmentKind: 'PLANNED',
    title: 'Ship onboarding',
    priority: 'P1',
    workType: 'STRATEGIC',
    confidence: 'HIGH',
    alignmentStatus: 'ALIGNED',
    allowedActions,
    version: 0,
  };
}

function mockCarry(trigger: ReturnType<typeof vi.fn>) {
  vi.mocked(useCarryForwardMutation).mockReturnValue([
    trigger,
    { isLoading: false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useCarryForwardMutation>);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('CarryForwardButton (server-gated E12 affordance — the only path that sets CARRIED_FORWARD)', () => {
  it('enabled_only_when_CARRY_FORWARD_allowed: renders iff CARRY_FORWARD ∈ commitment.allowedActions[]; absent → not rendered (never re-derived client-side)', () => {
    mockCarry(vi.fn());

    const { rerender } = render(
      <CarryForwardButton
        commitment={commitment(['CARRY_FORWARD'])}
        planId="plan-1"
      />,
    );
    expect(
      screen.getByRole('button', { name: /carry forward/i }),
    ).toBeInTheDocument();

    // CARRY_FORWARD absent → the control is not rendered at all.
    rerender(
      <CarryForwardButton
        commitment={commitment(['COMMENT'])}
        planId="plan-1"
      />,
    );
    expect(screen.queryByRole('button', { name: /carry forward/i })).toBeNull();
  });

  it('invokes_E12_and_surfaces_error: a click invokes carryForward({id, planId}); a rejection renders the parsed safeMessage verbatim', async () => {
    const user = userEvent.setup();
    const trigger = vi.fn(() => ({
      unwrap: () =>
        Promise.reject({
          safeMessage: 'A completed commitment cannot be carried forward.',
          code: 'ILLEGAL_STATE_TRANSITION',
          fieldErrors: [],
        }),
    }));
    mockCarry(trigger);

    render(
      <CarryForwardButton
        commitment={commitment(['CARRY_FORWARD'])}
        planId="plan-1"
      />,
    );
    await user.click(screen.getByRole('button', { name: /carry forward/i }));

    expect(trigger).toHaveBeenCalledWith({ id: 'c-1', planId: 'plan-1' });
    expect(
      await screen.findByText(
        'A completed commitment cannot be carried forward.',
      ),
    ).toBeInTheDocument();
  });
});
