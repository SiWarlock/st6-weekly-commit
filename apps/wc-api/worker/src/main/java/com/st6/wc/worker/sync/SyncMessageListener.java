package com.st6.wc.worker.sync;

import com.st6.wc.enums.SyncStatus;
import com.st6.wc.sns.payload.SyncJobPointer;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import io.awspring.cloud.sqs.annotation.SqsListener;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumes the {@code wc-sync} queue (Wave-2 s8, §10): deserializes the pointer-only {@link
 * SyncJobPointer}, atomically <strong>claims</strong> the {@link OutlookCalendarSyncRecord} by
 * {@code syncRecordId}, and drives the calendar op through the {@link GraphCalendarPort}.
 *
 * <p>Pipeline (brief 104 — atomic claim replaces the old read-then-{@code setStatus(SYNCING)}
 * guard's TOCTOU window): {@link OutlookCalendarSyncRecordRepository#claimForSync} CAS-transitions
 * a claimable row ({@code QUEUED}/{@code RETRY_REQUESTED}, or a stale-lease {@code SYNCING}) →
 * {@code SYNCING} in one statement. {@code claimed == 0} → no-op ACK (no claimable record — another
 * worker won, terminal, or an active claimer). {@code claimed == 1} → reload the fresh {@code
 * SYNCING} instance → if already in Graph ({@code graphEventId != null}) reconcile to {@code
 * SYNCED} (no double-create) → else port → {@code SYNCED} (+ {@code graphEventId} + {@code
 * processedAt}); on failure {@code → FAILED} (+ a fixed non-PII {@code failureCode}/{@code
 * safeMessage} + {@code retryCount++}) then rethrow a sanitized {@link SyncProcessingException} so
 * SQS redrives to the DLQ after {@code maxReceiveCount}. The DB serializes concurrent deliveries,
 * so exactly one claims → no {@code @Version} race, no duplicate Graph events; the sole claimer's
 * FAILED save can't race.
 *
 * <p><strong>Rule #7:</strong> only the pointer crosses the wire; the persisted failure fields are
 * fixed non-PII constants (never the raw Graph error); logs are ids-only; the rethrow carries no
 * cause-chain (so the framework's redrive logging can't surface the raw error). No
 * {@code @Transactional} wraps the Graph call — each save is its own transaction, so a slow/failed
 * network call never holds a DB transaction open.
 *
 * <p>Active only when {@code app.sqs.queue-url} is configured (deployed {@code aws}) — local/demo/
 * test don't poll a non-existent queue (LESSONS §43 pattern).
 */
@Component
@ConditionalOnProperty("app.sqs.queue-url")
public class SyncMessageListener {

  private static final Logger log = LoggerFactory.getLogger(SyncMessageListener.class);

  /** Fixed, non-PII failure detail (rule #7 — never the raw Graph/exception message). */
  private static final String FAILURE_CODE = "GRAPH_SYNC_FAILED";

  private static final String SAFE_MESSAGE = "Calendar sync failed; it will be retried.";

  private final OutlookCalendarSyncRecordRepository syncRecords;
  private final GraphCalendarPort graphPort;
  private final Clock clock;

  /**
   * Lease window for the stale-{@code SYNCING} reclaim (brief 104): a {@code SYNCING} row whose
   * {@code lastAttemptAt} is older than {@code now − claimLease} is reclaimable (a crashed
   * claimer); a recent (active) claimer is not. Tunable at deploy via {@code
   * app.sqs.sync-claim-lease}; MUST exceed the SQS visibility timeout (infra) so an active-but-slow
   * claimer's redelivery can't reclaim it mid-flight.
   */
  private final Duration claimLease;

  public SyncMessageListener(
      OutlookCalendarSyncRecordRepository syncRecords,
      GraphCalendarPort graphPort,
      Clock clock,
      @Value("${app.sqs.sync-claim-lease:PT5M}") Duration claimLease) {
    this.syncRecords = syncRecords;
    this.graphPort = graphPort;
    this.clock = clock;
    this.claimLease = claimLease;
  }

  @SqsListener("${app.sqs.queue-url}")
  public void onMessage(SyncJobPointer pointer) {
    Instant now = clock.instant();
    // Atomic claim (brief 104) — one conditional UPDATE transitions a claimable row to SYNCING; the
    // DB serializes concurrent at-least-once deliveries, so exactly one caller wins (no TOCTOU, no
    // @Version race, no duplicate Graph events). leaseExpiry = now − claimLease reclaims a stranded
    // (crashed-claimer) SYNCING; an active claimer's recent lastAttemptAt is left alone.
    int claimed = syncRecords.claimForSync(pointer.syncRecordId(), now, now.minus(claimLease));
    if (claimed == 0) {
      // No claimable record: an unknown/vanished id, a terminal/SYNCED row, or an ACTIVE claimer
      // (recent SYNCING within the lease). Returning ACKs the redundant delivery — a skipped state
      // is not a failure, so it never redrives. Log ids-only (rule #7).
      log.info(
          "sync claim skipped for syncRecord={} — no claimable record (no-op)",
          pointer.syncRecordId());
      return;
    }

    // Sole claimer. Reload the freshly-claimed SYNCING instance (the @Modifying bulk update
    // bypassed the persistence context).
    OutlookCalendarSyncRecord record = syncRecords.findById(pointer.syncRecordId()).orElse(null);
    if (record == null) {
      // Raced a delete between claim and reload (vanishingly rare) — nothing to sync.
      log.warn("claimed syncRecord={} vanished before reload — skipping", pointer.syncRecordId());
      return;
    }
    if (record.getGraphEventId() != null) {
      // Already created in Graph (idempotent at-least-once): reconcile this claim to SYNCED rather
      // than re-creating the event. Necessary — the claim already moved the row to SYNCING, so
      // leaving it would loop SYNCING → lease-reclaim → SYNCING forever.
      record.setStatus(SyncStatus.SYNCED);
      record.setProcessedAt(clock.instant());
      syncRecords.save(record);
      return;
    }

    try {
      String graphEventId = graphPort.createEvent(record);
      // Early-persist (brief 104b): save graphEventId in its OWN save BEFORE the SYNCED flip, so a
      // crash after the create can't lose it (reprocess → reconcile-to-SYNCED, no re-create). Pairs
      // with the gateway transactionId, which dedupes the create even if the id is never persisted.
      // REASSIGN to the flushed instance: save() merges, so the prior `record` ref is left STALE
      // (old @Version) — reusing it for the SYNCED save re-introduces the brief-104 two-save bug.
      // saveAndFlush returns the bumped-version instance, so the SYNCED save matches the DB.
      record.setGraphEventId(graphEventId);
      record = syncRecords.saveAndFlush(record);
      record.setStatus(SyncStatus.SYNCED);
      record.setProcessedAt(clock.instant());
      syncRecords.save(record);
    } catch (RuntimeException e) {
      // rule #7 — fixed non-PII code/message; the raw Graph error is NEVER copied or logged. The
      // sole claimer owns this SYNCING row (brief 104), so the FAILED save no longer races another
      // worker → the record reliably lands FAILED (retryable).
      record.setStatus(SyncStatus.FAILED);
      record.setFailureCode(FAILURE_CODE);
      record.setSafeMessage(SAFE_MESSAGE);
      record.setRetryCount(record.getRetryCount() + 1);
      syncRecords.save(record);
      log.warn(
          "sync failed for syncRecord={} — recorded FAILED, will redrive", pointer.syncRecordId());
      // Sanitized rethrow (ids-only, no cause-chain) → SQS redrive → DLQ without leaking the raw
      // Graph error into the framework's redrive logging.
      throw new SyncProcessingException(pointer.syncRecordId());
    }
  }
}
