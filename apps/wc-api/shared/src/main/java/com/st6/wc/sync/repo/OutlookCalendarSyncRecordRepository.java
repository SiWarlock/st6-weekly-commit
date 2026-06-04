package com.st6.wc.sync.repo;

import com.st6.wc.enums.EventKind;
import com.st6.wc.enums.SyncRelatedType;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link OutlookCalendarSyncRecord}. The review-block finder returns ≤1
 * row for a {@code (owner, week)} {@code MANAGER_REVIEW_BLOCK} by the V2 per-manager/week partial
 * unique (§10); review/sync services use it. The E22 finder lists a plan's records ({@code
 * WEEKLY_PLAN}/{@code planId} — its {@code IC_PLANNING}+{@code IC_RECONCILIATION}) server-ordered
 * {@code eventKind ASC, id ASC} (deterministic; the {@code queuedAt}/{@code lastAttemptAt} columns
 * are null for un-published records, so they're unfit sort keys).
 */
public interface OutlookCalendarSyncRecordRepository
    extends JpaRepository<OutlookCalendarSyncRecord, UUID> {

  Optional<OutlookCalendarSyncRecord> findByOwnerEmployeeIdAndWeekStartDateAndEventKind(
      UUID ownerEmployeeId, LocalDate weekStartDate, EventKind eventKind);

  List<OutlookCalendarSyncRecord> findByRelatedTypeAndRelatedIdOrderByEventKindAscIdAsc(
      SyncRelatedType relatedType, UUID relatedId);
}
