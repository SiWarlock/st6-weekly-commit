package com.st6.wc.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.st6.wc.audit.AuditEvent;
import com.st6.wc.audit.repo.AuditEventRepository;
import com.st6.wc.comment.Comment;
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
import com.st6.wc.enums.RiskBadge;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.SyncRelatedType;
import com.st6.wc.enums.SyncStatus;
import com.st6.wc.enums.WorkType;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.ManagerHeatmapCell;
import com.st6.wc.projection.ManagerPlanSummary;
import com.st6.wc.projection.repo.ManagerHeatmapCellRepository;
import com.st6.wc.projection.repo.ManagerPlanSummaryRepository;
import com.st6.wc.rcdo.DefiningObjective;
import com.st6.wc.rcdo.RallyCry;
import com.st6.wc.rcdo.SupportingOutcome;
import com.st6.wc.rcdo.repo.DefiningObjectiveRepository;
import com.st6.wc.rcdo.repo.RallyCryRepository;
import com.st6.wc.rcdo.repo.SupportingOutcomeRepository;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.ManagerReview;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.support.AbstractJpaIntegrationTest;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * Entity↔DDL fidelity + round-trip proof for the 14 Appendix-A JPA entities (task 1.5). The class
 * boots under {@code ddl-auto=validate} (base harness) — context start alone proves every mapping
 * matches its Flyway-migrated column. The methods then prove persistence round-trips, enum→string
 * storage, the four contract deltas, the {@code text[]}→{@code List<RiskBadge>} mapping, the
 * carry-forward self-link, and {@code @Version} presence on the 5 mutable lifecycle entities only.
 */
class JpaEntityMappingTest extends AbstractJpaIntegrationTest {

  @Autowired private TestEntityManager em;
  @Autowired private EmployeeRepository employees;
  @Autowired private ManagerRelationshipRepository relationships;
  @Autowired private RallyCryRepository rallyCries;
  @Autowired private DefiningObjectiveRepository definingObjectives;
  @Autowired private SupportingOutcomeRepository supportingOutcomes;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private WeeklyCommitmentRepository commitments;
  @Autowired private ManagerReviewRepository reviews;
  @Autowired private AlignmentDisputeRepository disputes;
  @Autowired private CommentRepository comments;
  @Autowired private OutlookCalendarSyncRecordRepository syncRecords;
  @Autowired private AuditEventRepository auditEvents;
  @Autowired private ManagerPlanSummaryRepository planSummaries;
  @Autowired private ManagerHeatmapCellRepository heatmapCells;

  private static final LocalDate WEEK = LocalDate.parse("2026-09-07");
  private static final Instant TS = Instant.parse("2026-06-02T12:00:00Z");

  // ---- Test 1: schema validates (the strongest fidelity proof) --------------
  @Test
  void schema_validates_against_flyway_migrations() {
    // Reaching this method means the context booted with ddl-auto=validate after Flyway V1-V3,
    // i.e. every one of the 14 entity mappings matched its migrated column (no
    // SchemaManagementException).
    assertThat(em).isNotNull();
    assertThat(employees.count()).isZero();
  }

