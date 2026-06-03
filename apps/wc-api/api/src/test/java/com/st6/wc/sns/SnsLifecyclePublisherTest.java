package com.st6.wc.sns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.st6.wc.enums.EventKind;
import com.st6.wc.enums.SyncRelatedType;
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
 * {@code SnsLifecyclePublisher} unit proof (task 3.5, §10 / rule #4) — the post-commit pointer
 * publish. On success it transitions the sync record {@code PENDING_PUBLISH → QUEUED}; on a gateway
 * failure it <strong>catches + logs + leaves the record retained</strong> ({@code PENDING_PUBLISH},
 * retryable) and NEVER rethrows — the load-bearing non-blocking guarantee (a publish failure can
 * never fail/roll back the already-committed lock). The payload is pointer-only (rule #7).
 */
class SnsLifecyclePublisherTest {

  private final LifecycleSnsGateway gateway = mock(LifecycleSnsGateway.class);
  private final OutlookCalendarSyncRecordRepository syncRecords =
      mock(OutlookCalendarSyncRecordRepository.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);
  private final SnsLifecyclePublisher publisher =
      new SnsLifecyclePublisher(gateway, syncRecords, clock, "test");

  private static OutlookCalendarSyncRecord record() {
    OutlookCalendarSyncRecord r = new OutlookCalendarSyncRecord();
    r.setId(UUID.randomUUID());
    r.setOwnerEmployeeId(UUID.randomUUID());
    r.setRelatedType(SyncRelatedType.WEEKLY_PLAN);
    r.setRelatedId(UUID.randomUUID());
    r.setEventKind(EventKind.IC_PLANNING);
    r.setStatus(SyncStatus.PENDING_PUBLISH);
    r.setRetryCount(0);
    r.setTraceId("trace-1");
    return r;
  }

  // --- success: publish the pointer → record QUEUED + queuedAt set ----
  @Test
  void publish_success_transitionsToQueued() {
    OutlookCalendarSyncRecord r = record();
    when(syncRecords.findById(r.getId())).thenReturn(Optional.of(r));

    publisher.publish(r.getId());

    verify(gateway).publish(any(SyncJobPointer.class));
    assertThat(r.getStatus()).isEqualTo(SyncStatus.QUEUED);
    assertThat(r.getQueuedAt()).isEqualTo(clock.instant());
    verify(syncRecords).save(r);
  }

  // --- failure: gateway throws → record stays PENDING_PUBLISH, NO exception, NOT queued (rule #4)
  // -
  @Test
  void publish_gatewayThrows_recordRetained_noThrow() {
    OutlookCalendarSyncRecord r = record();
    when(syncRecords.findById(r.getId())).thenReturn(Optional.of(r));
    doThrow(new RuntimeException("SNS unavailable")).when(gateway).publish(any());

    assertThatCode(() -> publisher.publish(r.getId())).doesNotThrowAnyException();

    assertThat(r.getStatus()).isEqualTo(SyncStatus.PENDING_PUBLISH); // retained, retryable
    verify(syncRecords, never()).save(any());
  }
}
