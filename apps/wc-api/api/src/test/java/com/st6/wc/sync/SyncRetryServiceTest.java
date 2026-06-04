package com.st6.wc.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.st6.wc.auth.DomainAuthorizationService;
import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.enums.EventKind;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.SyncRelatedType;
import com.st6.wc.enums.SyncStatus;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.AllowedActionResolver;
import com.st6.wc.sns.LifecycleSnsGateway;
import com.st6.wc.sns.SnsLifecyclePublisher;
import com.st6.wc.sns.payload.SyncJobPointer;
import com.st6.wc.sync.dto.OutlookSyncRecordDto;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import com.st6.wc.web.SyncNotRetryableException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link SyncRetryService} unit proof (E23, §10 / safety rules #3/#4/#7). Pins the mechanics the
 * afterCommit republish makes hard to see at the endpoint: the {@code FAILED → RETRY_REQUESTED}
 * transition is surfaced in the RESPONSE (the core-txn snapshot) while the reused {@link
 * SnsLifecyclePublisher} flips the record to {@code QUEUED} via the bare {@link SyncJobPointer}
 * (rule #7); a publish failure is swallowed — the record stays {@code RETRY_REQUESTED}, no
 * exception (rule #4, THE safety pin); the {@code authorizeSyncRecordAccess} chokepoint is the
 * first statement (no save / no republish on denial, §25); a non-{@code FAILED} record is rejected
 * before any write.
 *
 * <p>No ambient transaction here, so {@code SyncRetryService}'s {@code afterCommit} fallback
 * publishes <strong>immediately</strong> — the real publisher (with a mock gateway) runs
 * synchronously, letting us capture the gateway pointer + the rule-#4 swallow without Spring.
 */
class SyncRetryServiceTest {

  private final DomainAuthorizationService authz = mock(DomainAuthorizationService.class);
  private final OutlookCalendarSyncRecordRepository syncRecords =
      mock(OutlookCalendarSyncRecordRepository.class);
  private final LifecycleSnsGateway gateway = mock(LifecycleSnsGateway.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-06-04T12:00:00Z"), ZoneOffset.UTC);
  // the REAL publisher + mapper (both reused verbatim) — only the gateway + repo are mocked.
  private final SnsLifecyclePublisher publisher =
      new SnsLifecyclePublisher(gateway, syncRecords, clock, "test");
  private final AllowedActionResolver resolver = new AllowedActionResolver();
  private final SyncRecordMapper mapper = new SyncRecordMapper(resolver);
  private final SyncRetryService service =
      new SyncRetryService(authz, syncRecords, resolver, mapper, publisher);

  private static UserPrincipal ic(UUID id) {
    return new UserPrincipal(id, RoleType.IC, false);
  }

  private static OutlookCalendarSyncRecord record(UUID ownerId, SyncStatus status) {
    OutlookCalendarSyncRecord r = new OutlookCalendarSyncRecord();
    r.setId(UUID.randomUUID());
    r.setVersion(0L);
    r.setOwnerEmployeeId(ownerId);
    r.setRelatedType(SyncRelatedType.WEEKLY_PLAN);
    r.setRelatedId(UUID.randomUUID());
    r.setEventKind(EventKind.IC_PLANNING);
    r.setStatus(status);
    r.setRetryCount(1);
    r.setFailureCode("GRAPH_FORBIDDEN");
    r.setSafeMessage("Calendar sync failed; you can retry.");
    r.setTraceId("trace-1");
    return r;
  }

  // --- RED #5: FAILED → RETRY_REQUESTED (in the response) + afterCommit republish, bare pointer
  // ---
  @Test
  void retry_failedRecord_transitionsToRetryRequested_andRepublishesBarePointer() {
    UUID owner = UUID.randomUUID();
    OutlookCalendarSyncRecord r = record(owner, SyncStatus.FAILED);
    when(syncRecords.findById(r.getId())).thenReturn(Optional.of(r));

    OutlookSyncRecordDto dto = service.retry(ic(owner), r.getId());

    assertThat(dto.status()).isEqualTo(SyncStatus.RETRY_REQUESTED); // the core-txn transition
    ArgumentCaptor<SyncJobPointer> captor = ArgumentCaptor.forClass(SyncJobPointer.class);
    verify(gateway).publish(captor.capture()); // the reused publisher republished
    SyncJobPointer p = captor.getValue();
    assertThat(p.syncRecordId()).isEqualTo(r.getId());
    assertThat(p.eventKind()).isEqualTo(EventKind.IC_PLANNING);
    assertThat(p.env()).isEqualTo("test");
    assertThat(p.traceId()).isEqualTo("trace-1"); // bare 4-field pointer — rule #7, no PII
  }

  // --- RED #6: the RESPONSE is RETRY_REQUESTED even though the publish flips the DB to QUEUED
  // ------
  @Test
  void retry_responseShowsRetryRequested_notQueued() {
    UUID owner = UUID.randomUUID();
    OutlookCalendarSyncRecord r = record(owner, SyncStatus.FAILED);
    when(syncRecords.findById(r.getId())).thenReturn(Optional.of(r));

    OutlookSyncRecordDto dto = service.retry(ic(owner), r.getId());

    // the returned DTO is the core-txn snapshot (RETRY_REQUESTED), NOT a post-publish re-read;
    // the publisher advanced the entity itself to QUEUED (afterCommit ran immediately, no txn).
    assertThat(dto.status()).isEqualTo(SyncStatus.RETRY_REQUESTED);
    assertThat(r.getStatus()).isEqualTo(SyncStatus.QUEUED);
  }

  // --- RED #8: rule #4 — a republish failure is swallowed; record stays RETRY_REQUESTED, no throw
  // -
  @Test
  void retry_publishFailure_leavesRetryRequested_noThrow() {
    UUID owner = UUID.randomUUID();
    OutlookCalendarSyncRecord r = record(owner, SyncStatus.FAILED);
    when(syncRecords.findById(r.getId())).thenReturn(Optional.of(r));
    doThrow(new RuntimeException("SNS unreachable")).when(gateway).publish(any());

    OutlookSyncRecordDto[] holder = new OutlookSyncRecordDto[1];
    assertThatCode(() -> holder[0] = service.retry(ic(owner), r.getId()))
        .doesNotThrowAnyException(); // the gateway throw NEVER reaches the caller

    assertThat(holder[0].status()).isEqualTo(SyncStatus.RETRY_REQUESTED);
    // the publisher caught the throw → did NOT set QUEUED → the record is retained RETRY_REQUESTED
    assertThat(r.getStatus()).isEqualTo(SyncStatus.RETRY_REQUESTED);
  }

  // --- RED #9: the authorizer is the chokepoint — a denial never reaches the repo / gateway
  // -------
  @Test
  void retry_authorizerChokepoint_firstStatement() {
    UUID syncId = UUID.randomUUID();
    doThrow(new ResourceNotFoundOrUnauthorizedException())
        .when(authz)
        .authorizeSyncRecordAccess(any(), any());

    assertThatThrownBy(() -> service.retry(ic(UUID.randomUUID()), syncId))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);

    verify(syncRecords, never()).save(any());
    verifyNoInteractions(gateway);
  }

  // --- RED #7 (unit): a non-FAILED record → SYNC_NOT_RETRYABLE before any write / republish
  // -------
  @Test
  void retry_nonFailedRecord_throwsSyncNotRetryable_noWrite() {
    UUID owner = UUID.randomUUID();
    OutlookCalendarSyncRecord synced = record(owner, SyncStatus.SYNCED);
    when(syncRecords.findById(synced.getId())).thenReturn(Optional.of(synced));

    assertThatThrownBy(() -> service.retry(ic(owner), synced.getId()))
        .isInstanceOf(SyncNotRetryableException.class);

    verify(syncRecords, never()).save(any());
    verifyNoInteractions(gateway);
    assertThat(synced.getStatus()).isEqualTo(SyncStatus.SYNCED); // unchanged
  }
}