  // ---- Test 2: every entity persists + reloads via its repository -----------
  @Test
  void each_entity_persists_and_reloads() {
    Employee mgr = saveEmployee(RoleType.MANAGER);
    Employee ic = saveEmployee(RoleType.IC);
    assertThat(employees.findById(ic.getId()))
        .get()
        .extracting(Employee::getRole)
        .isEqualTo(RoleType.IC);

    ManagerRelationship rel = newRelationship(mgr.getId(), ic.getId());
    relationships.save(rel);
    assertThat(relationships.findById(rel.getId())).isPresent();

    RallyCry rc = newRallyCry();
    rallyCries.save(rc);
    DefiningObjective dobj = newDefiningObjective(rc.getId());
    definingObjectives.save(dobj);
    SupportingOutcome so = newSupportingOutcome(dobj.getId());
    supportingOutcomes.save(so);
    assertThat(supportingOutcomes.findById(so.getId())).isPresent();

    WeeklyPlan plan = newPlan(ic.getId(), PlanState.DRAFT);
    plans.save(plan);
    assertThat(plans.findById(plan.getId()))
        .get()
        .extracting(WeeklyPlan::getState)
        .isEqualTo(PlanState.DRAFT);

    WeeklyCommitment c = newCommitment(plan.getId(), so.getId());
    commitments.save(c);
    flushAndClear();
    assertThat(commitments.findById(c.getId()))
        .get()
        .satisfies(
            x -> {
              assertThat(x.getWorkType()).isEqualTo(WorkType.STRATEGIC);
              assertThat(x.getCommitmentKind()).isEqualTo(CommitmentKind.PLANNED);
              assertThat(x.getConfidence()).isEqualTo(Confidence.HIGH);
              assertThat(x.getAlignmentStatus()).isEqualTo(AlignmentStatus.ALIGNED);
              assertThat(x.getSupportingOutcomeId()).isEqualTo(so.getId());
            });

    ManagerReview review = newReview(plan.getId(), mgr.getId());
    reviews.save(review);
    assertThat(reviews.findById(review.getId()))
        .get()
        .extracting(ManagerReview::getStatus)
        .isEqualTo(ReviewStatus.NOT_REVIEWED);

    AlignmentDispute dispute = newDispute(c.getId(), mgr.getId());
    disputes.save(dispute);
    assertThat(disputes.findById(dispute.getId()))
        .get()
        .extracting(AlignmentDispute::getFlagType)
        .isEqualTo(FlagType.NEEDS_REVISION);

    Comment comment = newComment(plan.getId(), ic.getId());
    comments.save(comment);
    assertThat(comments.findById(comment.getId()))
        .get()
        .extracting(Comment::getTargetType)
        .isEqualTo(CommentTargetType.PLAN);

    OutlookCalendarSyncRecord sync = newSync(ic.getId(), plan.getId());
    syncRecords.save(sync);
    assertThat(syncRecords.findById(sync.getId()))
        .get()
        .satisfies(
            x -> {
              assertThat(x.getRelatedType()).isEqualTo(SyncRelatedType.WEEKLY_PLAN);
              assertThat(x.getEventKind()).isEqualTo(EventKind.IC_PLANNING);
              assertThat(x.getStatus()).isEqualTo(SyncStatus.PENDING_PUBLISH);
            });

    AuditEvent audit = newAudit(ic.getId());
    auditEvents.save(audit);
    assertThat(auditEvents.findById(audit.getId())).isPresent();

    ManagerPlanSummary summary = newSummary(mgr.getId(), ic.getId(), plan.getId());
    planSummaries.save(summary);
    assertThat(planSummaries.findById(summary.getId()))
        .get()
        .extracting(ManagerPlanSummary::getPlanState)
        .isEqualTo(PlanState.DRAFT);

    ManagerHeatmapCell cell = newHeatmapCell(mgr.getId(), ic.getId(), dobj.getId());
    heatmapCells.save(cell);
    assertThat(heatmapCells.findById(cell.getId())).isPresent();
  }

  // ---- Test 3: @Enumerated(STRING) stores the constant NAME, not ordinal ----
  @Test
  void enum_columns_store_string_values() {
    Employee ic = saveEmployee(RoleType.IC);
    WeeklyPlan plan = newPlan(ic.getId(), PlanState.LOCKED);
    plans.save(plan);
    SupportingOutcome so = persistRcdoChain();
    WeeklyCommitment c = newCommitment(plan.getId(), so.getId());
    c.setWorkType(WorkType.MAINTENANCE);
    commitments.save(c);
    flushAndClear();

    EntityManager delegate = em.getEntityManager();
    Object state =
        delegate
            .createNativeQuery("select state from weekly_plan where id = :id")
            .setParameter("id", plan.getId())
            .getSingleResult();
    Object workType =
        delegate
            .createNativeQuery("select work_type from weekly_commitment where id = :id")
            .setParameter("id", c.getId())
            .getSingleResult();
    assertThat(state).isEqualTo("LOCKED");
    assertThat(workType).isEqualTo("MAINTENANCE");
  }

