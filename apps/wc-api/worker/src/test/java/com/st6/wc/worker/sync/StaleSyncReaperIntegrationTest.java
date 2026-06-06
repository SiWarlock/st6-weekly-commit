package com.st6.wc.worker.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.st6.wc.enums.EventKind;
import com.st6.wc.enums.SyncRelatedType;
import com.st6.wc.enums.SyncStatus;
import com.st6.wc.sns.payload.SyncJobPointer;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import com.st6.wc.worker.support.WorkerPostgresSupport;
import io.awspring.cloud.sqs.operations.SqsOperations;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Real-PG integration proof for the 104b reaper (brief 104b, §49). Pins (1) the bounded
 * stale-{@code SYNCING} finder, and (2) the lead's <strong>crashed-claimer → reaper re-enqueue →
 * listener reclaim → SYNCED</strong> end-to-end — chained directly (reaper captures the pointer it
 * would send → the REAL {@link SyncMessageListener} consumes it over Testcontainers PG), so it's
 * deterministic (no real SQS / scheduler / thread race — same §102/104 discipline). Reuses the §44
 * {@link WorkerPostgresSupport} harness + the JdbcTemplate employee-FK seed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class StaleSyncReaperIntegrationTest extends WorkerPostgresSupport {

  private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
  private static final Duration LEASE = Duration.ofMinutes(5);
  private static final Instant CUTOFF = NOW.minus(LEASE);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Autowired private OutlookCalendarSyncRecordRepository syncRecords;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void clean() {
    jdbc.update("DELETE FROM outlook_calendar_sync_record");
    jdbc.update("DELETE FROM employee");
  }

  private UUID insertEmployee() {
    UUID empId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO employee (id, email, display_name, role, active) VALUES (?, ?, ?, 'IC', true)",
        empId,
        empId + "@example.test",
        "Reaper Test Owner");
    return empId;
  }

  private UUID seed(SyncStatus status, Instant lastAttemptAt) {
    OutlookCalendarSyncRecord r = new OutlookCalendarSyncRecord();
    r.setId(UUID.randomUUID());
    r.setOwnerEmployeeId(insertEmployee());
    r.setRelatedType(SyncRelatedType.WEEKLY_PLAN);
    r.setRelatedId(UUID.randomUUID());
    r.setEventKind(EventKind.IC_PLANNING);
    r.setStatus(status);
    r.setRetryCount(0);
    r.setLastAttemptAt(lastAttemptAt);
    r.setTraceId("trace-1");
    return syncRecords.saveAndFlush(r).getId();
  }

  // --- the finder returns ONLY stale-SYNCING (not recent-SYNCING / QUEUED / SYNCED) --------------
  @Test
  void finder_returnsOnlyStaleSyncing() {
    UUID stale = seed(SyncStatus.SYNCING, NOW.minus(Duration.ofMinutes(10)));
    seed(SyncStatus.SYNCING, NOW.minus(Duration.ofSeconds(30))); // recent active claimer — excluded
    seed(SyncStatus.QUEUED, null); // wrong status — excluded
    seed(SyncStatus.SYNCED, NOW.minus(Duration.ofMinutes(10))); // terminal — excluded

    List<OutlookCalendarSyncRecord> found =
        syncRecords.findByStatusAndLastAttemptAtBefore(
            SyncStatus.SYNCING, CUTOFF, PageRequest.of(0, 100));

    assertThat(found).extracting(OutlookCalendarSyncRecord::getId).containsExactly(stale);
  }

  // --- the finder is page-bounded (a backlog can't be republished unboundedly in one tick) -------
  @Test
  void finder_isPageBounded() {
    seed(SyncStatus.SYNCING, NOW.minus(Duration.ofMinutes(10)));
    seed(SyncStatus.SYNCING, NOW.minus(Duration.ofMinutes(11)));
    seed(SyncStatus.SYNCING, NOW.minus(Duration.ofMinutes(12)));

    List<OutlookCalendarSyncRecord> found =
        syncRecords.findByStatusAndLastAttemptAtBefore(
            SyncStatus.SYNCING, CUTOFF, PageRequest.of(0, 2));

    assertThat(found).hasSize(2); // bounded to the page size, not all 3
  }

  // --- THE LEAD'S ASK: crashed-claimer → reaper re-enqueue → listener reclaims → SYNCED ----------
  @Test
  void crashedClaimer_reaperReEnqueues_listenerReclaims_toSynced() {
    // a claimer crashed mid-Graph: the row is stuck SYNCING with lastAttemptAt 10min old (older
    // than the 5min lease) and its SQS message was already no-op-ACK-deleted, so only the reaper
    // can re-fire it.
    UUID id = seed(SyncStatus.SYNCING, NOW.minus(Duration.ofMinutes(10)));

    GraphCalendarPort graphPort = mock(GraphCalendarPort.class);
    when(graphPort.createEvent(any())).thenReturn("evt-reclaimed");
    SqsOperations sqs = mock(SqsOperations.class);
    StaleSyncReaper reaper =
        new StaleSyncReaper(syncRecords, sqs, clock, LEASE, "https://sqs/wc-sync", "aws", 100);
    SyncMessageListener listener = new SyncMessageListener(syncRecords, graphPort, clock, LEASE);

    // 1) the reaper finds the stale row + re-enqueues its pointer (capture what it sends to SQS).
    reaper.reap();
    ArgumentCaptor<SyncJobPointer> sent = ArgumentCaptor.forClass(SyncJobPointer.class);
    verify(sqs).send(eq("https://sqs/wc-sync"), sent.capture());
    assertThat(sent.getValue().syncRecordId()).isEqualTo(id);

    // 2) feed the re-enqueued pointer to the REAL listener → claimForSync reclaims via the lease
    //    clause → Graph (mock) → SYNCED. (Chained directly — no real SQS/scheduler.)
    listener.onMessage(sent.getValue());

    OutlookCalendarSyncRecord r = syncRecords.findById(id).orElseThrow();
    assertThat(r.getStatus()).isEqualTo(SyncStatus.SYNCED);
    assertThat(r.getGraphEventId()).isEqualTo("evt-reclaimed");
  }
}
