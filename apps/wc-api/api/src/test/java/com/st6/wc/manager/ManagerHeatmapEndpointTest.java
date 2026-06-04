package com.st6.wc.manager;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.st6.wc.audit.repo.AuditEventRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.RoleType;
import com.st6.wc.projection.ManagerHeatmapCell;
import com.st6.wc.projection.repo.ManagerHeatmapCellRepository;
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
 * {@code GET /api/manager/heatmap} (E14, task 6.5b) end-to-end through the demo-mode 2.6 chain
 * against real PG16 + the V4 RCDO seed. Proves the {@code HeatmapResponseDto} (NOT paginated) of
 * {@code HeatmapCellDto} rows, <strong>direct-report query-scoping</strong> (a manager sees ONLY
 * their own reports' cells — another manager's are unreachable, §6/IDOR), the coarse manager-only
 * gate (IC → 403), the {@code definingObjectiveId} column-axis filter, and the joined {@code
 * employeeDisplayName} + {@code definingObjectiveTitle}. N+1 is pinned separately in {@link
 * ManagerHeatmapQueryCountTest}.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class ManagerHeatmapEndpointTest extends AbstractAppBootTest {

  private static final String HEADER = "X-Demo-Employee-Id";
  private static final String URL = "/api/manager/heatmap";
  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);

  @Autowired private MockMvc mvc;
  @Autowired private EmployeeRepository employees;
  @Autowired private ManagerRelationshipRepository relationships;
  @Autowired private ManagerHeatmapCellRepository heatmapCells;
  @Autowired private DefiningObjectiveRepository definingObjectives;
  @Autowired private AuditEventRepository auditEvents;

  @AfterEach
  void cleanup() {
    auditEvents.deleteAll(); // the IC-403 test writes a denial audit (FK → employee) — §38 FK-order
    heatmapCells.deleteAll(); // FK → employee + defining_objective: delete before employees
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

  private void saveHeatmapCell(UUID managerId, UUID reportId, UUID definingObjectiveId) {
    ManagerHeatmapCell h = new ManagerHeatmapCell();
    h.setId(UUID.randomUUID());
    h.setManagerEmployeeId(managerId);
    h.setEmployeeId(reportId);
    h.setWeekStartDate(WEEK);
    h.setDefiningObjectiveId(definingObjectiveId);
    h.setCommitmentCount(2);
    h.setUpdatedAt(Instant.parse("2026-06-03T12:00:00Z"));
    heatmapCells.saveAndFlush(h);
  }

  // --- scoping: a manager sees ONLY their own reports' cells for the week, names+titles joined
  // ----
  @Test
  void heatmap_returnsManagerScopedCells() throws Exception {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    Employee alice = saveEmployee("Alice", RoleType.IC);
    saveActiveRelationship(mgr.getId(), alice.getId());
    List<DefiningObjective> dos = definingObjectives.findAllByOrderByIdAsc();
    saveHeatmapCell(mgr.getId(), alice.getId(), dos.get(0).getId());
    saveHeatmapCell(mgr.getId(), alice.getId(), dos.get(1).getId());
    // another manager's cell — must be unreachable
    Employee other = saveEmployee("OtherMgr", RoleType.MANAGER);
    Employee zara = saveEmployee("Zara", RoleType.IC);
    saveActiveRelationship(other.getId(), zara.getId());
    saveHeatmapCell(other.getId(), zara.getId(), dos.get(0).getId());

    mvc.perform(get(URL).param("weekStart", WEEK.toString()).header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.weekStart").value(WEEK.toString()))
        .andExpect(jsonPath("$.cells.length()").value(2))
        .andExpect(
            jsonPath("$.cells[*].employeeDisplayName")
                .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("Alice"))))
        .andExpect(jsonPath("$.cells[0].definingObjectiveTitle").exists())
        .andExpect(jsonPath("$.cells[0].cellId").exists())
        .andExpect(jsonPath("$.cells[0].managerEmployeeId").value(mgr.getId().toString()));
  }

  // --- coarse gate: an IC (no active reports) → 403 MANAGER_ROLE_REQUIRED ----
  @Test
  void heatmap_icDenied403() throws Exception {
    Employee ic = saveEmployee("Ivy", RoleType.IC);

    mvc.perform(get(URL).param("weekStart", WEEK.toString()).header(HEADER, ic.getId().toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("MANAGER_ROLE_REQUIRED"));
  }

  // --- the IDOR re-pin: another manager's cells stay unreachable (scope from principal) ----
  @Test
  void heatmap_idorScopeHolds() throws Exception {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    Employee alice = saveEmployee("Alice", RoleType.IC);
    saveActiveRelationship(mgr.getId(), alice.getId());
    UUID do1 = definingObjectives.findAllByOrderByIdAsc().get(0).getId();
    saveHeatmapCell(mgr.getId(), alice.getId(), do1);
    Employee other = saveEmployee("OtherMgr", RoleType.MANAGER);
    Employee zara = saveEmployee("Zara", RoleType.IC);
    saveActiveRelationship(other.getId(), zara.getId());
    saveHeatmapCell(other.getId(), zara.getId(), do1);

    mvc.perform(get(URL).param("weekStart", WEEK.toString()).header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cells.length()").value(1))
        .andExpect(jsonPath("$.cells[0].employeeDisplayName").value("Alice"))
        .andExpect(
            jsonPath("$.cells[*].employeeDisplayName")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Zara"))));
  }

  // --- definingObjectiveId column-axis filter ----
  @Test
  void heatmap_filterByDefiningObjective() throws Exception {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    Employee alice = saveEmployee("Alice", RoleType.IC);
    saveActiveRelationship(mgr.getId(), alice.getId());
    List<DefiningObjective> dos = definingObjectives.findAllByOrderByIdAsc();
    UUID do1 = dos.get(0).getId();
    UUID do2 = dos.get(1).getId();
    saveHeatmapCell(mgr.getId(), alice.getId(), do1);
    saveHeatmapCell(mgr.getId(), alice.getId(), do2);

    mvc.perform(
            get(URL)
                .param("weekStart", WEEK.toString())
                .param("definingObjectiveId", do1.toString())
                .header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cells.length()").value(1))
        .andExpect(jsonPath("$.cells[0].definingObjectiveId").value(do1.toString()));
  }

  // --- NOT paginated: a plain {weekStart, cells[]}, no B.20 envelope ----
  @Test
  void heatmap_notPaginated() throws Exception {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    Employee alice = saveEmployee("Alice", RoleType.IC);
    saveActiveRelationship(mgr.getId(), alice.getId());
    saveHeatmapCell(
        mgr.getId(), alice.getId(), definingObjectives.findAllByOrderByIdAsc().get(0).getId());

    mvc.perform(get(URL).param("weekStart", WEEK.toString()).header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cells").isArray())
        .andExpect(jsonPath("$.page").doesNotExist())
        .andExpect(jsonPath("$.content").doesNotExist())
        .andExpect(jsonPath("$.sort").doesNotExist());
  }
}
