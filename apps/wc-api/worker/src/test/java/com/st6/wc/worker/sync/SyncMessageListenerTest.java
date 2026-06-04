package com.st6.wc.worker.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.st6.wc.enums.EventKind;
import com.st6.wc.enums.SyncStatus;
import com.st6.wc.sns.payload.SyncJobPointer;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit proof for the worker SQS consumer (Wave-2 s8, §10 / rule #7). Reloads the {@code
 * OutlookCalendarSyncRecord} by the pointer's {@code syncRecordId}, transitions {@code QUEUED →
 * SYNCING → SYNCED/FAILED} around the {@link GraphCalendarPort}, is idempotent (skip when already
 * synced), and on failure records a <strong>non-PII</strong> {@code failureCode}/{@code
 * safeMessage} + rethrows (→ SQS redrive → DLQ). The {@code @SqsListener} method is exercised
 * directly (the annotation is inert in a unit call). Mirrors the {@code SnsLifecyclePublisher}
 * unit-test posture.
 */
class SyncMessageListenerTest {

  private final OutlookCalendarSyncRecordRepository syncRecords =
      mock(OutlookCalendarSyncRecordRepository.class);
  private final GraphCalendarPort graphPort = mock(GraphCalendarPort.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);
  private final SyncMessageListener listener =
      new SyncMessageListener(syncRecords, graphPort, clock);

  private static OutlookCalendarSyncRecord queuedRecord() {
    OutlookCalendarSyncRecord r = new OutlookCalendarSyncRecord();
    r.setId(UUID.randomUUID());
    r.setOwnerEmployeeId(UUID.randomUUID());
    r.setEventKind(EventKind.IC_PLANNING);
    r.setStatus(SyncStatus.QUEUED);
    r.setRetryCount(0);
    r.setTraceId("trace-1");
    return r;
  }

  private static SyncJobPointer pointerFor(OutlookCalendarSyncRecord r) {
    return new SyncJobPointer(r.getId(), r.getEventKind(), "aws", r.getTraceId());
  }

  // --- 1. happy path: reload → SYNCING → port → SYNCED + graphEventId + processedAt ----
  @Test
  void consumesPointer_reloadsAndSyncs() {
    OutlookCalendarSyncRecord r = queuedRecord();
    when(syncRecords.findById(r.getId())).thenReturn(Optional.of(r));
    when(graphPort.createEvent(r)).thenReturn("demo-graph-evt-1");

    listener.onMessage(pointerFor(r));

    verify(graphPort).createEvent(r);
    assertThat(r.getStatus()).isEqualTo(SyncStatus.SYNCED);
    assertThat(r.getGraphEventId()).isEqualTo("demo-graph-evt-1");
    assertThat(r.getProcessedAt()).isEqualTo(clock.instant());
    assertThat(r.getLastAttemptAt()).isEqualTo(clock.instant());
  }

  // --- 2. idempotent: a redelivered pointer for an already-synced record is a no-op skip ----
  @Test
  void alreadySynced_isIdempotentNoOp() {
    OutlookCalendarSyncRecord r = queuedRecord();
    r.setStatus(SyncStatus.SYNCED);
    r.setGraphEventId("demo-graph-evt-1"); // the "already created in Graph" signal
    when(syncRecords.findById(r.getId())).thenReturn(Optional.of(r));

    listener.onMessage(pointerFor(r));

    verifyNoInteractions(graphPort); // no second calendar op
    assertThat(r.getStatus()).isEqualTo(SyncStatus.SYNCED);
  }

  // --- 3. failure: port throws → FAILED + non-PII failureCode/safeMessage + retry++ + rethrow ----
  @Test
  void graphFailure_recordsFailedAndThrows() {
    OutlookCalendarSyncRecord r = queuedRecord();
    when(syncRecords.findById(r.getId())).thenReturn(Optional.of(r));
    // The Graph error carries PII/secret-ish detail — it must NOT leak into the record or the
    // throw.
    when(graphPort.createEvent(r))
        .thenThrow(new RuntimeException("Graph 403: john.doe@acme.com calendar 'Q3 OKRs' body"));

    // The rethrow is the headline rule-#7 defense: the sanitized type, NO cause-chain (so the SQS
    // framework's redrive logging can't surface the raw Graph error), and an ids-only message.
    assertThatThrownBy(() -> listener.onMessage(pointerFor(r)))
        .isInstanceOf(SyncProcessingException.class)
        .hasNoCause()
        .hasMessageNotContaining("john.doe@acme.com")
        .hasMessageNotContaining("Q3 OKRs")
        .hasMessageNotContaining("403");

    assertThat(r.getStatus()).isEqualTo(SyncStatus.FAILED);
    assertThat(r.getRetryCount()).isEqualTo(1);
    // rule #7 — fixed code + fixed non-PII message; the raw exception detail never copied through.
    assertThat(r.getFailureCode()).isEqualTo("GRAPH_SYNC_FAILED");
    assertThat(r.getSafeMessage()).isEqualTo("Calendar sync failed; it will be retried.");
    assertThat(r.getSafeMessage()).doesNotContain("john.doe@acme.com", "Q3 OKRs", "403");
    assertThat(r.getGraphEventId()).isNull();
  }

