package com.st6.wc.manager;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.st6.wc.projection.ManagerHeatmapCell;
import com.st6.wc.projection.ManagerPlanSummary;
import com.st6.wc.projection.repo.ManagerHeatmapCellRepository;
import com.st6.wc.projection.repo.ManagerPlanSummaryRepository;
import com.st6.wc.rcdo.DefiningObjective;
import com.st6.wc.rcdo.repo.DefiningObjectiveRepository;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.support.AbstractAppBootTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/manager/command-center} (E13, task 6.5a) end-to-end through the demo-mode 2.6
 * chain against real PG16 + the V4 RCDO seed ({@link AbstractAppBootTest}). Proves the B.20
 * envelope of B.11 {@code ManagerCommandCenterRowDto} rows, <strong>direct-report
 * query-scoping</strong> (a manager sees ONLY their own reports' rows — another manager's are
 * unreachable, not just hidden, §6/IDOR), the F.5 default sort + pagination clamp, the REQ-F-023
 * summary-level filters (employeeId/planState/reviewState incl. derived {@code OVERDUE}), {@code
 * weekStart}-required (→400), the coarse manager-only gate (IC →403 {@code MANAGER_ROLE_REQUIRED}),
 * and the record (not entity) leak guard. The cross-table filters
 * (DO/priority/workType/alignmentStatus) are 6.5a-2; the full IDOR matrix is 6.6.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class ManagerCommandCenterEndpointTest extends AbstractAppBootTest {

  private static final String HEADER = "X-Demo-Employee-Id";
  private static final String URL = "/api/manager/command-center";
  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);

  @Autowired private MockMvc mvc;
  @Autowired private EmployeeRepository employees;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private ManagerRelationshipRepository relationships;
  @Autowired private ManagerPlanSummaryRepository summaries;
  @Autowired private AuditEventRepository auditEvents;
  @Autowired private WeeklyCommitmentRepository commitments;
  @Autowired private ManagerHeatmapCellRepository heatmapCells;
  @Autowired private DefiningObjectiveRepository definingObjectives;

  @AfterEach
  void cleanup() {
    auditEvents
        .deleteAll(); // the IC-denial test writes an authorization-denial audit (FK→employee)
    commitments.deleteAll(); // FK → weekly_plan: delete before plans (§38 teardown FK-order)
    summaries.deleteAll();
    heatmapCells.deleteAll(); // FK → employee + defining_objective: delete before employees
    plans.deleteAll();
    relationships.deleteAll();
    employees.deleteAll();
  }

  private Employee saveEmployee(String displayName, RoleType role) {
    Employee e = new Employee();
    e.setId(UUID.randomUUID());
    e.setEmail(displayName.toLowerCase() + "@x.test");
    e.setDisplayName(displayName);
    e.setRole(role);
    e.setActive(true);
    return employees.saveAndFlush(e);
  }

  private void saveActiveRelationship(UUID managerId, UUID reportId) {
    ManagerRelationship r = new ManagerRelationship();
    r.setId(UUID.randomUUID());
    r.setManagerEmployeeId(managerId);
    r.setDirectReportEmployeeId(reportId);
    r.setActive(true);
    relationships.saveAndFlush(r);
  }

  private UUID savePlan(UUID ownerId, PlanState state) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(ownerId);
    p.setWeekStartDate(WEEK);
    p.setWeekEndDate(WEEK.plusDays(6));
    p.setState(state);
    return plans.saveAndFlush(p).getId();
  }

  /**
   * Seed a manager_plan_summary for (manager, report) with the fields the filters/sort assert.
   * Returns the backing weekly_plan id so cross-table commitment fixtures can attach to it.
   */
  private UUID saveSummary(
      UUID managerId,
      UUID reportId,
      PlanState planState,
      ReviewStatus reviewStatus,
      boolean overdue) {
    UUID planId = savePlan(reportId, planState);
    ManagerPlanSummary s = new ManagerPlanSummary();
    s.setId(UUID.randomUUID());
    s.setManagerEmployeeId(managerId);
    s.setEmployeeId(reportId);
    s.setWeeklyPlanId(planId);
    s.setWeekStartDate(WEEK);
    s.setPlanState(planState);
    s.setReviewStatus(reviewStatus);
    s.setReviewDueAt(Instant.parse("2026-06-04T22:00:00Z"));
    s.setReviewOverdue(overdue);
    s.setPlannedCount(3);
    s.setUnplannedCount(1);
    s.setMisalignedCount(1);
    s.setNeedsReviewCount(1);
    s.setBlockedCount(0);
    s.setCarryForwardCount(0);
    s.setUnresolvedDisputeCount(0);
    s.setUpdatedAt(Instant.parse("2026-06-03T12:00:00Z"));
    summaries.saveAndFlush(s);
    return planId;
  }

  /** Seed a commitment on a plan — the source for the priority/workType/alignmentStatus EXISTS. */
  private void saveCommitment(
      UUID planId, Priority priority, WorkType workType, AlignmentStatus alignmentStatus) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(planId);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("commitment");
    c.setPriority(priority);
    c.setWorkType(workType);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(alignmentStatus);
    commitments.saveAndFlush(c);
  }

  /** Seed a heatmap cell at the full grain — the source for the definingObjectiveId EXISTS. */
  private void saveHeatmapCell(UUID managerId, UUID reportId, UUID definingObjectiveId) {
    ManagerHeatmapCell h = new ManagerHeatmapCell();
    h.setId(UUID.randomUUID());
    h.setManagerEmployeeId(managerId);
    h.setEmployeeId(reportId);
    h.setWeekStartDate(WEEK);
    h.setDefiningObjectiveId(definingObjectiveId);
    h.setUpdatedAt(Instant.parse("2026-06-03T12:00:00Z"));
    heatmapCells.saveAndFlush(h);
  }

  // --- scoping + B.20 envelope: a manager sees ONLY their own reports' rows for the week ----
  @Test
  void commandCenter_returnsOnlyManagersReports_inEnvelope() throws Exception {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    Employee r1 = saveEmployee("Alice", RoleType.IC);
    Employee r2 = saveEmployee("Bob", RoleType.IC);
    saveActiveRelationship(mgr.getId(), r1.getId());
    saveActiveRelationship(mgr.getId(), r2.getId());
    saveSummary(mgr.getId(), r1.getId(), PlanState.LOCKED, ReviewStatus.NOT_REVIEWED, false);
    saveSummary(mgr.getId(), r2.getId(), PlanState.LOCKED, ReviewStatus.REVIEWED, false);
    // another manager's report + summary — must be UNREACHABLE from mgr's request (IDOR scoping)
    Employee other = saveEmployee("OtherMgr", RoleType.MANAGER);
    Employee r3 = saveEmployee("Zara", RoleType.IC);
    saveActiveRelationship(other.getId(), r3.getId());
    saveSummary(other.getId(), r3.getId(), PlanState.LOCKED, ReviewStatus.NOT_REVIEWED, false);

    mvc.perform(get(URL).param("weekStart", WEEK.toString()).header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.page.totalElements").value(2))
        .andExpect(jsonPath("$.page.number").value(0))
        .andExpect(jsonPath("$.sort").isArray())
        // B.11 shape on a row; only mgr's reports present, never the other manager's
        .andExpect(
            jsonPath("$.content[*].employeeDisplayName")
                .value(org.hamcrest.Matchers.hasItems("Alice", "Bob")))
        .andExpect(
            jsonPath("$.content[*].employeeDisplayName")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Zara"))))
        .andExpect(jsonPath("$.content[0].managerEmployeeId").value(mgr.getId().toString()));
  }

  // --- F.5 default sort: no `sort` ⇒ employeeDisplayName ASC within the (single) week ----
  @Test
  void commandCenter_defaultSort_employeeDisplayNameAsc() throws Exception {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    Employee carol = saveEmployee("Carol", RoleType.IC);
    Employee alice = saveEmployee("Alice", RoleType.IC);
    Employee bob = saveEmployee("Bob", RoleType.IC);
    for (Employee r : new Employee[] {carol, alice, bob}) {
      saveActiveRelationship(mgr.getId(), r.getId());
      saveSummary(mgr.getId(), r.getId(), PlanState.LOCKED, ReviewStatus.NOT_REVIEWED, false);
    }

    mvc.perform(get(URL).param("weekStart", WEEK.toString()).header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].employeeDisplayName").value("Alice"))
        .andExpect(jsonPath("$.content[1].employeeDisplayName").value("Bob"))
        .andExpect(jsonPath("$.content[2].employeeDisplayName").value("Carol"));
  }

  // --- pagination: size=1 paginates; size>100 clamps to 100 ----
  @Test
  void commandCenter_pagination_andClamp() throws Exception {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    for (String n : new String[] {"Alice", "Bob", "Carol"}) {
      Employee r = saveEmployee(n, RoleType.IC);
      saveActiveRelationship(mgr.getId(), r.getId());
      saveSummary(mgr.getId(), r.getId(), PlanState.LOCKED, ReviewStatus.NOT_REVIEWED, false);
    }

    mvc.perform(
            get(URL)
                .param("weekStart", WEEK.toString())
                .param("size", "1")
                .header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].employeeDisplayName").value("Alice")) // page 0, sorted
        .andExpect(jsonPath("$.page.size").value(1))
        .andExpect(jsonPath("$.page.totalElements").value(3))
        .andExpect(jsonPath("$.page.totalPages").value(3));

    mvc.perform(
            get(URL)
                .param("weekStart", WEEK.toString())
                .param("size", "500")
                .header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.size").value(100)); // clamped to max 100 (F.5/B.20)
  }

  // --- summary-level filters (REQ-F-023): employeeId / planState / reviewState incl. OVERDUE ----
  @Test
  void commandCenter_summaryFilters() throws Exception {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    Employee alice = saveEmployee("Alice", RoleType.IC); // LOCKED / REVIEWED / not overdue
    Employee bob = saveEmployee("Bob", RoleType.IC); // RECONCILING / NOT_REVIEWED / overdue
    saveActiveRelationship(mgr.getId(), alice.getId());
    saveActiveRelationship(mgr.getId(), bob.getId());
    saveSummary(mgr.getId(), alice.getId(), PlanState.LOCKED, ReviewStatus.REVIEWED, false);
    saveSummary(mgr.getId(), bob.getId(), PlanState.RECONCILING, ReviewStatus.NOT_REVIEWED, true);
    String mgrId = mgr.getId().toString();

    // employeeId narrows to one
    mvc.perform(
            get(URL)
                .param("weekStart", WEEK.toString())
                .param("employeeId", bob.getId().toString())
                .header(HEADER, mgrId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].employeeDisplayName").value("Bob"));
    // planState narrows
    mvc.perform(
            get(URL)
                .param("weekStart", WEEK.toString())
                .param("planState", "LOCKED")
                .header(HEADER, mgrId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].employeeDisplayName").value("Alice"));
    // reviewState=OVERDUE → only isReviewOverdue=true rows (derived, not a stored status)
    mvc.perform(
            get(URL)
                .param("weekStart", WEEK.toString())
                .param("reviewState", "OVERDUE")
                .header(HEADER, mgrId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].employeeDisplayName").value("Bob"))
        .andExpect(jsonPath("$.content[0].isReviewOverdue").value(true));
    // reviewState=REVIEWED → only the stored REVIEWED status
    mvc.perform(
            get(URL)
                .param("weekStart", WEEK.toString())
                .param("reviewState", "REVIEWED")
                .header(HEADER, mgrId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].reviewStatus").value("REVIEWED"));
    // a filter matching nothing → empty content, totalElements=0
    mvc.perform(
            get(URL)
                .param("weekStart", WEEK.toString())
                .param("planState", "RECONCILED")
                .header(HEADER, mgrId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.page.totalElements").value(0));
  }

  // --- weekStart is required → 400 ----
  @Test
  void commandCenter_missingWeekStart_400() throws Exception {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    Employee r1 = saveEmployee("Alice", RoleType.IC);
    saveActiveRelationship(mgr.getId(), r1.getId());

    mvc.perform(get(URL).header(HEADER, mgr.getId().toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  // --- a mistyped filter value (unknown enum) fails binding → 400 VALIDATION_ERROR, never 500 ----
  @Test
  void commandCenter_invalidFilterValue_400() throws Exception {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    Employee r1 = saveEmployee("Alice", RoleType.IC);
    saveActiveRelationship(mgr.getId(), r1.getId());

    mvc.perform(
            get(URL)
                .param("weekStart", WEEK.toString())
                .param("planState", "BOGUS") // not a PlanState → MethodArgumentTypeMismatch → 400
                .header(HEADER, mgr.getId().toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  // --- coarse gate: an IC (no active reports) is denied the team surface → 403
  // MANAGER_ROLE_REQUIRED
  @Test
  void commandCenter_nonManager_403() throws Exception {
    Employee ic = saveEmployee("Ivy", RoleType.IC); // no reports → isManager=false

    mvc.perform(get(URL).param("weekStart", WEEK.toString()).header(HEADER, ic.getId().toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("MANAGER_ROLE_REQUIRED"));
  }

  // --- the row is the B.11 record, never the projection entity (no id/version/audit leak) ----
  @Test
  void commandCenter_rowIsRecordNotEntity() throws Exception {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    Employee r1 = saveEmployee("Alice", RoleType.IC);
    saveActiveRelationship(mgr.getId(), r1.getId());
    saveSummary(mgr.getId(), r1.getId(), PlanState.LOCKED, ReviewStatus.NOT_REVIEWED, false);

    mvc.perform(get(URL).param("weekStart", WEEK.toString()).header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        // B.11 has no projection-row id; the entity's id/updatedBy/version must never leak
        .andExpect(jsonPath("$.content[0].id").doesNotExist())
        .andExpect(jsonPath("$.content[0].version").doesNotExist())
        .andExpect(jsonPath("$.content[0].createdAt").doesNotExist())
        .andExpect(jsonPath("$.content[0].updatedBy").doesNotExist())
        // B.11 fields ARE present
        .andExpect(jsonPath("$.content[0].employeeId").value(r1.getId().toString()))
        .andExpect(jsonPath("$.content[0].misalignedCount").value(1))
        .andExpect(jsonPath("$.content[0].updatedAt").exists());
  }

  // === 6.5a-2 cross-table EXISTS filters (REQ-F-023) ====================================

  // --- definingObjectiveId: EXISTS over manager_heatmap_cell at the full grain ----
  @Test
  void commandCenter_filterByDefiningObjective() throws Exception {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    Employee alice = saveEmployee("Alice", RoleType.IC); // touches DO-1
    Employee bob = saveEmployee("Bob", RoleType.IC); // touches DO-2
    saveActiveRelationship(mgr.getId(), alice.getId());
    saveActiveRelationship(mgr.getId(), bob.getId());
    saveSummary(mgr.getId(), alice.getId(), PlanState.LOCKED, ReviewStatus.NOT_REVIEWED, false);
    saveSummary(mgr.getId(), bob.getId(), PlanState.LOCKED, ReviewStatus.NOT_REVIEWED, false);
    List<DefiningObjective> dos = definingObjectives.findAllByOrderByIdAsc();
    UUID do1 = dos.get(0).getId();
    UUID do2 = dos.get(1).getId();
    saveHeatmapCell(mgr.getId(), alice.getId(), do1);
    saveHeatmapCell(mgr.getId(), bob.getId(), do2);

    mvc.perform(
            get(URL)
                .param("weekStart", WEEK.toString())
                .param("definingObjectiveId", do1.toString())
                .header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].employeeDisplayName").value("Alice"))
        .andExpect(jsonPath("$.page.totalElements").value(1));
  }

  // --- priority / workType / alignmentStatus: EXISTS over weekly_commitment (s.weeklyPlanId) ----
  @Test
  void commandCenter_filterByCommitmentAttributes() throws Exception {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    Employee alice = saveEmployee("Alice", RoleType.IC); // P0 / STRATEGIC / MISALIGNED
    Employee bob = saveEmployee("Bob", RoleType.IC); // P1 / MAINTENANCE / ALIGNED
    saveActiveRelationship(mgr.getId(), alice.getId());
    saveActiveRelationship(mgr.getId(), bob.getId());
    UUID alicePlan =
        saveSummary(mgr.getId(), alice.getId(), PlanState.LOCKED, ReviewStatus.NOT_REVIEWED, false);
    UUID bobPlan =
        saveSummary(mgr.getId(), bob.getId(), PlanState.LOCKED, ReviewStatus.NOT_REVIEWED, false);
    saveCommitment(alicePlan, Priority.P0, WorkType.STRATEGIC, AlignmentStatus.MISALIGNED);
    saveCommitment(bobPlan, Priority.P1, WorkType.MAINTENANCE, AlignmentStatus.ALIGNED);
    String mgrId = mgr.getId().toString();

    mvc.perform(
            get(URL)
                .param("weekStart", WEEK.toString())
                .param("priority", "P0")
                .header(HEADER, mgrId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].employeeDisplayName").value("Alice"));
    mvc.perform(
            get(URL)
                .param("weekStart", WEEK.toString())
                .param("workType", "STRATEGIC")
                .header(HEADER, mgrId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].employeeDisplayName").value("Alice"));
    mvc.perform(
            get(URL)
                .param("weekStart", WEEK.toString())
                .param("alignmentStatus", "MISALIGNED")
                .header(HEADER, mgrId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].employeeDisplayName").value("Alice"));
  }

  // --- cross-table filters AND-combine with the summary-level filters ----
  @Test
  void commandCenter_crossTableFiltersCombineWithSummaryFilters() throws Exception {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    Employee alice = saveEmployee("Alice", RoleType.IC); // LOCKED + P0 + DO-1 → matches all
    Employee bob = saveEmployee("Bob", RoleType.IC); // RECONCILING + P0 + DO-1 → fails planState
    Employee carol = saveEmployee("Carol", RoleType.IC); // LOCKED + P1 + DO-1 → fails priority
    saveActiveRelationship(mgr.getId(), alice.getId());
    saveActiveRelationship(mgr.getId(), bob.getId());
    saveActiveRelationship(mgr.getId(), carol.getId());
    UUID alicePlan =
        saveSummary(mgr.getId(), alice.getId(), PlanState.LOCKED, ReviewStatus.NOT_REVIEWED, false);
    UUID bobPlan =
        saveSummary(
            mgr.getId(), bob.getId(), PlanState.RECONCILING, ReviewStatus.NOT_REVIEWED, false);
    UUID carolPlan =
        saveSummary(mgr.getId(), carol.getId(), PlanState.LOCKED, ReviewStatus.NOT_REVIEWED, false);
    UUID do1 = definingObjectives.findAllByOrderByIdAsc().get(0).getId();
    saveHeatmapCell(mgr.getId(), alice.getId(), do1);
    saveHeatmapCell(mgr.getId(), bob.getId(), do1);
    saveHeatmapCell(mgr.getId(), carol.getId(), do1);
    saveCommitment(alicePlan, Priority.P0, WorkType.STRATEGIC, AlignmentStatus.MISALIGNED);
    saveCommitment(bobPlan, Priority.P0, WorkType.STRATEGIC, AlignmentStatus.MISALIGNED);
    saveCommitment(carolPlan, Priority.P1, WorkType.STRATEGIC, AlignmentStatus.MISALIGNED);
    String mgrId = mgr.getId().toString();

    // planState=LOCKED AND priority=P0 AND definingObjectiveId=DO-1 → only Alice
    mvc.perform(
            get(URL)
                .param("weekStart", WEEK.toString())
                .param("planState", "LOCKED")
                .param("priority", "P0")
                .param("definingObjectiveId", do1.toString())
                .header(HEADER, mgrId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].employeeDisplayName").value("Alice"));

    // a combination matching nobody → empty content, totalElements=0
    mvc.perform(
            get(URL)
                .param("weekStart", WEEK.toString())
                .param("planState", "LOCKED")
                .param("priority", "P2") // nobody has a P2 commitment
                .header(HEADER, mgrId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.page.totalElements").value(0));
  }

  // --- the IDOR re-pin: cross-table filters NEVER widen the manager scope ----
  @Test
  void commandCenter_crossTableFilters_idorScopeHolds() throws Exception {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    Employee alice = saveEmployee("Alice", RoleType.IC);
    saveActiveRelationship(mgr.getId(), alice.getId());
    UUID alicePlan =
        saveSummary(mgr.getId(), alice.getId(), PlanState.LOCKED, ReviewStatus.NOT_REVIEWED, false);
    UUID do1 = definingObjectives.findAllByOrderByIdAsc().get(0).getId();
    saveHeatmapCell(mgr.getId(), alice.getId(), do1);
    saveCommitment(alicePlan, Priority.P0, WorkType.STRATEGIC, AlignmentStatus.MISALIGNED);

    // another manager's report that ALSO matches the SAME cross-table filter values
    Employee other = saveEmployee("OtherMgr", RoleType.MANAGER);
    Employee zara = saveEmployee("Zara", RoleType.IC);
    saveActiveRelationship(other.getId(), zara.getId());
    UUID zaraPlan =
        saveSummary(
            other.getId(), zara.getId(), PlanState.LOCKED, ReviewStatus.NOT_REVIEWED, false);
    saveHeatmapCell(other.getId(), zara.getId(), do1);
    saveCommitment(zaraPlan, Priority.P0, WorkType.STRATEGIC, AlignmentStatus.MISALIGNED);

    // mgr applies the cross-table filters → still ONLY Alice; Zara stays unreachable (not hidden)
    mvc.perform(
            get(URL)
                .param("weekStart", WEEK.toString())
                .param("definingObjectiveId", do1.toString())
                .param("priority", "P0")
                .header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].employeeDisplayName").value("Alice"))
        // totalElements==1 pins the COUNT query scope too — Zara is excluded from the total, not
        // just the page (a count-scope leak would expose a cross-manager row's existence).
        .andExpect(jsonPath("$.page.totalElements").value(1))
        .andExpect(
            jsonPath("$.content[*].employeeDisplayName")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Zara"))));
  }

  // --- a plan with no matching commitment is excluded by a commitment-EXISTS filter ----
  // (manager_plan_summary.weekly_plan_id is NOT NULL, so a summary always has a plan; the
  // realizable
  //  exclusion case is "plan exists but has no commitment matching the filter" — no plan → no
  // match.)
  @Test
  void commandCenter_planWithNoMatchingCommitment_excludedByCommitmentFilter() throws Exception {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    Employee alice = saveEmployee("Alice", RoleType.IC); // plan seeded, but NO commitments
    saveActiveRelationship(mgr.getId(), alice.getId());
    saveSummary(mgr.getId(), alice.getId(), PlanState.LOCKED, ReviewStatus.NOT_REVIEWED, false);

    mvc.perform(
            get(URL)
                .param("weekStart", WEEK.toString())
                .param("priority", "P0")
                .header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.page.totalElements").value(0));
  }
}
