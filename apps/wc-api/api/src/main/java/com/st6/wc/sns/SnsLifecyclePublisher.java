package com.st6.wc.sns;

import com.st6.wc.enums.SyncStatus;
import com.st6.wc.sns.payload.SyncJobPointer;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes the lifecycle sync-record pointer to SNS <strong>after the core lock transaction
 * commits</strong> (task 3.5, §10 / safety rule #4). Invoked from a {@code
 * TransactionSynchronization.afterCommit} hook, so it runs in a NEW transaction ({@link
 * Propagation#REQUIRES_NEW}) on the already-committed lock. On success it transitions the record
 * {@code PENDING_PUBLISH → QUEUED}; on ANY gateway failure it <strong>catches + logs + leaves the
 * record retained</strong> ({@code PENDING_PUBLISH}, retryable) and NEVER rethrows — a publish
 * failure can never fail or roll back the committed lock (NEVER-TRIM). The payload is pointer-only
 * (rule #7).
 */
@Service
public class SnsLifecyclePublisher {

  private static final Logger log = LoggerFactory.getLogger(SnsLifecyclePublisher.class);

  private final LifecycleSnsGateway gateway;
  private final OutlookCalendarSyncRecordRepository syncRecords;
  private final Clock clock;
  private final String env;

  public SnsLifecyclePublisher(
      LifecycleSnsGateway gateway,
      OutlookCalendarSyncRecordRepository syncRecords,
      Clock clock,
      @Value("${app.env:local}") String env) {
    this.gateway = gateway;
    this.syncRecords = syncRecords;
    this.clock = clock;
    this.env = env;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void publish(UUID syncRecordId) {
    OutlookCalendarSyncRecord record = syncRecords.findById(syncRecordId).orElse(null);
    if (record == null) {
      return;
    }
    try {
      gateway.publish(
          new SyncJobPointer(record.getId(), record.getEventKind(), env, record.getTraceId()));
      record.setStatus(SyncStatus.QUEUED);
      record.setQueuedAt(clock.instant());
      syncRecords.save(record);
    } catch (RuntimeException e) {
      // rule #4 — never propagate: the record stays PENDING_PUBLISH (retryable). Log ids only.
      log.warn(
          "Lifecycle SNS publish failed for syncRecord={} — retained PENDING_PUBLISH (retryable)",
          syncRecordId,
          e);
    }
  }
}
