package com.st6.wc.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.dispute.AlignmentDispute;
import com.st6.wc.dispute.repo.AlignmentDisputeRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.DisputeStatus;
import com.st6.wc.enums.EventKind;
import com.st6.wc.enums.FlagType;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.SyncRelatedType;
import com.st6.wc.enums.SyncStatus;
import com.st6.wc.enums.WorkType;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.ManagerReview;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.support.AbstractJpaIntegrationTest;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Repository/ORM-layer proof for the Phase-1 invariants (task 1.6). The DDL constraints themselves
 * are proven at the raw-SQL layer (1.2 enum↔CHECK, 1.3 partial-uniques firing/non-firing); this
 * class proves the value-add that Phase 2+ services depend on: each unique/partial-unique fires
 * through {@code saveAndFlush} as a Spring {@link DataIntegrityViolationException},
 * {@code @Version} optimistic locking increments + raises {@link
 * ObjectOptimisticLockingFailureException} on a stale update (ORM-only, genuinely new vs 1.2), and
 * the three partial-unique-backed finder queries return the right rows. Reuses {@link
 * AbstractJpaIntegrationTest} (singleton PG16 + Flyway V1–V3).
 */
class RepositoryConstraintTest extends AbstractJpaIntegrationTest {

  @Autowired private TestEntityManager em;
  @Autowired private EmployeeRepository employees;
  @Autowired private ManagerRelationshipRepository relationships;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private WeeklyCommitmentRepository commitments;
  @Autowired private ManagerReviewRepository reviews;
  @Autowired private AlignmentDisputeRepository disputes;
  @Autowired private OutlookCalendarSyncRecordRepository syncRecords;

  private static final LocalDate WEEK = LocalDate.parse("2026-09-07");
  private static final Instant TS = Instant.parse("2026-06-02T12:00:00Z");

  // ===== Constraint proofs: violation -> Spring DataIntegrityViolationException =====

