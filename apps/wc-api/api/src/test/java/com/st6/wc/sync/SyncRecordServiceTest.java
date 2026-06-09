package com.st6.wc.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.st6.wc.enums.EventKind;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.SyncRelatedType;
import com.st6.wc.enums.SyncStatus;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit proof that the lock/start outbox sync-record creation is <strong>idempotent</strong> on the
 * V1 sync unique grain {@code (owner, related_type, related_id, event_kind)} — the documented
 * "later locks are no-ops" contract for {@code createWeeklyPlanRecord}. Regression for the live
 * lock 500: a {@code DRAFT} plan that already carries an {@code IC_PLANNING} sync record (a
 * re-lock, or legacy/orphan data) must be a no-op, never violate {@code uq_sync_owner_related_kind}
 * (23505) and roll back the lock. Mirrors the pre-existing idempotency of {@link
 * SyncRecordService#upsertManagerReviewBlock}.
 */
class SyncRecordServiceTest {

  private final OutlookCalendarSyncRecordRepository syncRecords =
      mock(OutlookCalendarSyncRecordRepository.class);
  private final SyncRecordService service = new SyncRecordService(syncRecords);

  private static final UUID OWNER = UUID.randomUUID();
  private static final UUID PLAN_ID = UUID.randomUUID();
  private static final LocalDate WEEK = LocalDate.of(2026, 6, 8);

  private static WeeklyPlan plan() {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(PLAN_ID);
    p.setEmployeeId(OWNER);
    p.setWeekStartDate(WEEK);
    p.setState(PlanState.DRAFT);
    return p;
  }

  // --- absent → create one PENDING_PUBLISH record on the (owner, WEEKLY_PLAN, planId, kind) grain
  // --
  @Test
  void createIcPlanningRecord_whenNoneExists_savesPendingPublishAndReturnsIt() {
    when(syncRecords.findByOwnerEmployeeIdAndRelatedTypeAndRelatedIdAndEventKind(
            OWNER, SyncRelatedType.WEEKLY_PLAN, PLAN_ID, EventKind.IC_PLANNING))
        .thenReturn(Optional.empty());
    when(syncRecords.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<OutlookCalendarSyncRecord> result = service.createIcPlanningRecord(plan(), "trace-1");

    assertThat(result).isPresent();
    OutlookCalendarSyncRecord saved = result.orElseThrow();
    assertThat(saved.getOwnerEmployeeId()).isEqualTo(OWNER);
    assertThat(saved.getRelatedType()).isEqualTo(SyncRelatedType.WEEKLY_PLAN);
    assertThat(saved.getRelatedId()).isEqualTo(PLAN_ID);
    assertThat(saved.getEventKind()).isEqualTo(EventKind.IC_PLANNING);
    assertThat(saved.getStatus()).isEqualTo(SyncStatus.PENDING_PUBLISH);
    assertThat(saved.getWeekStartDate()).isEqualTo(WEEK);
    verify(syncRecords).save(any());
  }

  // --- present → idempotent no-op (the live 500 regression: never a duplicate INSERT → no 23505)
  // --
  @Test
  void createIcPlanningRecord_whenRecordAlreadyExists_isNoOpReturnsEmpty() {
    OutlookCalendarSyncRecord existing = new OutlookCalendarSyncRecord();
    existing.setId(UUID.randomUUID());
    when(syncRecords.findByOwnerEmployeeIdAndRelatedTypeAndRelatedIdAndEventKind(
            OWNER, SyncRelatedType.WEEKLY_PLAN, PLAN_ID, EventKind.IC_PLANNING))
        .thenReturn(Optional.of(existing));

    Optional<OutlookCalendarSyncRecord> result = service.createIcPlanningRecord(plan(), "trace-2");

    assertThat(result).isEmpty(); // the lock succeeds + skips a redundant re-publish
    verify(syncRecords, never()).save(any()); // never a uq_sync_owner_related_kind 23505
  }

  // --- IC_RECONCILIATION shares the idempotent grain (distinct event_kind coexists with planning)
  // -
  @Test
  void createIcReconciliationRecord_whenRecordAlreadyExists_isNoOpReturnsEmpty() {
    OutlookCalendarSyncRecord existing = new OutlookCalendarSyncRecord();
    existing.setId(UUID.randomUUID());
    when(syncRecords.findByOwnerEmployeeIdAndRelatedTypeAndRelatedIdAndEventKind(
            OWNER, SyncRelatedType.WEEKLY_PLAN, PLAN_ID, EventKind.IC_RECONCILIATION))
        .thenReturn(Optional.of(existing));

    Optional<OutlookCalendarSyncRecord> result =
        service.createIcReconciliationRecord(plan(), "trace-3");

    assertThat(result).isEmpty();
    verify(syncRecords, never()).save(any());
  }
}
