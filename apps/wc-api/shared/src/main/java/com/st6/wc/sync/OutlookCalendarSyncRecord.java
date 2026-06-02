package com.st6.wc.sync;

import com.st6.wc.common.PersistableUuidEntity;
import com.st6.wc.enums.EventKind;
import com.st6.wc.enums.SyncRelatedType;
import com.st6.wc.enums.SyncStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Outlook calendar sync record — the durable outbox entity (Appendix A / §4 / §10), maps {@code
 * outlook_calendar_sync_record}. Carries delta 4: {@code weekStartDate} PRESENT and {@code
 * relatedType} over {@code MANAGER_REVIEW_WEEK}. A Graph/SNS failure lands here as a FAILED record
 * — never rolls back the core lifecycle (safety rule #4), enforced by services in Phase 8.
 * {@code @Version} via {@link PersistableUuidEntity}.
 */
@Entity
@Table(name = "outlook_calendar_sync_record")
@Getter
@Setter
public class OutlookCalendarSyncRecord extends PersistableUuidEntity {

  @Column(nullable = false)
  private UUID ownerEmployeeId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SyncRelatedType relatedType;

  @Column(nullable = false)
  private UUID relatedId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private EventKind eventKind;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SyncStatus status;

  private String graphEventId;

  private String failureCode;

  private String safeMessage;

  private Instant lastAttemptAt;

  private Instant queuedAt;

  private Instant processedAt;

  @Column(nullable = false)
  private int retryCount;

  private String traceId;

  private LocalDate weekStartDate;
}
