package com.st6.wc.sync;

import com.st6.wc.auth.DomainAuthorizationService;
import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.enums.SyncStatus;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.AllowedActionResolver;
import com.st6.wc.sns.SnsLifecyclePublisher;
import com.st6.wc.sync.dto.OutlookSyncRecordDto;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import com.st6.wc.web.SyncNotRetryableException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * E23 — manual retry of a {@code FAILED} Outlook sync record (§10 / safety rules #3/#4/#7). {@link
 * DomainAuthorizationService#authorizeSyncRecordAccess} is the FIRST statement (the rule-#3
 * chokepoint — admits the IC owner + the active direct manager per the E23 catalog scope; cross-
 * owner/missing → IDOR-safe {@code 404} + denial audit). Only a {@code FAILED} record is retryable
 * (else {@code 409 SYNC_NOT_RETRYABLE}, no write) — the SAME {@link
 * AllowedActionResolver#canRetrySync} predicate the {@code RETRY_SYNC} affordance gates on. On
 * retry it flips {@code FAILED → RETRY_REQUESTED} in the core txn and <strong>reuses the §28 {@link
 * SnsLifecyclePublisher}</strong> to republish the bare pointer afterCommit (the publisher is
 * trigger-agnostic: it sets {@code QUEUED} on success, and on any failure swallows + retains the
 * record — rule #4, the republish never throws to the caller nor rolls back the {@code
 * RETRY_REQUESTED} write).
 *
 * <p>The response DTO is the core-txn snapshot ({@code RETRY_REQUESTED}) mapped BEFORE the
 * afterCommit publish advances the DB to {@code QUEUED} — matching the frontend contract.
 */
@Service
public class SyncRetryService {

  private final DomainAuthorizationService authz;
  private final OutlookCalendarSyncRecordRepository syncRecords;
  private final AllowedActionResolver resolver;
  private final SyncRecordMapper mapper;
  private final SnsLifecyclePublisher publisher;

  public SyncRetryService(
      DomainAuthorizationService authz,
      OutlookCalendarSyncRecordRepository syncRecords,
      AllowedActionResolver resolver,
      SyncRecordMapper mapper,
      SnsLifecyclePublisher publisher) {
    this.authz = authz;
    this.syncRecords = syncRecords;
    this.resolver = resolver;
    this.mapper = mapper;
    this.publisher = publisher;
  }

  @Transactional
  public OutlookSyncRecordDto retry(UserPrincipal actor, UUID syncRecordId) {
    authz.authorizeSyncRecordAccess(
        actor, syncRecordId); // chokepoint FIRST (IDOR 404 + denial audit)
    OutlookCalendarSyncRecord record =
        syncRecords
            .findById(syncRecordId)
            .orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
    if (!resolver.canRetrySync(record.getStatus())) {
      throw new SyncNotRetryableException(); // 409 — only a FAILED record is retryable (no write)
    }

    record.setStatus(SyncStatus.RETRY_REQUESTED); // the core-txn intent state
    syncRecords.save(record);
    // Snapshot the response BEFORE the afterCommit publish flips the DB to QUEUED (contract).
    OutlookSyncRecordDto dto = mapper.toDto(record);
    publishAfterCommit(syncRecordId); // reuse the §28 publisher — bare pointer, rule #4/#7
    return dto;
  }

  /**
   * Republish after the retry commits — mirrors {@code PlanLifecycleService.publishAfterCommit}
   * (rule #4: a publish failure can never roll back the committed {@code RETRY_REQUESTED} write).
   * With no active transaction (a unit test), publish immediately.
   */
  private void publishAfterCommit(UUID syncRecordId) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              publisher.publish(syncRecordId);
            }
          });
    } else {
      publisher.publish(syncRecordId);
    }
  }
}
