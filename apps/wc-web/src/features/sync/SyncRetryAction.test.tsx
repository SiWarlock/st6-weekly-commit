import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { SyncRetryAction } from './SyncRetryAction';
import { useRetrySyncMutation } from './syncApi';
import type {
  OutlookSyncRecordDto,
  AllowedAction,
} from '../../shared/lib/dtos';

vi.mock('./syncApi');

function record(allowedActions: AllowedAction[]): OutlookSyncRecordDto {
  return {
    id: 'sync-1',
    ownerEmployeeId: 'emp-1',
    relatedType: 'WEEKLY_PLAN',
    relatedId: 'plan-1',
    eventKind: 'IC_PLANNING',
    status: 'FAILED',
    safeMessage: 'Calendar sync failed; you can retry.',
    retryCount: 1,
    allowedActions,
    version: 0,
  };
}

function mockRetry(trigger: ReturnType<typeof vi.fn>) {
  vi.mocked(useRetrySyncMutation).mockReturnValue([
    trigger,
    { isLoading: false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useRetrySyncMutation>);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('SyncRetryAction (server-gated E23 manual retry)', () => {
  it('enabled_only_when_RETRY_SYNC_allowed: renders iff RETRY_SYNC ∈ record.allowedActions[]; absent → not rendered (never re-derived; mirrors LockButton)', () => {
    mockRetry(vi.fn());

    const { rerender } = render(
      <SyncRetryAction record={record(['RETRY_SYNC'])} />,
    );
    expect(
      screen.getByRole('button', { name: /retry sync/i }),
    ).toBeInTheDocument();

    rerender(<SyncRetryAction record={record([])} />);
    expect(screen.queryByRole('button', { name: /retry sync/i })).toBeNull();
  });

  it('retry_invokes_E23_and_surfaces_error: a click invokes retrySync({syncRecordId}); a rejection renders the parsed safeMessage verbatim (no optimistic flip)', async () => {
    const user = userEvent.setup();
    const trigger = vi.fn(() => ({
      unwrap: () =>
        Promise.reject({
          safeMessage: 'Only failed syncs can be retried.',
          code: 'ILLEGAL_STATE_TRANSITION',
          fieldErrors: [],
        }),
    }));
    mockRetry(trigger);

    render(<SyncRetryAction record={record(['RETRY_SYNC'])} />);
    await user.click(screen.getByRole('button', { name: /retry sync/i }));

    expect(trigger).toHaveBeenCalledWith({ syncRecordId: 'sync-1' });
    expect(
      await screen.findByText('Only failed syncs can be retried.'),
    ).toBeInTheDocument();
  });
});
