package com.st6.wc.manager;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.st6.wc.audit.repo.AuditEventRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.RoleType;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.ManagerPlanSummary;
import com.st6.wc.projection.repo.ManagerPlanSummaryRepository;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.support.AbstractAppBootTest;
import java.time.Instant;
import java.time.LocalDate;
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

  @AfterEach
  void cleanup() {
    auditEvents
        .deleteAll(); // the IC-denial test writes an authorization-denial audit (FK→employee)
    summaries.deleteAll();
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

  /** Seed a manager_plan_summary for (manager, report) with the fields the filters/sort assert. */
  private void saveSummary(
      UUID managerId,
      UUID reportId,
      PlanState planState,
      ReviewStatus reviewStatus,
      boolean overdue) {
    ManagerPlanSummary s = new ManagerPlanSummary();
    s.setId(UUID.randomUUID());
    s.setManagerEmployeeId(managerId);
    s.setEmployeeId(reportId);
    s.setWeeklyPlanId(savePlan(reportId, planState));
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
}
