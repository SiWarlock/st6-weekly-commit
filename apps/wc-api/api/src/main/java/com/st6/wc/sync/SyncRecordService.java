package com.st6.wc.sync;

import com.st6.wc.enums.EventKind;
import com.st6.wc.enums.SyncRelatedType;
import com.st6.wc.enums.SyncStatus;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Writes the lifecycle outbox sync records inside the core lock transaction (task 3.5, §10) — each
 * created {@code PENDING_PUBLISH} (the post-commit {@code SnsLifecyclePublisher} publishes + flips
 * to {@code QUEUED}). {@link #createIcPlanningRecord} is the IC's {@code IC_PLANNING}/{@code
 * WEEKLY_PLAN} record (one per lock). {@link #upsertManagerReviewBlock} is the locking IC's direct
 * manager's per-week {@code MANAGER_REVIEW_BLOCK} — <strong>idempotent</strong> on the {@code
 * (owner, week, eventKind)} V2 partial-unique grain: the first direct-report lock of a manager/week
 * creates it; later locks are no-ops (returns empty). No secrets/PII (rule #7).
 */
@Service
public class SyncRecordService {

  private final OutlookCalendarSyncRecordRepository syncRecords;

  public SyncRecordService(OutlookCalendarSyncRecordRepository syncRecords) {
    this.syncRecords = syncRecords;
  }

  public OutlookCalendarSyncRecord createIcPlanningRecord(WeeklyPlan plan, String traceId) {
    return createWeeklyPlanRecord(plan, EventKind.IC_PLANNING, traceId);
  }

  /**
   * The IC's {@code IC_RECONCILIATION} record (E9 start-reconciliation, task 4.2) — coexists with
   * the lock's {@code IC_PLANNING} since the V1 sync unique {@code (owner, related_type,
   * related_id, event_kind)} includes {@code event_kind}.
   */
  public OutlookCalendarSyncRecord createIcReconciliationRecord(WeeklyPlan plan, String traceId) {
    return createWeeklyPlanRecord(plan, EventKind.IC_RECONCILIATION, traceId);
  }

  private OutlookCalendarSyncRecord createWeeklyPlanRecord(
      WeeklyPlan plan, EventKind eventKind, String traceId) {
    OutlookCalendarSyncRecord record = new OutlookCalendarSyncRecord();
    record.setId(UUID.randomUUID());
    record.setOwnerEmployeeId(plan.getEmployeeId());
    record.setRelatedType(SyncRelatedType.WEEKLY_PLAN);
    record.setRelatedId(plan.getId());
    record.setEventKind(eventKind);
    record.setStatus(SyncStatus.PENDING_PUBLISH);
    record.setRetryCount(0);
    record.setWeekStartDate(plan.getWeekStartDate());
    record.setTraceId(traceId);
    return syncRecords.save(record);
  }

  public Optional<OutlookCalendarSyncRecord> upsertManagerReviewBlock(
      UUID managerId, LocalDate weekStartDate, String traceId) {
    if (syncRecords
        .findByOwnerEmployeeIdAndWeekStartDateAndEventKind(
            managerId, weekStartDate, EventKind.MANAGER_REVIEW_BLOCK)
        .isPresent()) {
      return Optional.empty(); // idempotent — a prior direct-report's lock already created it
    }
    OutlookCalendarSyncRecord record = new OutlookCalendarSyncRecord();
    record.setId(UUID.randomUUID());
    record.setOwnerEmployeeId(managerId);
    record.setRelatedType(SyncRelatedType.MANAGER_REVIEW_WEEK);
    record.setRelatedId(managerId); // per-(owner,week,kind) partial-unique is the real key
    record.setEventKind(EventKind.MANAGER_REVIEW_BLOCK);
    record.setStatus(SyncStatus.PENDING_PUBLISH);
    record.setRetryCount(0);
    record.setWeekStartDate(weekStartDate);
    record.setTraceId(traceId);
    return Optional.of(syncRecords.save(record));
  }
}
