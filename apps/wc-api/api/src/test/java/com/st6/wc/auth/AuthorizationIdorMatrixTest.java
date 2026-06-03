package com.st6.wc.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.st6.wc.audit.AuditEvent;
import com.st6.wc.audit.AuditService;
import com.st6.wc.audit.repo.AuditEventRepository;
import com.st6.wc.comment.repo.CommentRepository;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.dispute.AlignmentDispute;
import com.st6.wc.dispute.repo.AlignmentDisputeRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommentTargetType;
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
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.ManagerHeatmapCell;
import com.st6.wc.projection.repo.ManagerHeatmapCellRepository;
import com.st6.wc.rcdo.DefiningObjective;
import com.st6.wc.rcdo.RallyCry;
import com.st6.wc.rcdo.repo.DefiningObjectiveRepository;
import com.st6.wc.rcdo.repo.RallyCryRepository;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.ManagerReview;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.support.AbstractJpaIntegrationTest;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * The §17 IDOR matrix (task 2.5, SAFETY-CRITICAL — rule #3 / RISK-001 / RISK-016) against a real
 * PG16 ({@link AbstractJpaIntegrationTest}). Seeds two ICs + a manager (active report = ic1) + a
 * second manager, with plans/commitments/disputes/heatmap-cells/sync-records, then drives every
 * required §6 denial case end-to-end through a real {@link DomainAuthorizationService} (wired to a
 * real {@link AuthorizationDeniedAuditer} + {@link AuditService}). Asserts: the correct {@code
 * 403}/{@code 404}, <strong>exactly one</strong> {@code audit_event} per denial, no existence leak,
 * and — the rule #7 / §15 pin — <strong>no resource text (SENTINEL) ever lands in any audit
 * column</strong>. Authorized accesses write no audit.
 *
 * <p>(The {@code @Transactional(REQUIRES_NEW)} survives-rollback semantics are proven separately in
 * {@link AuthorizationDeniedAuditerTest} with a real Spring proxy; here the auditer is constructed
 * directly, so its writes join the test transaction and are counted via {@code TestEntityManager}.)
 */
class AuthorizationIdorMatrixTest extends AbstractJpaIntegrationTest {

  /** A recognizable resource-text marker that must NEVER appear in any audit column (rule #7). */
  private static final String SENTINEL = "SENTINEL|secret-note|<pii>alice@x</pii>";

  private static final LocalDate WEEK = LocalDate.parse("2026-09-07");
  private static final Instant TS = Instant.parse("2026-06-02T12:00:00Z");

  @Autowired private EmployeeRepository employees;
  @Autowired private ManagerRelationshipRepository relationships;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private WeeklyCommitmentRepository commitments;
  @Autowired private AlignmentDisputeRepository disputes;
  @Autowired private CommentRepository comments;
  @Autowired private ManagerReviewRepository reviews;
  @Autowired private ManagerHeatmapCellRepository heatmapCells;
  @Autowired private OutlookCalendarSyncRecordRepository syncRecords;
  @Autowired private RallyCryRepository rallyCries;
  @Autowired private DefiningObjectiveRepository definingObjectives;
  @Autowired private AuditEventRepository auditEvents;
  @Autowired private TestEntityManager em;

  private DomainAuthorizationService authz() {
    AuditService auditService = new AuditService(auditEvents, Clock.fixed(TS, ZoneOffset.UTC));
    AuthorizationDeniedAuditer auditer = new AuthorizationDeniedAuditer(auditService);
    return new DomainAuthorizationService(
        plans,
        commitments,
        disputes,
        comments,
        reviews,
        heatmapCells,
        syncRecords,
        relationships,
        auditer);
  }

  @Test
  void idorMatrix_eachDenialMapsCorrectly_oneSafeAuditEach_noSentinelLeak() {
    DomainAuthorizationService authz = authz();

    // ---- seed: two ICs + two managers; mgr actively manages ic1 (NOT ic2) ----
    Employee ic1 = saveEmployee(RoleType.IC);
    Employee ic2 = saveEmployee(RoleType.IC);
    Employee mgr = saveEmployee(RoleType.MANAGER);
    Employee otherMgr = saveEmployee(RoleType.MANAGER);
    relationships.saveAndFlush(relationship(mgr.getId(), ic1.getId(), true));

    // ic2's resources (the cross-owner targets) — text fields carry the SENTINEL
    WeeklyPlan ic2Plan = plans.saveAndFlush(plan(ic2.getId()));
    WeeklyCommitment ic2Commitment =
        commitments.saveAndFlush(commitment(ic2Plan.getId(), SENTINEL));
    OutlookCalendarSyncRecord ic2Sync = syncRecords.saveAndFlush(syncRecord(ic2.getId(), SENTINEL));

    // ic1's own dispute (IC can SEE it but not resolve it) — managerNote carries the SENTINEL
    WeeklyPlan ic1Plan = plans.saveAndFlush(plan(ic1.getId()));
    WeeklyCommitment ic1Commitment = commitments.saveAndFlush(commitment(ic1Plan.getId(), "ok"));
    AlignmentDispute ic1Dispute =
        disputes.saveAndFlush(dispute(ic1Commitment.getId(), mgr.getId(), SENTINEL));

    // otherMgr's heatmap cell (the not-own drill-down target)
    RallyCry rc = rallyCries.saveAndFlush(rallyCry());
    DefiningObjective dobj = definingObjectives.saveAndFlush(definingObjective(rc.getId()));
    ManagerHeatmapCell otherCell =
        heatmapCells.saveAndFlush(heatmapCell(otherMgr.getId(), ic2.getId(), dobj.getId()));

    UserPrincipal ic1P = new UserPrincipal(ic1.getId(), RoleType.IC, false);
    UserPrincipal mgrP = new UserPrincipal(mgr.getId(), RoleType.MANAGER, true);
    em.flush();
    assertThat(auditEvents.count()).isZero();

    // ---- the §6 denial matrix ----
    // 1. IC -> another IC's plan -> 404
    assertThatThrownBy(() -> authz.authorizePlanAccess(ic1P, ic2Plan.getId()))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    // 1b. IC -> another IC's commitment -> 404 (proves the SENTINEL title never leaks)
    assertThatThrownBy(() -> authz.authorizeCommitmentAccess(ic1P, ic2Commitment.getId()))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    // 2. manager -> a non-direct-report's plan -> 404
    assertThatThrownBy(() -> authz.authorizePlanAccess(mgrP, ic2Plan.getId()))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    // 3. manager -> a heatmap cell that is not their own -> 404
    assertThatThrownBy(() -> authz.authorizeHeatmapCellAccess(mgrP, otherCell.getId()))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    // 4. IC resolves a dispute on its OWN commitment -> 403 IC_CANNOT_RESOLVE_DISPUTE
    assertThatThrownBy(() -> authz.authorizeDisputeResolution(ic1P, ic1Dispute.getId()))
        .isInstanceOf(AuthorizationDeniedException.class)
        .hasFieldOrPropertyWithValue("code", "IC_CANNOT_RESOLVE_DISPUTE");
    // 5. IC accesses the team heatmap surface -> 403
    assertThatThrownBy(() -> authz.authorizeTeamHeatmapAccess(ic1P))
        .isInstanceOf(AuthorizationDeniedException.class)
        .hasFieldOrPropertyWithValue("code", "MANAGER_ROLE_REQUIRED");
    // 6. IC comments on an unauthorized target (ic2's plan) -> 404 (§11)
    assertThatThrownBy(
            () -> authz.authorizeCommentTargetAccess(ic1P, CommentTargetType.PLAN, ic2Plan.getId()))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    // 7. IC sync-retry on an unowned record -> 404
    assertThatThrownBy(() -> authz.authorizeSyncRecordAccess(ic1P, ic2Sync.getId()))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);

    // ---- exactly one audit per denial (7), authorized accesses add none ----
    assertThatCode(
            () -> {
              authz.authorizePlanAccess(ic1P, ic1Plan.getId()); // IC self
              authz.authorizePlanAccess(mgrP, ic1Plan.getId()); // manager of ic1
              authz.authorizeDisputeAccess(ic1P, ic1Dispute.getId()); // IC can SEE its dispute
            })
        .doesNotThrowAnyException();

    em.flush();
    List<AuditEvent> audits = auditEvents.findAll();
    assertThat(audits).hasSize(8);

    // ---- rule #7 / §15: NO resource text (SENTINEL) in ANY audit column ----
    for (AuditEvent a : audits) {
      assertThat(nullToEmpty(a.getAction())).doesNotContain(SENTINEL);
      assertThat(nullToEmpty(a.getEntityType())).doesNotContain(SENTINEL);
      assertThat(nullToEmpty(a.getSummary())).doesNotContain(SENTINEL);
      assertThat(nullToEmpty(a.getMetadataJson())).doesNotContain(SENTINEL);
      assertThat(a.getAction()).isEqualTo("AUTHORIZATION_DENIED");
    }
    // actor on the IC-initiated denials is ic1 (a real authenticated principal, never leaked text)
    assertThat(audits)
        .filteredOn(a -> "Plan".equals(a.getEntityType()))
        .allSatisfy(a -> assertThat(a.getActorEmployeeId()).isIn(ic1.getId(), mgr.getId()));
  }

  // ===== fixtures =====

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private Employee saveEmployee(RoleType role) {
    Employee e = new Employee();
    e.setId(UUID.randomUUID());
    e.setEmail(UUID.randomUUID() + "@x.test");
    e.setDisplayName("N");
    e.setRole(role);
    e.setActive(true);
    return employees.saveAndFlush(e);
  }

  private ManagerRelationship relationship(UUID managerId, UUID reportId, boolean active) {
    ManagerRelationship r = new ManagerRelationship();
    r.setId(UUID.randomUUID());
    r.setManagerEmployeeId(managerId);
    r.setDirectReportEmployeeId(reportId);
    r.setActive(active);
    return r;
  }

  private WeeklyPlan plan(UUID ownerIc) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(ownerIc);
    p.setWeekStartDate(WEEK);
    p.setWeekEndDate(WEEK.plusDays(6));
    p.setState(PlanState.DRAFT);
    return p;
  }

  private WeeklyCommitment commitment(UUID planId, String title) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(planId);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle(title);
    c.setPriority(Priority.P1);
    c.setWorkType(WorkType.STRATEGIC);
    c.setConfidence(Confidence.HIGH);
    c.setAlignmentStatus(AlignmentStatus.ALIGNED);
    return c;
  }

  private AlignmentDispute dispute(UUID commitmentId, UUID managerId, String managerNote) {
    AlignmentDispute d = new AlignmentDispute();
    d.setId(UUID.randomUUID());
    d.setCommitmentId(commitmentId);
    d.setManagerEmployeeId(managerId);
    d.setStatus(DisputeStatus.OPEN);
    d.setFlagType(FlagType.NEEDS_REVISION);
    d.setManagerNote(managerNote);
    return d;
  }

  @SuppressWarnings("unused")
  private ManagerReview review(UUID planId, UUID managerId) {
    ManagerReview r = new ManagerReview();
    r.setId(UUID.randomUUID());
    r.setWeeklyPlanId(planId);
    r.setManagerEmployeeId(managerId);
    r.setStatus(ReviewStatus.NOT_REVIEWED);
    r.setReviewDueAt(TS);
    r.setSummaryNote(SENTINEL);
    return r;
  }

  private RallyCry rallyCry() {
    RallyCry rc = new RallyCry();
    rc.setId(UUID.randomUUID());
    rc.setTitle("RC");
    rc.setActive(true);
    return rc;
  }

  private DefiningObjective definingObjective(UUID rallyCryId) {
    DefiningObjective d = new DefiningObjective();
    d.setId(UUID.randomUUID());
    d.setRallyCryId(rallyCryId);
    d.setTitle("DO");
    d.setActive(true);
    return d;
  }

  private ManagerHeatmapCell heatmapCell(
      UUID managerId, UUID employeeId, UUID definingObjectiveId) {
    ManagerHeatmapCell cell = new ManagerHeatmapCell();
    cell.setId(UUID.randomUUID());
    cell.setManagerEmployeeId(managerId);
    cell.setEmployeeId(employeeId);
    cell.setWeekStartDate(WEEK);
    cell.setDefiningObjectiveId(definingObjectiveId);
    cell.setUpdatedAt(TS);
    return cell;
  }

  private OutlookCalendarSyncRecord syncRecord(UUID ownerId, String safeMessage) {
    OutlookCalendarSyncRecord s = new OutlookCalendarSyncRecord();
    s.setId(UUID.randomUUID());
    s.setOwnerEmployeeId(ownerId);
    s.setRelatedType(SyncRelatedType.MANAGER_REVIEW_WEEK);
    s.setRelatedId(UUID.randomUUID());
    s.setEventKind(EventKind.MANAGER_REVIEW_BLOCK);
    s.setStatus(SyncStatus.PENDING_PUBLISH);
    s.setWeekStartDate(WEEK);
    s.setSafeMessage(safeMessage);
    return s;
  }
}
