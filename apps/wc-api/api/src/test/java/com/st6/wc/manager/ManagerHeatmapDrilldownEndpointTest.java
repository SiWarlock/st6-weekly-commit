package com.st6.wc.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.st6.wc.audit.AuditEvent;
import com.st6.wc.audit.repo.AuditEventRepository;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.WorkType;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.ManagerHeatmapCell;
import com.st6.wc.projection.repo.ManagerHeatmapCellRepository;
import com.st6.wc.rcdo.SupportingOutcome;
import com.st6.wc.rcdo.repo.DefiningObjectiveRepository;
import com.st6.wc.rcdo.repo.SupportingOutcomeRepository;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.support.AbstractAppBootTest;
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
 * {@code GET /api/manager/heatmap/{cellId}/drilldown} (E15, task 6.5b) end-to-end. Proves the
 * own-cell IDOR chokepoint ({@code authorizeHeatmapCellAccess} FIRST: cross-manager → 404 + audit;
 * missing → 404 no-audit, §25), the Supporting-Outcome breakdown ({@code DrilldownOutcomeGroup[]}),
 * each group's commitments a B.20 envelope (priority ASC, F.5), and the <strong>affordance
 * posture</strong> — the context-free {@code CommitmentMapper.toDto(c)} → empty {@code
 * allowedActions} (dispute affordances are E3/E4-only, 5.5b; §35).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class ManagerHeatmapDrilldownEndpointTest extends AbstractAppBootTest {

  private static final String HEADER = "X-Demo-Employee-Id";
  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);

  @Autowired private MockMvc mvc;
  @Autowired private EmployeeRepository employees;
  @Autowired private ManagerRelationshipRepository relationships;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private WeeklyCommitmentRepository commitments;
  @Autowired private ManagerHeatmapCellRepository heatmapCells;
  @Autowired private DefiningObjectiveRepository definingObjectives;
  @Autowired private SupportingOutcomeRepository supportingOutcomes;
  @Autowired private AuditEventRepository auditEvents;

  @AfterEach
  void cleanup() {
    auditEvents.deleteAll(); // denial-audit rows (FK → employee)
    commitments.deleteAll(); // FK → weekly_plan
    heatmapCells.deleteAll(); // FK → employee + defining_objective
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

  private UUID savePlan(UUID ownerId) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(ownerId);
    p.setWeekStartDate(WEEK);
    p.setWeekEndDate(WEEK.plusDays(6));
    p.setState(com.st6.wc.enums.PlanState.LOCKED);
    return plans.saveAndFlush(p).getId();
  }

  private void saveCommitment(UUID planId, UUID supportingOutcomeId, Priority priority) {
    saveCommitment(UUID.randomUUID(), planId, supportingOutcomeId, priority);
  }

  private void saveCommitment(UUID id, UUID planId, UUID supportingOutcomeId, Priority priority) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(id);
    c.setWeeklyPlanId(planId);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("commitment");
    c.setSupportingOutcomeId(supportingOutcomeId);
    c.setPriority(priority);
    c.setWorkType(WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(AlignmentStatus.ALIGNED);
    commitments.saveAndFlush(c);
  }

  private UUID saveHeatmapCell(UUID managerId, UUID reportId, UUID definingObjectiveId) {
    ManagerHeatmapCell h = new ManagerHeatmapCell();
    h.setId(UUID.randomUUID());
    h.setManagerEmployeeId(managerId);
    h.setEmployeeId(reportId);
    h.setWeekStartDate(WEEK);
    h.setDefiningObjectiveId(definingObjectiveId);
    h.setUpdatedAt(java.time.Instant.parse("2026-06-03T12:00:00Z"));
    return heatmapCells.saveAndFlush(h).getId();
  }

  /** The Supporting Outcomes seeded under a Defining Objective (V4), in id order. */
  private List<SupportingOutcome> sosUnder(UUID definingObjectiveId) {
    return supportingOutcomes.findAllByOrderByIdAsc().stream()
        .filter(so -> so.getDefiningObjectiveId().equals(definingObjectiveId))
        .toList();
  }

  private UUID do1() {
    return definingObjectives.findAllByOrderByIdAsc().get(0).getId();
  }

  // --- own cell → SO groups, each a paginated commitments envelope, priority ASC ----
  @Test
  void drilldown_ownCell_returnsOutcomeGroups() throws Exception {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    Employee alice = saveEmployee("Alice", RoleType.IC);
    saveActiveRelationship(mgr.getId(), alice.getId());
    UUID plan = savePlan(alice.getId());
    List<SupportingOutcome> sos = sosUnder(do1());
    saveCommitment(plan, sos.get(0).getId(), Priority.P0);
    saveCommitment(plan, sos.get(1).getId(), Priority.P1);
    UUID cellId = saveHeatmapCell(mgr.getId(), alice.getId(), do1());

    mvc.perform(
            get("/api/manager/heatmap/{cellId}/drilldown", cellId)
                .header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cellId").value(cellId.toString()))
        .andExpect(jsonPath("$.employeeId").value(alice.getId().toString()))
        .andExpect(jsonPath("$.definingObjectiveId").value(do1().toString()))
        .andExpect(jsonPath("$.supportingOutcomes.length()").value(2))
        .andExpect(
            jsonPath("$.supportingOutcomes[0].supportingOutcomeId")
                .value(sos.get(0).getId().toString()))
        .andExpect(jsonPath("$.supportingOutcomes[0].supportingOutcomeTitle").exists())
        .andExpect(jsonPath("$.supportingOutcomes[0].commitments.content.length()").value(1))
        .andExpect(jsonPath("$.supportingOutcomes[0].commitments.page.totalElements").value(1))
        .andExpect(
            jsonPath("$.supportingOutcomes[1].supportingOutcomeId")
                .value(sos.get(1).getId().toString()));
  }

  // --- a cell whose manager_employee_id ≠ principal → 404 + a SPECIFIC denial audit (own-cell
  // IDOR) ----
  @Test
  void drilldown_notOwnCell_404AndAudit() throws Exception {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    Employee report = saveEmployee("Rob", RoleType.IC);
    saveActiveRelationship(mgr.getId(), report.getId()); // mgr is a manager (isManager=true)
    Employee other = saveEmployee("OtherMgr", RoleType.MANAGER);
    Employee zara = saveEmployee("Zara", RoleType.IC);
    saveActiveRelationship(other.getId(), zara.getId());
    UUID othersCell = saveHeatmapCell(other.getId(), zara.getId(), do1());

    mvc.perform(
            get("/api/manager/heatmap/{cellId}/drilldown", othersCell)
                .header(HEADER, mgr.getId().toString()))
        .andExpect(status().isNotFound());
    // §38 6.5b addendum: assert the SPECIFIC AUTHORIZATION_DENIED + HeatmapCell row, not count()>0
    // (accumulation can't make a stale assertion pass); credited to the requesting manager.
    assertSingleDenialAudit("HeatmapCell", mgr.getId());
  }

  // --- an IC has no team surface (REQ-F-030): IC → drill-down → 404 (owns no cell) + a denial
  // audit ----
  @Test
  void drilldown_icDenied_404_writesAudit() throws Exception {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    Employee report = saveEmployee("Rob", RoleType.IC);
    saveActiveRelationship(mgr.getId(), report.getId());
    UUID mgrCell = saveHeatmapCell(mgr.getId(), report.getId(), do1());
    Employee ic = saveEmployee("Ivy", RoleType.IC); // not a manager → owns no cell

    mvc.perform(
            get("/api/manager/heatmap/{cellId}/drilldown", mgrCell)
                .header(HEADER, ic.getId().toString()))
        .andExpect(
            status().isNotFound()); // the own-cell authorizer → IDOR 404 (manager namespace hidden)
    assertSingleDenialAudit("HeatmapCell", ic.getId());
  }

  // --- an unknown cellId → 404, NO audit (§25 no audit-spam on id-probing) ----
  @Test
  void drilldown_missingCell_404NoAudit() throws Exception {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    Employee report = saveEmployee("Rob", RoleType.IC);
    saveActiveRelationship(mgr.getId(), report.getId());

    mvc.perform(
            get("/api/manager/heatmap/{cellId}/drilldown", UUID.randomUUID())
                .header(HEADER, mgr.getId().toString()))
        .andExpect(status().isNotFound());
    assertThat(auditEvents.count()).isZero(); // missing → no audit
  }

  /**
   * §6/§17 + rule #7 (§15): exactly one {@code AUTHORIZATION_DENIED} audit for the given resource
   * type after a denial (the comprehensive SENTINEL-no-leak sweep is the service-level {@code
   * AuthorizationIdorMatrixTest}; the heatmap-cell denial carries only UUIDs, no resource text).
   */
  private void assertSingleDenialAudit(String entityType, UUID expectedActor) {
    List<AuditEvent> denials = auditEvents.findAll();
    assertThat(denials).hasSize(1);
    AuditEvent a = denials.get(0);
    assertThat(a.getAction()).isEqualTo("AUTHORIZATION_DENIED");
    assertThat(a.getEntityType()).isEqualTo(entityType);
    assertThat(a.getActorEmployeeId()).isEqualTo(expectedActor);
  }

  // --- every drilldown commitment carries empty allowedActions (the 5.5b posture, §35) ----
  @Test
  void drilldown_commitmentsCarryNoAffordances() throws Exception {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    Employee alice = saveEmployee("Alice", RoleType.IC);
    saveActiveRelationship(mgr.getId(), alice.getId());
    UUID plan = savePlan(alice.getId());
    saveCommitment(plan, sosUnder(do1()).get(0).getId(), Priority.P0);
    UUID cellId = saveHeatmapCell(mgr.getId(), alice.getId(), do1());

    mvc.perform(
            get("/api/manager/heatmap/{cellId}/drilldown", cellId)
                .header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.supportingOutcomes[*].commitments.content[*].allowedActions")
                .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.empty())));
  }

  // --- each group's commitments are a B.20 envelope; page/size honored; priority-ASC sort ----
  @Test
  void drilldown_commitmentsPaginated() throws Exception {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    Employee alice = saveEmployee("Alice", RoleType.IC);
    saveActiveRelationship(mgr.getId(), alice.getId());
    UUID plan = savePlan(alice.getId());
    UUID so = sosUnder(do1()).get(0).getId();
    saveCommitment(plan, so, Priority.P2);
    saveCommitment(plan, so, Priority.P0);
    saveCommitment(plan, so, Priority.P1);
    UUID cellId = saveHeatmapCell(mgr.getId(), alice.getId(), do1());

    mvc.perform(
            get("/api/manager/heatmap/{cellId}/drilldown", cellId)
                .param("size", "2")
                .header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.supportingOutcomes.length()").value(1))
        .andExpect(jsonPath("$.supportingOutcomes[0].commitments.content.length()").value(2))
        .andExpect(jsonPath("$.supportingOutcomes[0].commitments.page.size").value(2))
        .andExpect(jsonPath("$.supportingOutcomes[0].commitments.page.totalElements").value(3))
        .andExpect(jsonPath("$.supportingOutcomes[0].commitments.page.totalPages").value(2))
        // priority ASC (F.5) → P0 first
        .andExpect(jsonPath("$.supportingOutcomes[0].commitments.content[0].priority").value("P0"));
  }

  // --- same-priority commitments paginate deterministically via the id-ASC tiebreaker ----
  // (F.5 priority+createdAt leaves same-priority ties unstable while createdAt is null; the id-ASC
  //  refinement makes pagination a clean cover today — no overlap/skip across pages.)
  @Test
  void drilldown_samePriorityCommitments_paginateDeterministically() throws Exception {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    Employee alice = saveEmployee("Alice", RoleType.IC);
    saveActiveRelationship(mgr.getId(), alice.getId());
    UUID plan = savePlan(alice.getId());
    UUID so = sosUnder(do1()).get(0).getId();
    // ascending, high-bits-zero ids so the Postgres uuid order == Java order; ALL the same priority
    UUID c1 = new UUID(0L, 1L);
    UUID c2 = new UUID(0L, 2L);
    UUID c3 = new UUID(0L, 3L);
    saveCommitment(c1, plan, so, Priority.P1);
    saveCommitment(c2, plan, so, Priority.P1);
    saveCommitment(c3, plan, so, Priority.P1);
    UUID cellId = saveHeatmapCell(mgr.getId(), alice.getId(), do1());

    UUID[] expectedByPage = {c1, c2, c3};
    for (int page = 0; page < 3; page++) {
      mvc.perform(
              get("/api/manager/heatmap/{cellId}/drilldown", cellId)
                  .param("page", String.valueOf(page))
                  .param("size", "1")
                  .header(HEADER, mgr.getId().toString()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.supportingOutcomes[0].commitments.page.totalElements").value(3))
          .andExpect(jsonPath("$.supportingOutcomes[0].commitments.content.length()").value(1))
          .andExpect(
              jsonPath("$.supportingOutcomes[0].commitments.content[0].id")
                  .value(expectedByPage[page].toString()));
    }
  }
}
