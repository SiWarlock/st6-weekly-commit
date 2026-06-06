package com.st6.wc.worker.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.st6.wc.enums.EventKind;
import com.st6.wc.enums.SyncRelatedType;
import com.st6.wc.enums.SyncStatus;
import com.st6.wc.sns.payload.SyncJobPointer;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import com.st6.wc.worker.support.WorkerPostgresSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Single-thread stale-entity regression (Deploy-1 gate) — proves brief 104 fixes the live {@code
 * 340d7e3b} {@code StaleObjectStateException}, which was <strong>single-thread stale-entity-reuse,
 * NOT the concurrency race</strong>. The old flow loaded a record once, {@code save()}d it SYNCING
 * (a merge → the DB version goes 0→1 but the detached reference stays version 0), then mutated +
 * {@code save()}d the SAME stale reference again on the terminal transition (the old line-98 FAILED
 * save) → optimistic-lock conflict. Real PG16 (the §44 {@link WorkerPostgresSupport} harness) so
 * the Hibernate {@code @Version} semantics the mocked {@code SyncMessageListenerTest} can't
 * exercise are exercised here. NO production change — 104 ({@code c45a7a6}) already fixes it
 * structurally; this pins the proof.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SyncMessageListenerStaleEntityRegressionTest extends WorkerPostgresSupport {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);
  private static final Duration LEASE = Duration.ofMinutes(5);

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
        "Stale Regression Owner");
    return empId;
  }

  private UUID seedQueued() {
    OutlookCalendarSyncRecord r = new OutlookCalendarSyncRecord();
    r.setId(UUID.randomUUID());
    r.setOwnerEmployeeId(insertEmployee());
    r.setRelatedType(SyncRelatedType.WEEKLY_PLAN);
    r.setRelatedId(UUID.randomUUID());
    r.setEventKind(EventKind.IC_PLANNING);
    r.setStatus(SyncStatus.QUEUED);
    r.setRetryCount(0);
    r.setTraceId("trace-1");
    return syncRecords.saveAndFlush(r).getId();
  }

  private static SyncJobPointer pointerFor(UUID id) {
    return new SyncJobPointer(id, EventKind.IC_PLANNING, "aws", "trace-1");
  }

  // --- (a) reproduce the OLD bug: two saves on one stale detached reference → optimistic-lock ----
  @Test
  void oldPattern_secondSaveOnStaleReference_throwsOptimisticLock() {
    UUID id = seedQueued();
    OutlookCalendarSyncRecord stale = syncRecords.findById(id).orElseThrow(); // version 0, detached

    stale.setStatus(SyncStatus.SYNCING);
    syncRecords.saveAndFlush(stale); // save 1 (merge): DB version 0→1; `stale` ref STAYS version 0

    // the old listener's second save (the line-98 FAILED save) reused the SAME now-stale reference:
    stale.setStatus(SyncStatus.FAILED);
    assertThatThrownBy(() -> syncRecords.saveAndFlush(stale))
        .as("two saves on a reused detached reference collide on @Version — the 340d7e3b bug")
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }

  // --- (b) prove 104: a single real delivery whose Graph call FAILS lands FAILED (no stale) ---
  @Test
  void brief104_singleDelivery_graphFails_landsFailed_noStaleException() {
    UUID id = seedQueued();
    GraphCalendarPort graphPort = mock(GraphCalendarPort.class);
    when(graphPort.createEvent(any()))
        .thenThrow(new RuntimeException("Graph 403: john.doe@acme.com 'Q3 OKRs'"));
    SyncMessageListener listener = new SyncMessageListener(syncRecords, graphPort, CLOCK, LEASE);

    // the failure path rethrows the SANITIZED exception (→ DLQ) — NOT an optimistic-lock exception:
    // the FAILED save lands on the fresh post-claim reload (claim bulk-UPDATE → findById → one
    // save),
    // so it matches the DB version and never throws stale.
    assertThatThrownBy(() -> listener.onMessage(pointerFor(id)))
        .isInstanceOf(SyncProcessingException.class)
        .isNotInstanceOf(ObjectOptimisticLockingFailureException.class)
        .hasNoCause();

    OutlookCalendarSyncRecord r = syncRecords.findById(id).orElseThrow();
    assertThat(r.getStatus()).isEqualTo(SyncStatus.FAILED);
    assertThat(r.getRetryCount()).isEqualTo(1);
    assertThat(r.getFailureCode()).isEqualTo("GRAPH_SYNC_FAILED");
  }

  // --- (b) success: a single real delivery whose Graph call SUCCEEDS lands SYNCED ---
  @Test
  void brief104_singleDelivery_graphSucceeds_landsSynced_noStaleException() {
    UUID id = seedQueued();
    GraphCalendarPort graphPort = mock(GraphCalendarPort.class);
    when(graphPort.createEvent(any())).thenReturn("graph-evt-ok");
    SyncMessageListener listener = new SyncMessageListener(syncRecords, graphPort, CLOCK, LEASE);

    // claim (bulk UPDATE) → fresh reload → single terminal save → no stale exception escapes.
    listener.onMessage(pointerFor(id));

    OutlookCalendarSyncRecord r = syncRecords.findById(id).orElseThrow();
    assertThat(r.getStatus()).isEqualTo(SyncStatus.SYNCED);
    assertThat(r.getGraphEventId()).isEqualTo("graph-evt-ok");
    assertThat(r.getProcessedAt()).isEqualTo(CLOCK.instant());
  }
}