  // --- 4. unknown record (vanished id): no port call, no throw (nothing to redrive) ----
  @Test
  void unknownRecord_isNoOp() {
    SyncJobPointer pointer =
        new SyncJobPointer(UUID.randomUUID(), EventKind.IC_PLANNING, "aws", "trace-x");
    when(syncRecords.findById(pointer.syncRecordId())).thenReturn(Optional.empty());

    listener.onMessage(pointer);

    verifyNoInteractions(graphPort);
    verify(syncRecords, never()).save(any());
  }

  // ===== 098 — the §10 worker-redelivery status guard =====

  // --- 098 #1: a RETRY_REQUESTED record is re-attempted → SYNCING → port → SYNCED (097 e2e) ----
  @Test
  void retryRequested_reattempts_toSynced() {
    OutlookCalendarSyncRecord r = queuedRecord();
    r.setStatus(SyncStatus.RETRY_REQUESTED); // graphEventId null (retry only fires on FAILED)
    when(syncRecords.findById(r.getId())).thenReturn(Optional.of(r));
    when(graphPort.createEvent(r)).thenReturn("graph-evt-retry");

    listener.onMessage(pointerFor(r));

    verify(graphPort).createEvent(r);
    assertThat(r.getStatus()).isEqualTo(SyncStatus.SYNCED);
    assertThat(r.getGraphEventId()).isEqualTo("graph-evt-retry");
    assertThat(r.getProcessedAt()).isEqualTo(clock.instant());
  }

  // --- 098 #4: a SYNCING record (in-flight duplicate redelivery) → no-op, no double-attempt ----
  // graphEventId is null here, so ONLY the new status guard catches it (the gap the s8
  // graphEventId-only guard left).
  @Test
  void syncing_noOp() {
    OutlookCalendarSyncRecord r = queuedRecord();
    r.setStatus(SyncStatus.SYNCING);
    when(syncRecords.findById(r.getId())).thenReturn(Optional.of(r));

    listener.onMessage(pointerFor(r));

    verifyNoInteractions(graphPort);
    verify(syncRecords, never()).save(any());
    assertThat(r.getStatus()).isEqualTo(SyncStatus.SYNCING); // unchanged
  }

  // --- 098 #5: a FAILED record (not yet retried) → no-op (FAILED is not a worker-trigger state) --
  @Test
  void failed_noOp() {
    OutlookCalendarSyncRecord r = queuedRecord();
    r.setStatus(SyncStatus.FAILED);
    when(syncRecords.findById(r.getId())).thenReturn(Optional.of(r));

    listener.onMessage(pointerFor(r));

    verifyNoInteractions(graphPort);
    verify(syncRecords, never()).save(any());
    assertThat(r.getStatus()).isEqualTo(SyncStatus.FAILED);
  }

  // --- 098 #6: a PENDING_PUBLISH record (shouldn't be queued) → defensive no-op ----
  @Test
  void pendingPublish_noOp() {
    OutlookCalendarSyncRecord r = queuedRecord();
    r.setStatus(SyncStatus.PENDING_PUBLISH);
    when(syncRecords.findById(r.getId())).thenReturn(Optional.of(r));

    listener.onMessage(pointerFor(r));

    verifyNoInteractions(graphPort);
    verify(syncRecords, never()).save(any());
  }

  // --- 098: defense-in-depth — a (shouldn't-happen) QUEUED record that ALREADY has a graphEventId
  // passes the status guard but is skipped by the secondary graphEventId guard (no double-create).
  // --
  @Test
  void queuedWithGraphEventId_secondaryGuardSkips() {
    OutlookCalendarSyncRecord r = queuedRecord(); // status QUEUED → passes the §10 status guard
    r.setGraphEventId("already-created"); // …but already created → the secondary guard skips
    when(syncRecords.findById(r.getId())).thenReturn(Optional.of(r));

    listener.onMessage(pointerFor(r));

    verifyNoInteractions(graphPort); // no duplicate create
    verify(syncRecords, never()).save(any());
  }

  // --- 098 #7: §44 preserved on the RETRY path — graph failure → FAILED + cause-less rethrow ----
  @Test
  void retryRequested_graphFailure_recordsFailedAndThrows() {
    OutlookCalendarSyncRecord r = queuedRecord();
    r.setStatus(SyncStatus.RETRY_REQUESTED);
    when(syncRecords.findById(r.getId())).thenReturn(Optional.of(r));
    when(graphPort.createEvent(r))
        .thenThrow(new RuntimeException("Graph 403: john.doe@acme.com 'Q3 OKRs' rejected"));

    assertThatThrownBy(() -> listener.onMessage(pointerFor(r)))
        .isInstanceOf(SyncProcessingException.class)
        .hasNoCause()
        .hasMessageNotContaining("john.doe@acme.com")
        .hasMessageNotContaining("Q3 OKRs");

    assertThat(r.getStatus()).isEqualTo(SyncStatus.FAILED);
    assertThat(r.getRetryCount()).isEqualTo(1);
    assertThat(r.getFailureCode()).isEqualTo("GRAPH_SYNC_FAILED");
  }
}
