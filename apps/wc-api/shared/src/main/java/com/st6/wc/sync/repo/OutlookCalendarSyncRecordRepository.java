package com.st6.wc.sync.repo;

import com.st6.wc.sync.OutlookCalendarSyncRecord;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Bare Spring Data repository for {@link OutlookCalendarSyncRecord} (task 1.5). Finders: 1.6. */
public interface OutlookCalendarSyncRecordRepository
    extends JpaRepository<OutlookCalendarSyncRecord, UUID> {}
