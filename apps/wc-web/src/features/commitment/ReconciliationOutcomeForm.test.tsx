import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { ReconciliationOutcomeForm } from './ReconciliationOutcomeForm';
import { useUpdateCommitmentMutation } from './commitmentsApi';
import type { WeeklyCommitmentDto } from '../../shared/lib/dtos';

vi.mock('./commitmentsApi');

function commitment(
  overrides: Partial<WeeklyCommitmentDto> = {},
): WeeklyCommitmentDto {
  return {
    id: 'c-1',
    weeklyPlanId: 'plan-1',
    commitmentKind: 'PLANNED',
    title: 'Ship onboarding',
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

function mockUpdate(
  trigger: ReturnType<typeof vi.fn>,
  state: { isLoading?: boolean } = {},
) {
  vi.mocked(useUpdateCommitmentMutation).mockReturnValue([
    trigger,
    { isLoading: state.isLoading ?? false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useUpdateCommitmentMutation>);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('ReconciliationOutcomeForm (E6 PATCH reconciliationOutcome — completion outcomes only)', () => {
  it('offers_only_the_four_completion_outcomes: the outcome selector offers COMPLETED/PARTIALLY_COMPLETED/BLOCKED/CANCELED and NEVER CARRIED_FORWARD (§3, backend 4.1 — carry-forward is the E12 affordance only)', () => {
    mockUpdate(vi.fn());
    render(
      <ReconciliationOutcomeForm commitment={commitment()} planId="plan-1" />,
    );

    expect(
      screen.getByRole('option', { name: /^completed$/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('option', { name: /partially completed/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('option', { name: /^blocked$/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('option', { name: /^canceled$/i }),
    ).toBeInTheDocument();
    // The single-outcome rule: CARRIED_FORWARD is unreachable from the form.
    expect(
      screen.queryByRole('option', { name: /carried.forward/i }),
    ).toBeNull();
  });

  it('records_outcome_via_updateCommitment_patch: selecting an outcome + note and submitting dispatches updateCommitment({id, planId, patch:{reconciliationOutcome, outcomeNote}}) — the existing E6 PATCH', async () => {
    const user = userEvent.setup();
    const trigger = vi.fn(() => ({
      unwrap: () => Promise.resolve({ id: 'c-1' }),
    }));
    mockUpdate(trigger);

    render(
      <ReconciliationOutcomeForm commitment={commitment()} planId="plan-1" />,
    );
    await user.selectOptions(screen.getByLabelText(/outcome/i), 'COMPLETED');
    await user.type(screen.getByLabelText(/note/i), 'Shipped onboarding v1.');
    await user.click(screen.getByRole('button', { name: /record outcome/i }));

    expect(trigger).toHaveBeenCalledTimes(1);
    expect(trigger).toHaveBeenCalledWith({
      id: 'c-1',
      planId: 'plan-1',
      patch: expect.objectContaining({
        reconciliationOutcome: 'COMPLETED',
        outcomeNote: 'Shipped onboarding v1.',
      }),
    });
  });

  it('surfaces_server_safeMessage_verbatim: a 409/422 rejection renders the parsed safeMessage + per-field fieldErrors — the client never re-derives validity (LESSONS §11)', async () => {
    const user = userEvent.setup();
    const trigger = vi.fn(() => ({
      unwrap: () =>
        Promise.reject({
          safeMessage: 'Outcomes can only be recorded while reconciling.',
          code: 'ILLEGAL_STATE_TRANSITION',
          fieldErrors: [
            {
              field: 'reconciliationOutcome',
              code: 'IllegalState',
              message: 'Plan is not reconciling.',
            },
          ],
        }),
    }));
    mockUpdate(trigger);

    render(
      <ReconciliationOutcomeForm commitment={commitment()} planId="plan-1" />,
    );
    await user.selectOptions(screen.getByLabelText(/outcome/i), 'BLOCKED');
    await user.click(screen.getByRole('button', { name: /record outcome/i }));

    expect(
      await screen.findByText(
        'Outcomes can only be recorded while reconciling.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByText('Plan is not reconciling.')).toBeInTheDocument();
  });
});
