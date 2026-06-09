package com.st6.wc.sync.repo;

import com.st6.wc.enums.EventKind;
import com.st6.wc.enums.SyncRelatedType;
import com.st6.wc.enums.SyncStatus;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

  /**
   * Existence lookup on the full V1 {@code uq_sync_owner_related_kind} grain {@code (owner,
   * related_type, related_id, event_kind)} — the idempotency pre-filter for the lock/start outbox
   * writers ({@code SyncRecordService.createWeeklyPlanRecord}), so a re-lock or a pre-existing
   * (legacy/orphan) record is a no-op rather than a 23505 that rolls back the core lifecycle txn.
   */
  Optional<OutlookCalendarSyncRecord> findByOwnerEmployeeIdAndRelatedTypeAndRelatedIdAndEventKind(
      UUID ownerEmployeeId, SyncRelatedType relatedType, UUID relatedId, EventKind eventKind);

  List<OutlookCalendarSyncRecord> findByRelatedTypeAndRelatedIdOrderByEventKindAscIdAsc(
      SyncRelatedType relatedType, UUID relatedId);

  /**
   * Atomically CLAIM a record for sync (brief 104 — replaces the worker's read-then-{@code
   * setStatus(SYNCING)} guard, which had a TOCTOU window that collided on {@code @Version} under
   * concurrent at-least-once redelivery and could double-create Graph events). A single conditional
   * {@code UPDATE} transitions the row to {@code SYNCING} only if it is claimable, returning the
   * affected-row count: <strong>1</strong> = this caller is the sole claimer (→ call Graph);
   * <strong>0</strong> = no claimable record (another thread/pod already claimed it, it is
   * terminal/ {@code SYNCED}, or an active claimer holds a recent lease) → the caller no-ops/ACKs.
   * The DB serializes the conditional updates, so exactly one concurrent delivery wins — no
   * optimistic-lock race, no duplicate side-effect.
   *
   * <p>Claimable = {@code QUEUED}/{@code RETRY_REQUESTED}, OR a <em>stale</em> {@code SYNCING}
   * whose {@code lastAttemptAt} predates {@code leaseExpiry} (= {@code now − lease}) — the
   * lease-reclaim that un-strands a crashed claimer (a claimer that died after {@code SYNCING} but
   * before a terminal transition; the §10 redelivery guard would otherwise skip it forever). An
   * <em>active</em> claimer's recent {@code lastAttemptAt} is NOT reclaimed. INVARIANT: this method
   * is the ONLY setter of {@code SYNCING} and it always stamps {@code lastAttemptAt}, so no {@code
   * SYNCING} row ever has a null {@code lastAttemptAt} (which would make {@code lastAttemptAt <
   * :leaseExpiry} NULL→false and strand it). The lease MUST exceed the SQS visibility timeout
   * (infra) so a slow active claimer's redelivery can't reclaim it mid-flight.
   *
   * <p>{@code version} is bumped manually (the bulk update bypasses the {@code @Version} auto-
   * increment) so a subsequent managed load sees a consistent token. Runs in its own short
   * transaction that COMMITS before the (non-transactional) Graph call (§44 — no transaction held
   * across the network). {@code clearAutomatically} evicts any stale persistence-context instance
   * so the post-claim {@code findById} reload is fresh.
   */
  @Modifying(clearAutomatically = true)
  @Transactional
  @Query(
      """
      UPDATE OutlookCalendarSyncRecord r
         SET r.status = com.st6.wc.enums.SyncStatus.SYNCING,
             r.lastAttemptAt = :now,
             r.version = r.version + 1
       WHERE r.id = :id
         AND ( r.status IN (com.st6.wc.enums.SyncStatus.QUEUED, com.st6.wc.enums.SyncStatus.RETRY_REQUESTED)
               OR (r.status = com.st6.wc.enums.SyncStatus.SYNCING AND r.lastAttemptAt < :leaseExpiry) )
      """)
  int claimForSync(
      @Param("id") UUID id, @Param("now") Instant now, @Param("leaseExpiry") Instant leaseExpiry);

  /**
   * Stale in-flight rows for the 104b reaper: those in {@code status} (always {@code SYNCING})
   * whose {@code lastAttemptAt} predates {@code cutoff} (= {@code now − lease}). {@link
   * Pageable}-BOUNDED so a backlog can't be republished unboundedly in one reaper tick. The reaper
   * re-enqueues each via {@code SqsTemplate} → the listener's {@link #claimForSync} lease clause
   * reclaims it (the re-trigger 104's lease clause needs but the no-op-ACK-deleted message can't
   * provide). Only {@code SYNCING} rows are passed (a recent active claimer's {@code lastAttemptAt}
   * is ≥ cutoff → excluded; QUEUED/RETRY_REQUESTED/SYNCED/FAILED/PENDING_PUBLISH are not this
   * status).
   */
  List<OutlookCalendarSyncRecord> findByStatusAndLastAttemptAtBefore(
      SyncStatus status, Instant cutoff, Pageable page);
}
