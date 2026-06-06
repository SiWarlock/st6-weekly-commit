package com.st6.wc.worker.sync;

import com.st6.wc.enums.SyncStatus;
import com.st6.wc.sns.payload.SyncJobPointer;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import io.awspring.cloud.sqs.operations.SqsOperations;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Re-triggers the stale-{@code SYNCING} reclaim (brief 104b, §49 — the crashed-claimer recovery).
 * 104's {@link OutlookCalendarSyncRecordRepository#claimForSync} lease clause makes a stranded
 * in-flight row <em>reclaimable by a redelivery</em>, but the listener's no-op path ACK-deletes the
 * SQS message — so a claimer that crashes mid-Graph (its sole message already deleted by a racing
 * redelivery) has nothing left to re-fire the reclaim. This {@code @Scheduled} reaper IS that
 * re-trigger: it finds stale-{@code SYNCING} rows ({@code lastAttemptAt < now − lease}) and
 * <strong>re-enqueues the bare {@link SyncJobPointer}</strong> onto the {@code wc-sync} queue via
 * {@link SqsOperations} → the listener's lease clause reclaims the row (it stays {@code SYNCING},
 * reclaimed via the stale-{@code SYNCING} branch) → reprocesses to {@code SYNCED}/{@code FAILED}.
 * Single processing path (the listener); the reaper never reimplements Graph processing.
 *
 * <p><strong>Not the §28 {@code SnsLifecyclePublisher}</strong> — that is an {@code api}-module
 * bean (the worker is {@code :shared}-only, REQ-O-016 / {@code checkModuleBoundaries}), and it
 * flips {@code PENDING_PUBLISH→QUEUED} (wrong for a SYNCING-reclaim). The worker re-enqueues
 * directly onto its own SQS queue instead (it already has the SQS client); the SNS→SQS subscription
 * uses raw message delivery, so a direct pointer send matches the listener's wire format.
 *
 * <p><strong>Rule #7:</strong> only the bare pointer is sent (no calendar/PII). <strong>Rule
 * #4:</strong> the whole {@code reap()} is swallow-guarded — a per-row re-enqueue failure skips
 * that row and continues, and a tick-level failure (e.g. a transient finder query error) skips the
 * tick; the {@code @Scheduled} poller never blocks (the next tick retries). <strong>Infra
 * dependency:</strong> the worker IRSA is consume-only until infra grants {@code sqs:SendMessage}
 * on {@code wc-sync}; until then every send is AccessDenied and this reaper is an inert, benign
 * no-op (the swallow logs it). Active only in {@code aws} ({@code app.sqs.queue-url} gate,
 * mirroring the listener — no scheduler/queue locally).
 */
@Component
@ConditionalOnProperty("app.sqs.queue-url")
public class StaleSyncReaper {

  private static final Logger log = LoggerFactory.getLogger(StaleSyncReaper.class);

  private final OutlookCalendarSyncRecordRepository syncRecords;
  private final SqsOperations sqs;
  private final Clock clock;

  /** Same value as the listener's claim lease (single source — the cutoff == the claim clause). */
  private final Duration lease;

  private final String queueUrl;
  private final String env;
  private final int batchSize;

  public StaleSyncReaper(
      OutlookCalendarSyncRecordRepository syncRecords,
      SqsOperations sqs,
      Clock clock,
      @Value("${app.sqs.sync-claim-lease:PT5M}") Duration lease,
      @Value("${app.sqs.queue-url}") String queueUrl,
      @Value("${app.env:local}") String env,
      @Value("${app.sqs.reaper-batch-size:100}") int batchSize) {
    this.syncRecords = syncRecords;
    this.sqs = sqs;
    this.clock = clock;
    this.lease = lease;
    this.queueUrl = queueUrl;
    this.env = env;
    this.batchSize = batchSize;
  }

  @Scheduled(fixedDelayString = "${app.sqs.reaper-interval:PT1M}")
  public void reap() {
    try {
      Instant cutoff = clock.instant().minus(lease);
      List<OutlookCalendarSyncRecord> stale =
          syncRecords.findByStatusAndLastAttemptAtBefore(
              SyncStatus.SYNCING, cutoff, PageRequest.of(0, batchSize));
      if (stale.isEmpty()) {
        return;
      }
      int republished = 0;
      for (OutlookCalendarSyncRecord row : stale) {
        try {
          sqs.send(
              queueUrl, new SyncJobPointer(row.getId(), row.getEventKind(), env, row.getTraceId()));
          republished++;
        } catch (RuntimeException e) {
          // a single re-enqueue failure skips that row + continues (inert no-op until infra grants
          // the worker sqs:SendMessage — see the class doc).
          log.warn(
              "stale-sync reaper failed to re-enqueue syncRecord={} — skipping", row.getId(), e);
        }
      }
      log.info("stale-sync reaper re-enqueued {}/{} stale SYNCING rows", republished, stale.size());
    } catch (RuntimeException e) {
      // rule #4 — the @Scheduled poller never blocks: a tick-level failure (e.g. a transient finder
      // query error) logs + skips this tick; the next tick retries. (No core txn is ever held.)
      log.warn("stale-sync reaper tick failed — skipping this tick, the next tick will retry", e);
    }
  }
}