  // ---- Test 4: nullable/optional fields persist (the contract deltas) -------
  @Test
  void nullable_optional_fields_persist() {
    Employee ic = saveEmployee(RoleType.IC);
    WeeklyPlan plan = newPlan(ic.getId(), PlanState.DRAFT);
    plans.save(plan);

    // WeeklyCommitment with NULL managerAlignmentNote + NULL supportingOutcomeId
    WeeklyCommitment c = newCommitment(plan.getId(), null);
    c.setManagerAlignmentNote(null);
    commitments.save(c);
    flushAndClear();
    assertThat(commitments.findById(c.getId()))
        .get()
        .satisfies(
            x -> {
              assertThat(x.getSupportingOutcomeId()).isNull();
              assertThat(x.getManagerAlignmentNote()).isNull();
            });

    // Comment flat default: parent NULL, depth 0
    Comment comment = newComment(plan.getId(), ic.getId());
    comments.save(comment);
    flushAndClear();
    assertThat(comments.findById(comment.getId()))
        .get()
        .satisfies(
            x -> {
              assertThat(x.getParentCommentId()).isNull();
              assertThat(x.getDepth()).isZero();
            });

    // AuditEvent with NULL actor (SYSTEM)
    AuditEvent audit = newAudit(null);
    auditEvents.save(audit);
    flushAndClear();
    assertThat(auditEvents.findById(audit.getId()))
        .get()
        .extracting(AuditEvent::getActorEmployeeId)
        .isNull();
  }

  // ---- Test 5: risk_badges text[] <-> List<RiskBadge> round-trips -----------
  @Test
  void risk_badges_multi_element_round_trips() {
    Employee mgr = saveEmployee(RoleType.MANAGER);
    Employee ic = saveEmployee(RoleType.IC);
    DefiningObjective dobj = persistDefiningObjective();

    ManagerHeatmapCell multi = newHeatmapCell(mgr.getId(), ic.getId(), dobj.getId());
    multi.setRiskBadges(List.of(RiskBadge.MISALIGNED, RiskBadge.BLOCKED));
    heatmapCells.save(multi);

    Employee ic2 = saveEmployee(RoleType.IC);
    ManagerHeatmapCell empty = newHeatmapCell(mgr.getId(), ic2.getId(), dobj.getId());
    heatmapCells.save(empty);
    flushAndClear();

    assertThat(heatmapCells.findById(multi.getId()))
        .get()
        .extracting(ManagerHeatmapCell::getRiskBadges)
        .satisfies(b -> assertThat(b).containsExactly(RiskBadge.MISALIGNED, RiskBadge.BLOCKED));
    assertThat(heatmapCells.findById(empty.getId()))
        .get()
        .extracting(ManagerHeatmapCell::getRiskBadges)
        .satisfies(b -> assertThat(b).isEmpty());
  }

  // ---- Test 5b: metadata_json jsonb round-trips (Hibernate-native type) -----
  @Test
  void metadata_json_jsonb_round_trips() throws Exception {
    AuditEvent audit = newAudit(null);
    audit.setMetadataJson("{\"traceId\":\"abc\"}");
    auditEvents.save(audit);
    flushAndClear();

    AuditEvent reloaded = auditEvents.findById(audit.getId()).orElseThrow();
    assertThat(reloaded.getMetadataJson()).isNotNull();
    // Parse-and-compare, not raw string equality: jsonb normalizes whitespace/key-order on store.
    JsonNode node = new ObjectMapper().readTree(reloaded.getMetadataJson());
    assertThat(node.get("traceId").asText()).isEqualTo("abc");

    // The ->> operator is jsonb/json-only — doubly proves the column is genuinely jsonb, not text.
    Object extracted =
        em.getEntityManager()
            .createNativeQuery("select metadata_json->>'traceId' from audit_event where id = :id")
            .setParameter("id", audit.getId())
            .getSingleResult();
    assertThat(extracted).isEqualTo("abc");
  }

