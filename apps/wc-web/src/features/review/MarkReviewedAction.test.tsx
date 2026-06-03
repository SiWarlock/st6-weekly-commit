import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { MarkReviewedAction } from './MarkReviewedAction';
import { useMarkReviewedMutation } from './reviewApi';
import type { ManagerReviewDto, AllowedAction } from '../../shared/lib/dtos';

vi.mock('./reviewApi');

function review(allowedActions: AllowedAction[]): ManagerReviewDto {
  return {
    id: 'rev-1',
    weeklyPlanId: 'plan-1',
    managerEmployeeId: 'mgr-1',
    status: 'NOT_REVIEWED',
    reviewDueAt: '2026-06-09T17:00:00Z',
    isOverdue: false,
    unresolvedDisputeCount: 0,
    allowedActions,
    version: 1,
  };
}

function mockMarkReviewed(trigger: ReturnType<typeof vi.fn>) {
  vi.mocked(useMarkReviewedMutation).mockReturnValue([
    trigger,
    { isLoading: false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useMarkReviewedMutation>);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('MarkReviewedAction (reusable server-gated E16 affordance)', () => {
  it('enabled_only_when_MARK_REVIEWED_allowed: renders iff MARK_REVIEWED ∈ review.allowedActions[]; absent → not rendered (mirrors LockButton/CarryForwardButton, never re-derived)', () => {
    mockMarkReviewed(vi.fn());

    const { rerender } = render(
      <MarkReviewedAction review={review(['MARK_REVIEWED'])} />,
    );
    expect(
      screen.getByRole('button', { name: /mark reviewed/i }),
    ).toBeInTheDocument();

    rerender(<MarkReviewedAction review={review([])} />);
    expect(screen.queryByRole('button', { name: /mark reviewed/i })).toBeNull();
  });

  it('submits_optional_summary_and_refetches: submit calls markReviewed({reviewId, planId, body:{summaryNote?}}) (reviewId+planId derived from the review); no optimistic flip', async () => {
    const user = userEvent.setup();
    const trigger = vi.fn(() => ({ unwrap: () => Promise.resolve({}) }));
    mockMarkReviewed(trigger);

    render(<MarkReviewedAction review={review(['MARK_REVIEWED'])} />);
    await user.type(screen.getByLabelText(/summary/i), 'Aligned, nice work.');
    await user.click(screen.getByRole('button', { name: /mark reviewed/i }));

    expect(trigger).toHaveBeenCalledWith({
      reviewId: 'rev-1',
      planId: 'plan-1',
      body: { summaryNote: 'Aligned, nice work.' },
    });
  });

  it('surfaces_server_safeMessage_verbatim: a rejection renders the parsed safeMessage (client never re-derives validity, LESSONS §11)', async () => {
    const user = userEvent.setup();
    const trigger = vi.fn(() => ({
      unwrap: () =>
        Promise.reject({
          safeMessage: 'Only the direct manager can mark this reviewed.',
          code: 'ILLEGAL_STATE_TRANSITION',
          fieldErrors: [],
        }),
    }));
    mockMarkReviewed(trigger);

    render(<MarkReviewedAction review={review(['MARK_REVIEWED'])} />);
    await user.click(screen.getByRole('button', { name: /mark reviewed/i }));

    expect(
      await screen.findByText(
        'Only the direct manager can mark this reviewed.',
      ),
    ).toBeInTheDocument();
  });
});
