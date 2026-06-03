import { SYNC_STATUS_TAXONOMY } from '../../shared/lib/statusTaxonomy';
import { Badge } from '../../shared/components/Badge';
import type { OutlookSyncRecordDto } from '../../shared/lib/dtos';

/**
 * Renders a sync record's `SyncStatus` via the §10 `SYNC_STATUS_TAXONOMY`
 * (glyph+text+color, LESSONS §7; unknown→nothing). A `FAILED` record ALSO renders
 * a visible warning showing the server `safeMessage` as Cypress-assertable text —
 * and ONLY `safeMessage`: `failureCode`/`graphEventId`/`traceId` are never
 * rendered (rule #7, no secret/code leak).
 */
export function SyncStatusBadge({ record }: { record: OutlookSyncRecordDto }) {
  const entry = SYNC_STATUS_TAXONOMY[record.status];
  return (
    <div data-cy="sync-status" className="flex flex-col items-start gap-1">
      {entry ? (
        <Badge
          tone={entry.tone}
          icon={entry.icon}
          label={entry.label}
          size="xs"
          dataCy="sync-badge"
        />
      ) : null}
      {record.status === 'FAILED' && record.safeMessage ? (
        <div
          data-cy="sync-failed-warning"
          role="alert"
          className="rounded-md border border-tone-failure-border bg-tone-failure-bg p-3 text-tone-failure-fg"
        >
          <p data-cy="sync-failed-message">{record.safeMessage}</p>
        </div>
      ) : null}
    </div>
  );
}
