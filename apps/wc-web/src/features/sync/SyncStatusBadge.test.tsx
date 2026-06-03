import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { SyncStatusBadge } from './SyncStatusBadge';
import type { OutlookSyncRecordDto, SyncStatus } from '../../shared/lib/dtos';

function record(
  overrides: Partial<OutlookSyncRecordDto> = {},
): OutlookSyncRecordDto {
  return {
    id: 'sync-1',
    ownerEmployeeId: 'emp-1',
    relatedType: 'WEEKLY_PLAN',
    relatedId: 'plan-1',
    eventKind: 'IC_PLANNING',
    status: 'SYNCED',
    retryCount: 0,
    allowedActions: [],
    version: 0,
    ...overrides,
  };
}

describe('SyncStatusBadge (SyncStatus via SYNC_STATUS_TAXONOMY, LESSONS §7)', () => {
  it('renders_each_SyncStatus_via_taxonomy: known statuses render their taxonomy label; an unknown status renders nothing (no throw)', () => {
    const cases: [SyncStatus, RegExp][] = [
      ['SYNCED', /synced/i],
      ['QUEUED', /queued/i],
      ['SYNCING', /syncing/i],
      ['RETRY_REQUESTED', /retry requested/i],
      ['PENDING_PUBLISH', /pending/i],
    ];
    for (const [status, label] of cases) {
      const { unmount } = render(
        <SyncStatusBadge record={record({ status })} />,
      );
      expect(screen.getByText(label)).toBeInTheDocument();
      unmount();
    }

    // Unknown status → nothing rendered (tolerant lookup, §7).
    const { container } = render(
      <SyncStatusBadge record={record({ status: 'BOGUS' as SyncStatus })} />,
    );
    expect(container.querySelector('[data-cy="sync-badge"]')).toBeNull();
  });

  it('FAILED_renders_warning_with_safeMessage_not_secrets: a FAILED record renders a visible warning showing safeMessage (Cypress-assertable); failureCode/graphEventId/traceId are NOT rendered (rule #7)', () => {
    render(
      <SyncStatusBadge
        record={record({
          status: 'FAILED',
          safeMessage: 'Calendar sync failed; you can retry.',
          failureCode: 'GRAPH_FORBIDDEN',
          graphEventId: 'evt-secret-abc',
          traceId: '00-trace-secret',
        })}
      />,
    );

    // safeMessage is the ONLY error text shown.
    expect(
      screen.getByText('Calendar sync failed; you can retry.'),
    ).toBeInTheDocument();
    expect(
      document.querySelector('[data-cy="sync-failed-warning"]'),
    ).not.toBeNull();
    // rule #7: never leak the code / graph id / trace id.
    expect(screen.queryByText(/GRAPH_FORBIDDEN/)).toBeNull();
    expect(screen.queryByText(/evt-secret-abc/)).toBeNull();
    expect(screen.queryByText(/00-trace-secret/)).toBeNull();
  });
});