  // --- 1. weekly_plan unique(employee_id, week_start_date) ------------------
  @Test
  void plan_unique_employee_week_violation_raises() {
    Employee ic = saveEmployee(RoleType.IC);
    plans.saveAndFlush(newPlan(ic.getId(), WEEK));
    assertThatThrownBy(() -> plans.saveAndFlush(newPlan(ic.getId(), WEEK)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // --- 2. single-active-manager partial unique (§6) -------------------------
  @Test
  void single_active_manager_partial_unique() {
    Employee report = saveEmployee(RoleType.IC);
    Employee m1 = saveEmployee(RoleType.MANAGER);
    Employee m2 = saveEmployee(RoleType.MANAGER);
    // one active + one inactive for the same report coexist (partial WHERE active)
    relationships.saveAndFlush(newRelationship(m1.getId(), report.getId(), true));
    relationships.saveAndFlush(newRelationship(m2.getId(), report.getId(), false));
    assertThat(relationships.count()).isEqualTo(2);
    // a SECOND active manager for the same report fires the partial unique
    Employee m3 = saveEmployee(RoleType.MANAGER);
    assertThatThrownBy(
            () -> relationships.saveAndFlush(newRelationship(m3.getId(), report.getId(), true)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // --- 3. one-unresolved-dispute partial unique (safety rule #6) ------------
  @Test
  void one_unresolved_dispute_partial_unique() {
    UUID commitmentId = persistCommitment();
    Employee mgr = saveEmployee(RoleType.MANAGER);
    // an OPEN + a RESOLVED dispute on the same commitment coexist (partial WHERE OPEN/IC_RESPONDED)
    disputes.saveAndFlush(newDispute(commitmentId, mgr.getId(), DisputeStatus.OPEN));
    disputes.saveAndFlush(newDispute(commitmentId, mgr.getId(), DisputeStatus.RESOLVED));
    assertThat(disputes.count()).isEqualTo(2);
    // a second UNRESOLVED dispute fires the partial unique — use IC_RESPONDED (not a second OPEN):
    // "unresolved" spans BOTH {OPEN, IC_RESPONDED}, so this pins that the index treats the two as
    // one uniqueness bucket (OPEN+OPEN would still pass if the predicate were wrongly narrowed to
    // status='OPEN', silently breaking safety rule #6).
    assertThatThrownBy(
            () ->
                disputes.saveAndFlush(
                    newDispute(commitmentId, mgr.getId(), DisputeStatus.IC_RESPONDED)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // --- 4. per-manager/week review-block partial unique (§10) ----------------
  @Test
  void review_block_per_manager_week_partial_unique() {
    Employee owner = saveEmployee(RoleType.MANAGER);
    // distinct related_id isolates from the V1 full unique (LESSONS §5); same (owner, week, kind)
    syncRecords.saveAndFlush(newReviewBlock(owner.getId(), WEEK, UUID.randomUUID()));
    assertThatThrownBy(
            () -> syncRecords.saveAndFlush(newReviewBlock(owner.getId(), WEEK, UUID.randomUUID())))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // ===== @Version optimistic locking (ORM-only) =====

  // --- 5. version increments on update, across the 5 mutable entities -------
  @Test
  void version_increments_on_update() {
    Employee ic = saveEmployee(RoleType.IC);
    Employee mgr = saveEmployee(RoleType.MANAGER);

    WeeklyPlan plan = plans.saveAndFlush(newPlan(ic.getId(), WEEK));
    WeeklyCommitment commitment = commitments.saveAndFlush(newCommitment(plan.getId()));
    ManagerReview review = reviews.saveAndFlush(newReview(plan.getId(), mgr.getId()));
    AlignmentDispute dispute =
        disputes.saveAndFlush(newDispute(commitment.getId(), mgr.getId(), DisputeStatus.OPEN));
    OutlookCalendarSyncRecord sync =
        syncRecords.saveAndFlush(newReviewBlock(ic.getId(), WEEK, UUID.randomUUID()));

    // all five start at version 0 after insert
    assertThat(plan.getVersion()).isZero();
    assertThat(commitment.getVersion()).isZero();
    assertThat(review.getVersion()).isZero();
    assertThat(dispute.getVersion()).isZero();
    assertThat(sync.getVersion()).isZero();

    plan.setLockedAt(TS);
    commitment.setManagerAlignmentNote("note");
    review.setSummaryNote("summary");
    dispute.setIcResponse("response");
    sync.setStatus(SyncStatus.QUEUED);

    assertThat(plans.saveAndFlush(plan).getVersion()).isEqualTo(1L);
    assertThat(commitments.saveAndFlush(commitment).getVersion()).isEqualTo(1L);
    assertThat(reviews.saveAndFlush(review).getVersion()).isEqualTo(1L);
    assertThat(disputes.saveAndFlush(dispute).getVersion()).isEqualTo(1L);
    assertThat(syncRecords.saveAndFlush(sync).getVersion()).isEqualTo(1L);
  }

  // --- 6. stale-version update raises optimistic-lock conflict --------------
  @Test
  void stale_version_update_raises_optimistic_lock() {
    Employee ic = saveEmployee(RoleType.IC);
    WeeklyPlan persisted = plans.saveAndFlush(newPlan(ic.getId(), WEEK));
    UUID id = persisted.getId();
    em.clear();

    // two independent snapshots, both at version 0
    WeeklyPlan stale = plans.findById(id).orElseThrow();
    em.detach(stale);
    WeeklyPlan fresh = plans.findById(id).orElseThrow();

    // fresh saves first -> DB version bumps to 1
    fresh.setLockedAt(TS);
    plans.saveAndFlush(fresh);

    // stale (still version 0) saving second -> optimistic-lock conflict
    stale.setLockedAt(Instant.parse("2026-06-03T00:00:00Z"));
    assertThatThrownBy(() -> plans.saveAndFlush(stale))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }

  // ===== Finder queries =====

  // --- 7. active-manager finder returns the single active relationship ------
  @Test
  void active_manager_finder_returns_single() {
    Employee report = saveEmployee(RoleType.IC);
    Employee active = saveEmployee(RoleType.MANAGER);
    Employee inactive = saveEmployee(RoleType.MANAGER);
    relationships.saveAndFlush(newRelationship(active.getId(), report.getId(), true));
    relationships.saveAndFlush(newRelationship(inactive.getId(), report.getId(), false));

    Optional<ManagerRelationship> found =
        relationships.findByDirectReportEmployeeIdAndActiveTrue(report.getId());
    assertThat(found)
        .get()
        .satisfies(
            r -> {
              assertThat(r.isActive()).isTrue();
              assertThat(r.getManagerEmployeeId()).isEqualTo(active.getId());
            });
  }

  // --- 7b. active-manager finder is empty when no ACTIVE relationship -------
  @Test
  void active_manager_finder_empty_when_no_active() {
    Employee report = saveEmployee(RoleType.IC);
    Employee inactive = saveEmployee(RoleType.MANAGER);
    relationships.saveAndFlush(newRelationship(inactive.getId(), report.getId(), false));
    // the deny-branch DomainAuthorizationService (Phase 2) relies on: no active manager -> empty
    assertThat(relationships.findByDirectReportEmployeeIdAndActiveTrue(report.getId())).isEmpty();
  }

  // --- 8. unresolved-dispute finder excludes RESOLVED -----------------------
  @Test
  void unresolved_dispute_finder_excludes_resolved() {
    UUID commitmentId = persistCommitment();
    Employee mgr = saveEmployee(RoleType.MANAGER);
    disputes.saveAndFlush(newDispute(commitmentId, mgr.getId(), DisputeStatus.OPEN));
    disputes.saveAndFlush(newDispute(commitmentId, mgr.getId(), DisputeStatus.RESOLVED));

    Optional<AlignmentDispute> found =
        disputes.findByCommitmentIdAndStatusIn(
            commitmentId, List.of(DisputeStatus.OPEN, DisputeStatus.IC_RESPONDED));
    assertThat(found).get().extracting(AlignmentDispute::getStatus).isEqualTo(DisputeStatus.OPEN);
  }

  // --- 8b. the List-In finder (6.2) returns OPEN+IC_RESPONDED across commitments, excludes
  // RESOLVED
  // (the one query backing the §9 projection dispute-count/union derivation) -------------------
  @Test
  void unresolved_disputes_in_finder_returns_across_statuses_excludes_resolved() {
    UUID c1 = persistCommitment();
    UUID c2 = persistCommitment();
    Employee mgr = saveEmployee(RoleType.MANAGER);
    disputes.saveAndFlush(newDispute(c1, mgr.getId(), DisputeStatus.OPEN));
    disputes.saveAndFlush(
        newDispute(c1, mgr.getId(), DisputeStatus.RESOLVED)); // coexists, excluded
    disputes.saveAndFlush(newDispute(c2, mgr.getId(), DisputeStatus.IC_RESPONDED));

    List<AlignmentDispute> found =
        disputes.findByCommitmentIdInAndStatusIn(
            List.of(c1, c2), List.of(DisputeStatus.OPEN, DisputeStatus.IC_RESPONDED));
    assertThat(found)
        .hasSize(2)
        .extracting(AlignmentDispute::getStatus)
        .containsExactlyInAnyOrder(DisputeStatus.OPEN, DisputeStatus.IC_RESPONDED);
  }

  // --- 9. review-block finder returns the seeded block ----------------------
  @Test
  void review_block_finder_returns_block() {
    Employee owner = saveEmployee(RoleType.MANAGER);
    OutlookCalendarSyncRecord block =
        syncRecords.saveAndFlush(newReviewBlock(owner.getId(), WEEK, UUID.randomUUID()));

    Optional<OutlookCalendarSyncRecord> found =
        syncRecords.findByOwnerEmployeeIdAndWeekStartDateAndEventKind(
            owner.getId(), WEEK, EventKind.MANAGER_REVIEW_BLOCK);
    assertThat(found).get().extracting(OutlookCalendarSyncRecord::getId).isEqualTo(block.getId());
  }

  // ===== fixtures =====

  private Employee saveEmployee(RoleType role) {
    Employee e = new Employee();
    e.setId(UUID.randomUUID());
    e.setEmail(UUID.randomUUID() + "@x.test");
    e.setDisplayName("N");
    e.setRole(role);
    e.setActive(true);
    return employees.saveAndFlush(e);
  }

  private ManagerRelationship newRelationship(UUID managerId, UUID reportId, boolean active) {
    ManagerRelationship r = new ManagerRelationship();
    r.setId(UUID.randomUUID());
    r.setManagerEmployeeId(managerId);
    r.setDirectReportEmployeeId(reportId);
    r.setActive(active);
    return r;
  }

  private WeeklyPlan newPlan(UUID employeeId, LocalDate week) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(employeeId);
    p.setWeekStartDate(week);
    p.setWeekEndDate(week.plusDays(6));
    p.setState(PlanState.DRAFT);
    return p;
  }

  private UUID persistCommitment() {
    Employee ic = saveEmployee(RoleType.IC);
    WeeklyPlan plan = plans.saveAndFlush(newPlan(ic.getId(), WEEK));
    return commitments.saveAndFlush(newCommitment(plan.getId())).getId();
  }

  private WeeklyCommitment newCommitment(UUID planId) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(planId);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("commit");
    c.setPriority(Priority.P1);
    c.setWorkType(WorkType.STRATEGIC);
    c.setConfidence(Confidence.HIGH);
    c.setAlignmentStatus(AlignmentStatus.ALIGNED);
    return c;
  }

  private ManagerReview newReview(UUID planId, UUID managerId) {
    ManagerReview r = new ManagerReview();
    r.setId(UUID.randomUUID());
    r.setWeeklyPlanId(planId);
    r.setManagerEmployeeId(managerId);
    r.setStatus(ReviewStatus.NOT_REVIEWED);
    r.setReviewDueAt(TS);
    return r;
  }

  private AlignmentDispute newDispute(UUID commitmentId, UUID managerId, DisputeStatus status) {
    AlignmentDispute d = new AlignmentDispute();
    d.setId(UUID.randomUUID());
    d.setCommitmentId(commitmentId);
    d.setManagerEmployeeId(managerId);
    d.setStatus(status);
    d.setFlagType(FlagType.NEEDS_REVISION);
    d.setManagerNote("note");
    return d;
  }

  private OutlookCalendarSyncRecord newReviewBlock(UUID ownerId, LocalDate week, UUID relatedId) {
    OutlookCalendarSyncRecord s = new OutlookCalendarSyncRecord();
    s.setId(UUID.randomUUID());
    s.setOwnerEmployeeId(ownerId);
    s.setRelatedType(SyncRelatedType.MANAGER_REVIEW_WEEK);
    s.setRelatedId(relatedId);
    s.setEventKind(EventKind.MANAGER_REVIEW_BLOCK);
    s.setStatus(SyncStatus.PENDING_PUBLISH);
    s.setWeekStartDate(week);
    return s;
  }
}
