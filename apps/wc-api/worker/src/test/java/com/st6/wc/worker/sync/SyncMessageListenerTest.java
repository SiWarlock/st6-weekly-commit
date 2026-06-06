package com.st6.wc.worker.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit proof for the worker SQS consumer (Wave-2 s8, §10 / rule #7; brief 104 atomic-claim
 * refactor). The consumer now <strong>claims</strong> the record atomically ({@link
 * OutlookCalendarSyncRecordRepository#claimForSync}) instead of read-then-{@code
 * setStatus(SYNCING)}: {@code claimed == 0} → no-op ACK; {@code claimed == 1} → reload →
 * reconcile-to-{@code SYNCED} (if already in Graph) or port → {@code SYNCED}/{@code FAILED}. These
 * tests pin the listener's branching on the claim result (mocked); the row-state matrix the claim
 * CAS reduces to is pinned deterministically by {@link OutlookCalendarSyncRecordClaimTest} (real
 * PG, no threads). On failure the §44 sanitized cause-less rethrow + fixed non-PII code/message are
 * preserved (the rethrow assertion keeps the brief-102 exact-message form). The
 * {@code @SqsListener} method is exercised directly (the annotation is inert in a unit call).
 */
class SyncMessageListenerTest {

  private static final Duration LEASE = Duration.ofMinutes(5);

  private final OutlookCalendarSyncRecordRepository syncRecords =
      mock(OutlookCalendarSyncRecordRepository.class);
  private final GraphCalendarPort graphPort = mock(GraphCalendarPort.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);
  private final SyncMessageListener listener =
      new SyncMessageListener(syncRecords, graphPort, clock, LEASE);

  /**
   * A record as the post-claim {@code findById} reload returns it (SYNCING, the claim's outcome).
   */
  private static OutlookCalendarSyncRecord syncingRecord() {
    OutlookCalendarSyncRecord r = new OutlookCalendarSyncRecord();
    r.setId(UUID.randomUUID());
    r.setOwnerEmployeeId(UUID.randomUUID());
    r.setEventKind(EventKind.IC_PLANNING);
    r.setStatus(SyncStatus.SYNCING);
    r.setRetryCount(0);
    r.setTraceId("trace-1");
    return r;
  }

  private static SyncJobPointer pointerFor(OutlookCalendarSyncRecord r) {
    return new SyncJobPointer(r.getId(), r.getEventKind(), "aws", r.getTraceId());
  }

  // --- 1. sole claimer: claim → reload → port → SYNCED + graphEventId + processedAt --------------
  @Test
  void claimed_syncsToSynced() {
    OutlookCalendarSyncRecord r = syncingRecord();
    when(syncRecords.claimForSync(eq(r.getId()), any(Instant.class), any(Instant.class)))
        .thenReturn(1);
    when(syncRecords.findById(r.getId())).thenReturn(Optional.of(r));
    when(graphPort.createEvent(r)).thenReturn("demo-graph-evt-1");
    when(syncRecords.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0)); // early-persist

    listener.onMessage(pointerFor(r));

    // the claim is passed (now, leaseExpiry = now − lease) — pins the lease wiring.
    ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Instant> leaseExpiry = ArgumentCaptor.forClass(Instant.class);
    verify(syncRecords).claimForSync(eq(r.getId()), now.capture(), leaseExpiry.capture());
    assertThat(now.getValue()).isEqualTo(clock.instant());
    assertThat(leaseExpiry.getValue()).isEqualTo(clock.instant().minus(LEASE));

    verify(graphPort).createEvent(r);
    assertThat(r.getStatus()).isEqualTo(SyncStatus.SYNCED);
    assertThat(r.getGraphEventId()).isEqualTo("demo-graph-evt-1");
    assertThat(r.getProcessedAt()).isEqualTo(clock.instant());
  }

  // --- 1b. early-persist (brief 104b idempotency): graphEventId saved in its OWN save BEFORE the
  // SYNCED flip — a crash after the create can't lose it (reprocess → reconcile-to-SYNCED).
  @Test
  void claimed_earlyPersistsGraphEventId_beforeSyncedFlip() {
    OutlookCalendarSyncRecord r = syncingRecord();
    when(syncRecords.claimForSync(eq(r.getId()), any(Instant.class), any(Instant.class)))
        .thenReturn(1);
    when(syncRecords.findById(r.getId())).thenReturn(Optional.of(r));
    when(graphPort.createEvent(r)).thenReturn("evt-1");

    // snapshot the record's (status, graphEventId) at each persist — in order. The early-persist is
    // a saveAndFlush (its own save); the SYNCED flip is a save. The record is mutated in place.
    List<String> persistStates = new ArrayList<>();
    when(syncRecords.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              OutlookCalendarSyncRecord saved = inv.getArgument(0);
              persistStates.add(saved.getStatus() + ":" + saved.getGraphEventId());
              return saved;
            });
    doAnswer(
            inv -> {
              OutlookCalendarSyncRecord saved = inv.getArgument(0);
              persistStates.add(saved.getStatus() + ":" + saved.getGraphEventId());
              return saved;
            })
        .when(syncRecords)
        .save(any());

    listener.onMessage(pointerFor(r));

    // saveAndFlush = the early-persist (graphEventId durable, still SYNCING); then save = SYNCED.
    // A crash between them leaves graphEventId recoverable (reprocess → reconcile-SYNCED).
    assertThat(persistStates).containsExactly("SYNCING:evt-1", "SYNCED:evt-1");
  }

  // --- 2. not claimable (claim returns 0): no-op ACK — no reload, no port, no save, no throw -----
  // Folds the old per-state no-ops (SYNCED / SYNCING / FAILED / PENDING_PUBLISH / unknown id): the
  // claim's WHERE clause decides claimability (pinned by the repo CAS test), so at the listener
  // level they all collapse to "claimed == 0".
  @Test
  void notClaimable_isNoOpAck() {
    SyncJobPointer pointer =
        new SyncJobPointer(UUID.randomUUID(), EventKind.IC_PLANNING, "aws", "trace-x");
    when(syncRecords.claimForSync(
            eq(pointer.syncRecordId()), any(Instant.class), any(Instant.class)))
        .thenReturn(0);

    listener.onMessage(pointer); // no throw

    verifyNoInteractions(graphPort);
    verify(syncRecords, never()).findById(any());
    verify(syncRecords, never()).save(any());
  }

  // --- 3. claimer's Graph call fails → FAILED + non-PII code/message + retry++ + sanitized rethrow
  @Test
  void claimerGraphFailure_recordsFailedAndThrows() {
    OutlookCalendarSyncRecord r = syncingRecord();
    when(syncRecords.claimForSync(eq(r.getId()), any(Instant.class), any(Instant.class)))
        .thenReturn(1);
    when(syncRecords.findById(r.getId())).thenReturn(Optional.of(r));
    // The Graph error carries PII/secret-ish detail — it must NOT leak into the record or the
    // throw.
    when(graphPort.createEvent(r))
        .thenThrow(new RuntimeException("Graph 403: john.doe@acme.com calendar 'Q3 OKRs' body"));

    // §44 rule-#7 defense: sanitized type, NO cause-chain (so the SQS framework's redrive logging
    // can't surface the raw Graph error), and the EXACT ids-only message (brief-102 — stronger than
    // token-absence checks AND deterministic for any random record id; never regress to
    // notContaining).
    assertThatThrownBy(() -> listener.onMessage(pointerFor(r)))
        .isInstanceOf(SyncProcessingException.class)
        .hasNoCause()
        .hasMessage("Sync processing failed for syncRecord=" + r.getId());

    assertThat(r.getStatus()).isEqualTo(SyncStatus.FAILED);
    assertThat(r.getRetryCount()).isEqualTo(1);
    assertThat(r.getFailureCode()).isEqualTo("GRAPH_SYNC_FAILED");
    assertThat(r.getSafeMessage()).isEqualTo("Calendar sync failed; it will be retried.");
    assertThat(r.getSafeMessage()).doesNotContain("john.doe@acme.com", "Q3 OKRs", "403");
    assertThat(r.getGraphEventId()).isNull();
  }

  // --- 4. claimed but already created in Graph → reconcile to SYNCED (no double-create) ----------
  // The claim moved the row to SYNCING; if a graphEventId is already present (idempotent at-least-
  // once), reconcile to SYNCED rather than re-calling Graph — leaving it SYNCING would loop
  // SYNCING → lease-reclaim → SYNCING forever.
  @Test
  void claimedButAlreadyInGraph_reconcilesToSynced() {
    OutlookCalendarSyncRecord r = syncingRecord();
    r.setGraphEventId("already-created");
    when(syncRecords.claimForSync(eq(r.getId()), any(Instant.class), any(Instant.class)))
        .thenReturn(1);
    when(syncRecords.findById(r.getId())).thenReturn(Optional.of(r));

    listener.onMessage(pointerFor(r));

    verifyNoInteractions(graphPort); // no duplicate create
    assertThat(r.getStatus()).isEqualTo(SyncStatus.SYNCED);
    assertThat(r.getProcessedAt()).isEqualTo(clock.instant());
    assertThat(r.getGraphEventId()).isEqualTo("already-created"); // unchanged
    verify(syncRecords).save(r);
  }

  // --- 5. claimed (1) but the row vanished before the reload (raced a delete) → defensive no-op
  // ---
  @Test
  void claimedThenVanishedBeforeReload_isNoOp() {
    UUID id = UUID.randomUUID();
    when(syncRecords.claimForSync(eq(id), any(Instant.class), any(Instant.class))).thenReturn(1);
    when(syncRecords.findById(id)).thenReturn(Optional.empty());

    listener.onMessage(new SyncJobPointer(id, EventKind.IC_PLANNING, "aws", "trace-z")); // no throw

    verifyNoInteractions(graphPort);
    verify(syncRecords, never()).save(any());
  }
}
