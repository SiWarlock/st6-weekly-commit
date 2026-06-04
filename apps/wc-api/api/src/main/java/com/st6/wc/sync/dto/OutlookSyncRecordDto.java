package com.st6.wc.sync.dto;

import com.st6.wc.action.AllowedAction;
import com.st6.wc.enums.EventKind;
import com.st6.wc.enums.SyncRelatedType;
import com.st6.wc.enums.SyncStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * B.10 — the Outlook sync-record response DTO (E22 list item; E23 retry response, §10). A {@code
 * record} mirroring the frontend {@code dtos.ts} shape verbatim, never the {@link
 * com.st6.wc.sync.OutlookCalendarSyncRecord} entity (the internal {@code queuedAt}/{@code
 * processedAt}/{@code lastAttemptAt} + audit fields are leak-tested out, §21). {@code safeMessage}
 * is the ONLY user-visible error text — {@code failureCode}/{@code graphEventId}/{@code traceId}
 * are present for the contract but never rendered (rule #7). {@code allowedActions} carries {@code
 * RETRY_SYNC} iff {@code status == FAILED} (affordance↔enforcement single-source via {@code
 * AllowedActionResolver.canRetrySync}, §31).
 */
public record OutlookSyncRecordDto(
    UUID id,
    UUID ownerEmployeeId,
    SyncRelatedType relatedType,
    UUID relatedId,
    EventKind eventKind,
    LocalDate weekStartDate,
    SyncStatus status,
    String graphEventId,
    String failureCode,
    String safeMessage,
    int retryCount,
    String traceId,
    List<AllowedAction> allowedActions,
    long version) {

  public OutlookSyncRecordDto {
    allowedActions = List.copyOf(allowedActions); // defensive immutable copy (§22, no EI exposure)
  }
}
