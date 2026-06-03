package com.st6.wc.sync.repo;

import com.st6.wc.enums.EventKind;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link OutlookCalendarSyncRecord}. The review-block finder returns ≤1
 * row for a {@code (owner, week)} {@code MANAGER_REVIEW_BLOCK} by the V2 per-manager/week partial
 * unique (§10); review/sync services use it.
 */
public interface OutlookCalendarSyncRecordRepository
    extends JpaRepository<OutlookCalendarSyncRecord, UUID> {

  Optional<OutlookCalendarSyncRecord> findByOwnerEmployeeIdAndWeekStartDateAndEventKind(
      UUID ownerEmployeeId, LocalDate weekStartDate, EventKind eventKind);
}
