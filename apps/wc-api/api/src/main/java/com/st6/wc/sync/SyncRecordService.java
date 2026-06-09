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
 * to {@code QUEUED}). {@link #createIcPlanningRecord} (the IC's {@code IC_PLANNING}/{@code
 * WEEKLY_PLAN} lock record) and {@link #createIcReconciliationRecord} ({@code IC_RECONCILIATION})
 * are <strong>idempotent</strong> on the V1 {@code uq_sync_owner_related_kind} grain {@code (owner,
 * related_type, related_id, event_kind)}: the first call creates the {@code PENDING_PUBLISH} row;
 * if one already exists (a re-lock, or a pre-existing/orphan record) it is a no-op that returns
 * {@code Optional.empty()} — so the core lifecycle txn is never rolled back by a 23505. {@link
 * #upsertManagerReviewBlock} is the symmetric idempotent writer on the {@code (owner, week,
 * eventKind)} V2 partial-unique grain. No secrets/PII (rule #7).
 */
@Service
public class SyncRecordService {

  private final OutlookCalendarSyncRecordRepository syncRecords;

  public SyncRecordService(OutlookCalendarSyncRecordRepository syncRecords) {
    this.syncRecords = syncRecords;
  }

  public Optional<OutlookCalendarSyncRecord> createIcPlanningRecord(
      WeeklyPlan plan, String traceId) {
    return createWeeklyPlanRecord(plan, EventKind.IC_PLANNING, traceId);
  }

  /**
   * The IC's {@code IC_RECONCILIATION} record (E9 start-reconciliation, task 4.2) — coexists with
   * the lock's {@code IC_PLANNING} since the V1 sync unique {@code (owner, related_type,
   * related_id, event_kind)} includes {@code event_kind}.
   */
  public Optional<OutlookCalendarSyncRecord> createIcReconciliationRecord(
      WeeklyPlan plan, String traceId) {
    return createWeeklyPlanRecord(plan, EventKind.IC_RECONCILIATION, traceId);
  }

  /**
   * Idempotent create on the V1 {@code uq_sync_owner_related_kind} grain {@code (owner,
   * related_type, related_id, event_kind)}: returns {@code empty} (a no-op) when a record already
   * exists for the grain, else the newly-saved {@code PENDING_PUBLISH} row. The existence
   * pre-filter keeps a re-lock / pre-existing record from raising a {@code
   * DataIntegrityViolationException} (23505) that would roll back the caller's core lifecycle
   * transaction.
   */
  private Optional<OutlookCalendarSyncRecord> createWeeklyPlanRecord(
      WeeklyPlan plan, EventKind eventKind, String traceId) {
    if (syncRecords
        .findByOwnerEmployeeIdAndRelatedTypeAndRelatedIdAndEventKind(
            plan.getEmployeeId(), SyncRelatedType.WEEKLY_PLAN, plan.getId(), eventKind)
        .isPresent()) {
      return Optional
          .empty(); // idempotent — the row already exists; the lock/start is a no-op here
    }
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
    return Optional.of(syncRecords.save(record));
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
