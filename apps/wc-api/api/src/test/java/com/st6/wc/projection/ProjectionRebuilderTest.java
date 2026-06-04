package com.st6.wc.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.st6.wc.audit.AuditEvent;
import com.st6.wc.audit.repo.AuditEventRepository;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.WorkType;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.repo.ManagerHeatmapCellRepository;
import com.st6.wc.projection.repo.ManagerPlanSummaryRepository;
import com.st6.wc.rcdo.repo.DefiningObjectiveRepository;
import com.st6.wc.rcdo.repo.SupportingOutcomeRepository;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.ManagerReview;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.support.AbstractAppBootTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The §9 projection-rebuild proof (task 6.7, REQ-D-013) against real PG16 + the V4 RCDO seed. The
 * {@link ProjectionRebuilder} truncates + recomputes both projection tables from source via the
 * SAME {@link ProjectionRefresher#recomputeForPlan} the 9 synchronous triggers use — so {@code
 * rebuild == incremental}. The core proof corrupts/drifts a correctly-built projection (mutate a
 * count, delete a cell, inject an orphan) then asserts {@code rebuild()} reconstructs the
 * correct-from-source state. Plus idempotency, the §28 unmanaged/unreviewed skip, and one SYSTEM
 * null-actor audit per run. ({@code rebuild==seed} R1–R6 is deferred to the HELD V5/V6 demo seed.)
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
class ProjectionRebuilderTest extends AbstractAppBootTest {

  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);
  private static final Instant TS = Instant.parse("2026-06-03T12:00:00Z");

  @Autowired private ProjectionRebuilder rebuilder;
  @Autowired private ProjectionRefresher refresher;
  @Autowired private EmployeeRepository employees;
  @Autowired private ManagerRelationshipRepository relationships;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private WeeklyCommitmentRepository commitments;
  @Autowired private ManagerReviewRepository reviews;
  @Autowired private ManagerPlanSummaryRepository summaries;
  @Autowired private ManagerHeatmapCellRepository heatmapCells;
  @Autowired private AuditEventRepository auditEvents;
  @Autowired private SupportingOutcomeRepository supportingOutcomes;
  @Autowired private DefiningObjectiveRepository definingObjectives;

  @AfterEach
  void cleanup() {
    auditEvents.deleteAll();
    heatmapCells.deleteAll();
    summaries.deleteAll();
    commitments.deleteAll();
    reviews.deleteAll();
    plans.deleteAll();
    relationships.deleteAll();
    employees.deleteAll();
  }

  // --- the core proof: rebuild reconstructs from source = the incremental result (drift corrected)
  // ---
  @Test
  void rebuild_reconstructsProjectionFromSource_equalsIncremental() {
    Employee mgr = saveEmployee(RoleType.MANAGER);
    Employee ic = saveEmployee(RoleType.IC);
    saveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId());
    UUID so = soUnderDo1();
    saveCommitment(plan.getId(), so, Priority.P0, AlignmentStatus.MISALIGNED);
    saveCommitment(plan.getId(), so, Priority.P1, AlignmentStatus.MISALIGNED);
    saveReview(plan.getId(), mgr.getId());

    refresher.recomputeForPlan(plan); // the incremental truth
    int expectedPlanned = summaries.findAll().get(0).getPlannedCount();
    int expectedMisaligned = summaries.findAll().get(0).getMisalignedCount();
    assertThat(heatmapCells.findAll()).hasSize(1);

    // corrupt: a wrong summary count + a deleted cell (drift the read-model away from source)
    ManagerPlanSummary corrupt = summaries.findAll().get(0);
    corrupt.setMisalignedCount(999);
    summaries.saveAndFlush(corrupt);
    heatmapCells.deleteAll();

    int processed = rebuilder.rebuild();

    assertThat(processed).isEqualTo(1); // the one seeded plan
    ManagerPlanSummary rebuilt = summaries.findAll().get(0);
    assertThat(rebuilt.getMisalignedCount()).isEqualTo(expectedMisaligned); // corrected from 999
    assertThat(rebuilt.getPlannedCount()).isEqualTo(expectedPlanned);
    assertThat(heatmapCells.findAll()).hasSize(1); // the deleted cell is back
  }

  // --- a cell the source no longer produces is pruned (truncate + recompute) ---
  @Test
  void rebuild_prunesOrphanCells() {
    Employee mgr = saveEmployee(RoleType.MANAGER);
    Employee ic = saveEmployee(RoleType.IC);
    saveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId());
    saveCommitment(plan.getId(), soUnderDo1(), Priority.P0, AlignmentStatus.MISALIGNED);
    saveReview(plan.getId(), mgr.getId());
    refresher.recomputeForPlan(plan);
    assertThat(heatmapCells.findAll()).hasSize(1); // the DO-1 cell

    // inject an orphan cell for DO-2 (the source has no commitment under DO-2)
    UUID do2 = definingObjectives.findAllByOrderByIdAsc().get(1).getId();
    saveOrphanCell(mgr.getId(), ic.getId(), do2);
    assertThat(heatmapCells.findAll()).hasSize(2);

    rebuilder.rebuild();

    List<ManagerHeatmapCell> cells = heatmapCells.findAll();
    assertThat(cells).hasSize(1);
    assertThat(cells.get(0).getDefiningObjectiveId()).isEqualTo(do1());
  }

  // --- truncate + recompute is naturally idempotent: 2x → identical row set, no duplicates ---
  @Test
  void rebuild_isIdempotent() {
    Employee mgr = saveEmployee(RoleType.MANAGER);
    Employee ic = saveEmployee(RoleType.IC);
    saveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId());
    saveCommitment(plan.getId(), soUnderDo1(), Priority.P0, AlignmentStatus.MISALIGNED);
    saveReview(plan.getId(), mgr.getId());

    rebuilder.rebuild();
    long summariesAfterFirst = summaries.count();
    long cellsAfterFirst = heatmapCells.count();
    rebuilder.rebuild();

    assertThat(summaries.count()).isEqualTo(summariesAfterFirst).isEqualTo(1);
    assertThat(heatmapCells.count()).isEqualTo(cellsAfterFirst).isEqualTo(1);
  }

  // --- §28: a plan with no active manager (and no review) projects nothing ---
  @Test
  void rebuild_skipsUnmanagedAndUnreviewedPlans() {
    Employee ic = saveEmployee(RoleType.IC); // no manager relationship, no review
    WeeklyPlan plan = savePlan(ic.getId());
    saveCommitment(plan.getId(), soUnderDo1(), Priority.P0, AlignmentStatus.MISALIGNED);

    rebuilder.rebuild();

    assertThat(summaries.count()).isZero();
    assertThat(heatmapCells.count()).isZero();
  }

  // --- §23: one SYSTEM null-actor audit row per run, even with 0 plans ---
  @Test
  void rebuild_writesOneSystemAuditRow() {
    rebuilder.rebuild(); // no plans seeded

    List<AuditEvent> audits = auditEvents.findAll();
    assertThat(audits).hasSize(1);
    AuditEvent a = audits.get(0);
    assertThat(a.getAction()).isEqualTo("PROJECTIONS_REBUILT");
    assertThat(a.getActorEmployeeId()).isNull(); // SYSTEM
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

  private void saveRelationship(UUID managerId, UUID reportId) {
    ManagerRelationship r = new ManagerRelationship();
    r.setId(UUID.randomUUID());
    r.setManagerEmployeeId(managerId);
    r.setDirectReportEmployeeId(reportId);
    r.setActive(true);
    relationships.saveAndFlush(r);
  }

  private WeeklyPlan savePlan(UUID ownerId) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(ownerId);
    p.setWeekStartDate(WEEK);
    p.setWeekEndDate(WEEK.plusDays(6));
    p.setState(PlanState.LOCKED);
    return plans.saveAndFlush(p);
  }

  private void saveCommitment(
      UUID planId, UUID supportingOutcomeId, Priority priority, AlignmentStatus alignmentStatus) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(planId);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("commitment");
    c.setSupportingOutcomeId(supportingOutcomeId);
    c.setPriority(priority);
    c.setWorkType(WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(alignmentStatus);
    commitments.saveAndFlush(c);
  }

  private void saveReview(UUID planId, UUID managerId) {
    ManagerReview r = new ManagerReview();
    r.setId(UUID.randomUUID());
    r.setWeeklyPlanId(planId);
    r.setManagerEmployeeId(managerId);
    r.setStatus(ReviewStatus.NOT_REVIEWED);
    r.setReviewDueAt(TS);
    reviews.saveAndFlush(r);
  }

  private void saveOrphanCell(UUID managerId, UUID reportId, UUID definingObjectiveId) {
    ManagerHeatmapCell h = new ManagerHeatmapCell();
    h.setId(UUID.randomUUID());
    h.setManagerEmployeeId(managerId);
    h.setEmployeeId(reportId);
    h.setWeekStartDate(WEEK);
    h.setDefiningObjectiveId(definingObjectiveId);
    h.setUpdatedAt(TS);
    heatmapCells.saveAndFlush(h);
  }

  private UUID do1() {
    return definingObjectives.findAllByOrderByIdAsc().get(0).getId();
  }

  private UUID soUnderDo1() {
    return supportingOutcomes.findByDefiningObjectiveIdOrderByIdAsc(do1()).get(0).getId();
  }
}
