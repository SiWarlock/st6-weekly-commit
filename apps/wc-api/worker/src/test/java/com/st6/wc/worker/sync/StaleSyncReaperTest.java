package com.st6.wc.worker.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.st6.wc.enums.EventKind;
import com.st6.wc.enums.SyncStatus;
import com.st6.wc.sns.payload.SyncJobPointer;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import io.awspring.cloud.sqs.operations.SqsOperations;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

/**
 * Unit proof for the 104b {@link StaleSyncReaper}. Mocks the finder + {@link SqsOperations} + a
 * fixed {@code Clock}; pins the reaper's branching deterministically (no real scheduler/queue). The
 * row-state matrix of which rows ARE stale is pinned by {@link StaleSyncReaperIntegrationTest}
 * (real PG); the crashed-claimer→reclaim→SYNCED e2e lives there too.
 */
class StaleSyncReaperTest {

  private static final String QUEUE = "https://sqs/wc-sync";
  private static final String ENV = "aws";
  private static final Duration LEASE = Duration.ofMinutes(5);
  private static final int BATCH = 100;

  private final OutlookCalendarSyncRecordRepository syncRecords =
      mock(OutlookCalendarSyncRecordRepository.class);
  private final SqsOperations sqs = mock(SqsOperations.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);
  private final StaleSyncReaper reaper =
      new StaleSyncReaper(syncRecords, sqs, clock, LEASE, QUEUE, ENV, BATCH);

  private static OutlookCalendarSyncRecord staleRow() {
    OutlookCalendarSyncRecord r = new OutlookCalendarSyncRecord();
    r.setId(UUID.randomUUID());
    r.setEventKind(EventKind.IC_PLANNING);
    r.setStatus(SyncStatus.SYNCING);
    r.setTraceId("trace-stale");
    return r;
  }

  @Test
  void reap_reEnqueuesEachStaleRow_withCutoffNowMinusLease() {
    OutlookCalendarSyncRecord a = staleRow();
    OutlookCalendarSyncRecord b = staleRow();
    when(syncRecords.findByStatusAndLastAttemptAtBefore(
            eq(SyncStatus.SYNCING), any(Instant.class), any(Pageable.class)))
        .thenReturn(List.of(a, b));

    reaper.reap();

    // cutoff = now − lease (pins the single-source lease wiring) + the bounded page size.
    ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
    verify(syncRecords)
        .findByStatusAndLastAttemptAtBefore(
            eq(SyncStatus.SYNCING), cutoff.capture(), page.capture());
    assertThat(cutoff.getValue()).isEqualTo(clock.instant().minus(LEASE));
    assertThat(page.getValue().getPageSize()).isEqualTo(BATCH);

    // each stale row → a bare pointer re-enqueued onto the wc-sync queue (rule #7).
    ArgumentCaptor<SyncJobPointer> sent = ArgumentCaptor.forClass(SyncJobPointer.class);
    verify(sqs, times(2)).send(eq(QUEUE), sent.capture());
    assertThat(sent.getAllValues())
        .extracting(SyncJobPointer::syncRecordId)
        .containsExactlyInAnyOrder(a.getId(), b.getId());
    assertThat(sent.getAllValues()).allSatisfy(p -> assertThat(p.env()).isEqualTo(ENV));
  }

  @Test
  void reap_noStaleRows_sendsNothing() {
    when(syncRecords.findByStatusAndLastAttemptAtBefore(
            eq(SyncStatus.SYNCING), any(Instant.class), any(Pageable.class)))
        .thenReturn(List.of());

    reaper.reap();

    verifyNoInteractions(sqs);
  }

  @Test
  void reap_sendFailureIsSwallowed_andNextRowStillReEnqueued() {
    OutlookCalendarSyncRecord a = staleRow();
    OutlookCalendarSyncRecord b = staleRow();
    when(syncRecords.findByStatusAndLastAttemptAtBefore(
            eq(SyncStatus.SYNCING), any(Instant.class), any(Pageable.class)))
        .thenReturn(List.of(a, b));
    // the first send throws (e.g. AccessDenied until infra grants sqs:SendMessage) — rule #4: it
    // must NOT propagate and must NOT stop the next row's re-enqueue.
    when(sqs.send(eq(QUEUE), any(SyncJobPointer.class)))
        .thenThrow(new RuntimeException("AccessDenied: sqs:SendMessage"))
        .thenReturn(null);

    assertThatCode(reaper::reap).doesNotThrowAnyException();

    verify(sqs, times(2)).send(eq(QUEUE), any(SyncJobPointer.class)); // both attempted
  }

  // a tick-level failure (a transient finder query error — connection drop / pool exhaustion) is
  // OUTSIDE the per-row guard, so it needs the method-level swallow (security-reviewer medium): the
  // @Scheduled poller must never block; the next tick retries.
  @Test
  void reap_finderThrows_isSwallowed_doesNotEscapeTheTick() {
    when(syncRecords.findByStatusAndLastAttemptAtBefore(
            eq(SyncStatus.SYNCING), any(Instant.class), any(Pageable.class)))
        .thenThrow(new RuntimeException("transient DB error — pool exhausted"));

    assertThatCode(reaper::reap).doesNotThrowAnyException();

    verifyNoInteractions(sqs); // the tick is skipped cleanly — no send attempted
  }
}
