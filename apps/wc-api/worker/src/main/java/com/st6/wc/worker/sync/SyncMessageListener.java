package com.st6.wc.worker.sync;

import com.st6.wc.enums.SyncStatus;
import com.st6.wc.sns.payload.SyncJobPointer;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import io.awspring.cloud.sqs.annotation.SqsListener;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumes the {@code wc-sync} queue (Wave-2 s8, §10): deserializes the pointer-only {@link
 * SyncJobPointer}, reloads the {@link OutlookCalendarSyncRecord} by {@code syncRecordId}, and
 * drives the calendar op through the {@link GraphCalendarPort}.
 *
 * <p>Pipeline: idempotent skip when already created in Graph ({@code graphEventId != null} — at-
 * least-once safe) → {@code QUEUED → SYNCING} (+ {@code lastAttemptAt}) → port → {@code SYNCED} (+
 * {@code graphEventId} + {@code processedAt}); on failure {@code → FAILED} (+ a fixed non-PII
 * {@code failureCode}/{@code safeMessage} + {@code retryCount++}) then rethrow a sanitized {@link
 * SyncProcessingException} so SQS redrives to the DLQ after {@code maxReceiveCount}.
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

  public SyncMessageListener(
      OutlookCalendarSyncRecordRepository syncRecords, GraphCalendarPort graphPort, Clock clock) {
    this.syncRecords = syncRecords;
    this.graphPort = graphPort;
    this.clock = clock;
  }

  @SqsListener("${app.sqs.queue-url}")
  public void onMessage(SyncJobPointer pointer) {
    OutlookCalendarSyncRecord record = syncRecords.findById(pointer.syncRecordId()).orElse(null);
    if (record == null) {
      // The record vanished (or an unknown id) — nothing to sync; don't redrive a ghost.
      log.warn("sync pointer for unknown syncRecord={} — skipping", pointer.syncRecordId());
      return;
    }
    if (record.getStatus() != SyncStatus.QUEUED
        && record.getStatus() != SyncStatus.RETRY_REQUESTED) {
      // §10 redelivery guard — the worker (re)attempts Graph ONLY for QUEUED/RETRY_REQUESTED. Any
      // other state (SYNCED / active-SYNCING / FAILED / PENDING_PUBLISH) is a no-op: returning
      // ACKS/deletes the message (a legitimately-skipped state is NOT a failure, so it never
      // redrives). Hardens at-least-once idempotency — closes the in-flight-SYNCING-duplicate gap
      // the graphEventId-only guard left. Log ids only (status name is non-PII, rule #7).
      log.info(
          "sync skip for syncRecord={} status={} — not a worker-trigger state (no-op)",
          pointer.syncRecordId(),
          record.getStatus());
      return;
    }
    if (record.getGraphEventId() != null) {
      // Idempotent (at-least-once): already created in Graph — skip the duplicate op.
      return;
    }

    record.setStatus(SyncStatus.SYNCING);
    record.setLastAttemptAt(clock.instant());
    syncRecords.save(record);

    try {
      String graphEventId = graphPort.createEvent(record);
      record.setStatus(SyncStatus.SYNCED);
      record.setGraphEventId(graphEventId);
      record.setProcessedAt(clock.instant());
      syncRecords.save(record);
    } catch (RuntimeException e) {
      // rule #7 — fixed non-PII code/message; the raw Graph error is NEVER copied or logged.
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