  // ---- Test 6: carry_forward self-link persists + loads ---------------------
  @Test
  void carry_forward_self_link_loads() {
    Employee ic = saveEmployee(RoleType.IC);
    WeeklyPlan plan = newPlan(ic.getId(), PlanState.RECONCILING);
    plans.save(plan);

    WeeklyCommitment a = newCommitment(plan.getId(), null);
    commitments.save(a);
    WeeklyCommitment b = newCommitment(plan.getId(), null);
    b.setCarryForwardSourceCommitmentId(a.getId());
    commitments.save(b);
    flushAndClear();

    assertThat(commitments.findById(b.getId()))
        .get()
        .extracting(WeeklyCommitment::getCarryForwardSourceCommitmentId)
        .isEqualTo(a.getId());
  }

  // ---- Test 7: @Version present on the 5 mutable lifecycle entities only ----
  @Test
  void version_present_on_mutable_only() {
    Employee ic = saveEmployee(RoleType.IC);
    Employee mgr = saveEmployee(RoleType.MANAGER);
    WeeklyPlan plan = newPlan(ic.getId(), PlanState.DRAFT);
    plans.save(plan);
    WeeklyCommitment c = newCommitment(plan.getId(), null);
    commitments.save(c);
    ManagerReview review = newReview(plan.getId(), mgr.getId());
    reviews.save(review);
    AlignmentDispute dispute = newDispute(c.getId(), mgr.getId());
    disputes.save(dispute);
    OutlookCalendarSyncRecord sync = newSync(ic.getId(), plan.getId());
    syncRecords.save(sync);
    flushAndClear();

    assertThat(plans.findById(plan.getId())).get().extracting(WeeklyPlan::getVersion).isEqualTo(0L);
    assertThat(commitments.findById(c.getId()))
        .get()
        .extracting(WeeklyCommitment::getVersion)
        .isEqualTo(0L);
    assertThat(reviews.findById(review.getId()))
        .get()
        .extracting(ManagerReview::getVersion)
        .isEqualTo(0L);
    assertThat(disputes.findById(dispute.getId()))
        .get()
        .extracting(AlignmentDispute::getVersion)
        .isEqualTo(0L);
    assertThat(syncRecords.findById(sync.getId()))
        .get()
        .extracting(OutlookCalendarSyncRecord::getVersion)
        .isEqualTo(0L);

    // ...and ABSENT on the other 9 (structurally — shapes (b)/(c) do not extend
    // PersistableUuidEntity).
    for (Class<?> nonVersioned :
        new Class<?>[] {
          Employee.class,
          ManagerRelationship.class,
          RallyCry.class,
          DefiningObjective.class,
          SupportingOutcome.class,
          Comment.class,
          AuditEvent.class,
          ManagerPlanSummary.class,
          ManagerHeatmapCell.class
        }) {
      assertThat(hasField(nonVersioned, "version"))
          .as("%s must NOT carry a version field", nonVersioned.getSimpleName())
          .isFalse();
    }
  }

  // ---- fixtures -------------------------------------------------------------
  private void flushAndClear() {
    em.flush();
    em.clear();
  }

  private static boolean hasField(Class<?> type, String name) {
    for (Class<?> t = type; t != null && t != Object.class; t = t.getSuperclass()) {
      for (Field f : t.getDeclaredFields()) {
        if (f.getName().equals(name)) {
          return true;
        }
      }
    }
    return false;
  }

  private Employee saveEmployee(RoleType role) {
    Employee e = new Employee();
    e.setId(UUID.randomUUID());
    e.setEmail(UUID.randomUUID() + "@x.test");
    e.setDisplayName("N");
    e.setRole(role);
    e.setActive(true);
    return employees.save(e);
  }

  private ManagerRelationship newRelationship(UUID managerId, UUID reportId) {
    ManagerRelationship r = new ManagerRelationship();
    r.setId(UUID.randomUUID());
    r.setManagerEmployeeId(managerId);
    r.setDirectReportEmployeeId(reportId);
    r.setActive(true);
    return r;
  }

