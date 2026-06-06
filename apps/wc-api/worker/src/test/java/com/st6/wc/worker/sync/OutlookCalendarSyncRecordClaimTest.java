package com.st6.wc.worker.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.st6.wc.enums.EventKind;
import com.st6.wc.enums.SyncRelatedType;
import com.st6.wc.enums.SyncStatus;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import com.st6.wc.worker.support.WorkerPostgresSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Deterministic CAS proof for {@link OutlookCalendarSyncRecordRepository#claimForSync} (brief 104 —
 * the atomic claim that replaces the worker's read-then-{@code setStatus(SYNCING)} TOCTOU guard).
 * Real PG16 via the §44 {@link WorkerPostgresSupport} harness; NO threads — the row-state matrix is
 * exercised with fixed instants, so the mutual-exclusion logic the DB serialization reduces to is
 * pinned deterministically (we just killed a flaky test — no thread-race timing here). The
 * losing-thread no-op is the "second claim returns 0" case; the lease reclaim is the
 * old-vs-recent-{@code SYNCING} split.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OutlookCalendarSyncRecordClaimTest extends WorkerPostgresSupport {

  private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
  private static final Duration LEASE = Duration.ofMinutes(5);
  private static final Instant LEASE_EXPIRY = NOW.minus(LEASE);

  @Autowired private OutlookCalendarSyncRecordRepository syncRecords;
  @Autowired private JdbcTemplate jdbc;

  /** A minimal owner employee — the sync record FKs {@code owner_employee_id → employee(id)}. */
  private UUID insertEmployee() {
    UUID empId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO employee (id, email, display_name, role, active) VALUES (?, ?, ?, 'IC', true)",
        empId,
        empId + "@example.test",
        "Claim Test Owner");
    return empId;
  }

  private UUID seed(SyncStatus status, Instant lastAttemptAt) {
    OutlookCalendarSyncRecord r = new OutlookCalendarSyncRecord();
    r.setId(UUID.randomUUID());
    r.setOwnerEmployeeId(insertEmployee());
    r.setRelatedType(SyncRelatedType.WEEKLY_PLAN);
    r.setRelatedId(UUID.randomUUID());
    r.setEventKind(EventKind.IC_PLANNING);
    r.setStatus(status);
    r.setRetryCount(0);
    r.setLastAttemptAt(lastAttemptAt);
    return syncRecords.saveAndFlush(r).getId();
  }

  private OutlookCalendarSyncRecord reload(UUID id) {
    return syncRecords.findById(id).orElseThrow();
  }

  // --- claimable states win the claim ----------------------------------------------------------
  @Test
  void queued_isClaimed_toSyncing() {
    UUID id = seed(SyncStatus.QUEUED, null);

    int claimed = syncRecords.claimForSync(id, NOW, LEASE_EXPIRY);

    assertThat(claimed).isEqualTo(1);
    OutlookCalendarSyncRecord r = reload(id);
    assertThat(r.getStatus()).isEqualTo(SyncStatus.SYNCING);
    assertThat(r.getLastAttemptAt()).isEqualTo(NOW);
    // manual version bump (the bulk UPDATE bypasses the @Version auto-increment): 0 → 1.
    assertThat(r.getVersion()).isEqualTo(1L);
  }

  @Test
  void retryRequested_isClaimed_toSyncing() {
    UUID id = seed(SyncStatus.RETRY_REQUESTED, null);

    assertThat(syncRecords.claimForSync(id, NOW, LEASE_EXPIRY)).isEqualTo(1);
    assertThat(reload(id).getStatus()).isEqualTo(SyncStatus.SYNCING);
  }

  // --- the losing thread's no-op: a just-claimed (recent SYNCING) row is NOT re-claimed ---------
  @Test
  void secondClaim_onJustClaimedRow_returns0() {
    UUID id = seed(SyncStatus.RETRY_REQUESTED, null);

    assertThat(syncRecords.claimForSync(id, NOW, LEASE_EXPIRY)).isEqualTo(1); // first wins

    // The row is now SYNCING with lastAttemptAt = NOW (recent, within lease) → the second
    // concurrent
    // delivery finds nothing claimable → 0 (the loser no-ops; no @Version collision, no 2nd Graph).
    assertThat(syncRecords.claimForSync(id, NOW, LEASE_EXPIRY)).isEqualTo(0);
    assertThat(reload(id).getStatus()).isEqualTo(SyncStatus.SYNCING);
  }

  // --- lease reclaim: stale SYNCING reclaimable, recent SYNCING not -----------------------------
  @Test
  void staleSyncing_olderThanLease_isReclaimed() {
    // a claimer crashed long ago: lastAttemptAt 10min < the 5min lease window → reclaimable.
    UUID id = seed(SyncStatus.SYNCING, NOW.minus(Duration.ofMinutes(10)));

    assertThat(syncRecords.claimForSync(id, NOW, LEASE_EXPIRY)).isEqualTo(1);
    OutlookCalendarSyncRecord r = reload(id);
    assertThat(r.getStatus()).isEqualTo(SyncStatus.SYNCING);
    assertThat(r.getLastAttemptAt()).isEqualTo(NOW); // the lease was renewed by the reclaim
  }

  @Test
  void recentSyncing_withinLease_isNotReclaimed() {
    // an active claimer attempted 30s ago — well within the 5min lease → must NOT be reclaimed.
    UUID id = seed(SyncStatus.SYNCING, NOW.minus(Duration.ofSeconds(30)));

    assertThat(syncRecords.claimForSync(id, NOW, LEASE_EXPIRY)).isEqualTo(0);
  }

  // --- terminal / non-claimable states are never claimed ----------------------------------------
  @Test
  void synced_isNotClaimed() {
    UUID id = seed(SyncStatus.SYNCED, NOW.minus(Duration.ofMinutes(10)));
    assertThat(syncRecords.claimForSync(id, NOW, LEASE_EXPIRY)).isEqualTo(0);
  }

  @Test
  void failed_isNotClaimed() {
    UUID id = seed(SyncStatus.FAILED, NOW.minus(Duration.ofMinutes(10)));
    assertThat(syncRecords.claimForSync(id, NOW, LEASE_EXPIRY)).isEqualTo(0);
  }

  @Test
  void pendingPublish_isNotClaimed() {
    UUID id = seed(SyncStatus.PENDING_PUBLISH, null);
    assertThat(syncRecords.claimForSync(id, NOW, LEASE_EXPIRY)).isEqualTo(0);
  }

  // --- an unknown id claims nothing (the vanished-pointer no-op) ---------------------------------
  @Test
  void unknownId_claimsNothing() {
    assertThat(syncRecords.claimForSync(UUID.randomUUID(), NOW, LEASE_EXPIRY)).isEqualTo(0);
  }
}
