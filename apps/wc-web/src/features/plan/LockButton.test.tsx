import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { LockButton } from './LockButton';
import { useLockPlanMutation } from './plansApi';
import type { WeeklyPlanDto, AllowedAction } from '../../shared/lib/dtos';

vi.mock('./plansApi');

function plan(allowedActions: AllowedAction[]): WeeklyPlanDto {
  return {
    id: 'plan-1',
    employeeId: 'emp-1',
    employeeDisplayName: 'Ivy Chen',
    weekStartDate: '2026-06-01',
    weekEndDate: '2026-06-07',
    state: 'DRAFT',
    plannedCount: 1,
    unplannedCount: 0,
    commitments: [],
    managerReview: null,
    allowedActions,
    version: 1,
  };
}

function mockLock(trigger: ReturnType<typeof vi.fn>) {
  vi.mocked(useLockPlanMutation).mockReturnValue([
    trigger,
    { isLoading: false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useLockPlanMutation>);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('LockButton (server-authoritative lock affordance, rule #1)', () => {
  it('lock_button_enabled_only_when_LOCK_in_allowedActions: enabled when LOCK ∈ allowedActions[], disabled when absent (never re-derived client-side)', () => {
    mockLock(vi.fn());

    const { rerender } = render(<LockButton plan={plan(['LOCK'])} />);
    expect(screen.getByRole('button', { name: /lock/i })).toBeEnabled();

    rerender(<LockButton plan={plan([])} />);
    expect(screen.getByRole('button', { name: /lock/i })).toBeDisabled();
  });

  it('lock_blocked_renders_UNLINKED_PLANNED_COMMITMENT_safeMessage_and_fieldErrors: a 409 surfaces the server safeMessage + per-field fieldErrors verbatim', async () => {
    const user = userEvent.setup();
    const trigger = vi.fn(() => ({
      unwrap: () =>
        Promise.reject({
          safeMessage:
            'Every commitment must link to a Supporting Outcome before locking.',
          code: 'UNLINKED_PLANNED_COMMITMENT',
          fieldErrors: [
            {
              field: 'commitments[0].supportingOutcomeId',
              code: 'Required',
              message: 'Link a Supporting Outcome.',
            },
          ],
        }),
    }));
    mockLock(trigger);

    render(<LockButton plan={plan(['LOCK'])} />);
    await user.click(screen.getByRole('button', { name: /lock/i }));

    expect(
      await screen.findByText(
        'Every commitment must link to a Supporting Outcome before locking.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByText('Link a Supporting Outcome.')).toBeInTheDocument();
  });

  it('lock_blocked_renders_EMPTY_PLAN_LOCK: an empty-plan lock rejection surfaces its safeMessage (rule #1)', async () => {
    const user = userEvent.setup();
    const trigger = vi.fn(() => ({
      unwrap: () =>
        Promise.reject({
          safeMessage: 'Add at least one commitment before locking the week.',
          code: 'EMPTY_PLAN_LOCK',
          fieldErrors: [],
        }),
    }));
    mockLock(trigger);

    render(<LockButton plan={plan(['LOCK'])} />);
    await user.click(screen.getByRole('button', { name: /lock/i }));

    expect(
      await screen.findByText(
        'Add at least one commitment before locking the week.',
      ),
    ).toBeInTheDocument();
  });
});