  private RallyCry newRallyCry() {
    RallyCry rc = new RallyCry();
    rc.setId(UUID.randomUUID());
    rc.setTitle("RC");
    rc.setActive(true);
    return rc;
  }

  private DefiningObjective newDefiningObjective(UUID rallyCryId) {
    DefiningObjective d = new DefiningObjective();
    d.setId(UUID.randomUUID());
    d.setRallyCryId(rallyCryId);
    d.setTitle("DO");
    d.setActive(true);
    return d;
  }

  private SupportingOutcome newSupportingOutcome(UUID definingObjectiveId) {
    SupportingOutcome s = new SupportingOutcome();
    s.setId(UUID.randomUUID());
    s.setDefiningObjectiveId(definingObjectiveId);
    s.setTitle("SO");
    s.setActive(true);
    return s;
  }

  private DefiningObjective persistDefiningObjective() {
    RallyCry rc = newRallyCry();
    rallyCries.save(rc);
    DefiningObjective d = newDefiningObjective(rc.getId());
    return definingObjectives.save(d);
  }

  private SupportingOutcome persistRcdoChain() {
    DefiningObjective d = persistDefiningObjective();
    SupportingOutcome s = newSupportingOutcome(d.getId());
    return supportingOutcomes.save(s);
  }

  private WeeklyPlan newPlan(UUID employeeId, PlanState state) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(employeeId);
    p.setWeekStartDate(WEEK);
    p.setWeekEndDate(WEEK.plusDays(6));
    p.setState(state);
    return p;
  }

  private WeeklyCommitment newCommitment(UUID planId, UUID supportingOutcomeId) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(planId);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("commit");
    c.setSupportingOutcomeId(supportingOutcomeId);
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

  private AlignmentDispute newDispute(UUID commitmentId, UUID managerId) {
    AlignmentDispute d = new AlignmentDispute();
    d.setId(UUID.randomUUID());
    d.setCommitmentId(commitmentId);
    d.setManagerEmployeeId(managerId);
    d.setStatus(DisputeStatus.OPEN);
    d.setFlagType(FlagType.NEEDS_REVISION);
    d.setManagerNote("note");
    return d;
  }

  private Comment newComment(UUID targetId, UUID authorId) {
    Comment c = new Comment();
    c.setId(UUID.randomUUID());
    c.setTargetType(CommentTargetType.PLAN);
    c.setTargetId(targetId);
    c.setAuthorEmployeeId(authorId);
    c.setBody("body");
    return c;
  }

  private OutlookCalendarSyncRecord newSync(UUID ownerId, UUID relatedId) {
    OutlookCalendarSyncRecord s = new OutlookCalendarSyncRecord();
    s.setId(UUID.randomUUID());
    s.setOwnerEmployeeId(ownerId);
    s.setRelatedType(SyncRelatedType.WEEKLY_PLAN);
    s.setRelatedId(relatedId);
    s.setEventKind(EventKind.IC_PLANNING);
    s.setStatus(SyncStatus.PENDING_PUBLISH);
    return s;
  }

  private AuditEvent newAudit(UUID actorId) {
    AuditEvent a = new AuditEvent();
    a.setId(UUID.randomUUID());
    a.setActorEmployeeId(actorId);
    a.setAction("PLAN_LOCKED");
    a.setEntityType("WeeklyPlan");
    a.setSummary("summary");
    a.setCreatedAt(TS);
    return a;
  }

  private ManagerPlanSummary newSummary(UUID managerId, UUID employeeId, UUID planId) {
    ManagerPlanSummary s = new ManagerPlanSummary();
    s.setId(UUID.randomUUID());
    s.setManagerEmployeeId(managerId);
    s.setEmployeeId(employeeId);
    s.setWeeklyPlanId(planId);
    s.setWeekStartDate(WEEK);
    s.setPlanState(PlanState.DRAFT);
    s.setUpdatedAt(TS);
    return s;
  }

  private ManagerHeatmapCell newHeatmapCell(
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
}
