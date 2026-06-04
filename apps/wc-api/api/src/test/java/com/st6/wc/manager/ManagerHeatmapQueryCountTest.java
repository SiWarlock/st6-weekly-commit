package com.st6.wc.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.RoleType;
import com.st6.wc.manager.dto.HeatmapCellDto;
import com.st6.wc.manager.query.ManagerHeatmapQuery;
import com.st6.wc.projection.ManagerHeatmapCell;
import com.st6.wc.projection.repo.ManagerHeatmapCellRepository;
import com.st6.wc.rcdo.repo.DefiningObjectiveRepository;
import com.st6.wc.support.AbstractAppBootTest;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * N+1-free proof for the E14 heatmap read (task 6.5b, §14/REQ-NF-003): the Criteria query issues a
 * FIXED number of JDBC statements — ONE query (the cells + the {@code employee} display-name +
 * {@code defining_objective} title cross-joins), no per-cell lookups, no count (E14 is NOT
 * paginated). Seeds 3 cells across 3 reports and asserts Hibernate's {@code prepareStatementCount
 * == 1} regardless of cell count (an N+1 read would be {@code 1 + 3}). Distinct context (the {@code
 * generate_statistics} property) — its own class, shared PG (LESSONS §29).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "demo-auth.enabled=true",
      "spring.jpa.properties.hibernate.generate_statistics=true"
    })
class ManagerHeatmapQueryCountTest extends AbstractAppBootTest {

  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);

  @Autowired private EntityManagerFactory emf;
  @Autowired private EmployeeRepository employees;
  @Autowired private ManagerHeatmapCellRepository heatmapCells;
  @Autowired private DefiningObjectiveRepository definingObjectives;
  @Autowired private ManagerHeatmapQuery query;

  @AfterEach
  void cleanup() {
    heatmapCells.deleteAll();
    employees.deleteAll();
  }

  private Employee saveEmployee(String name, RoleType role) {
    Employee e = new Employee();
    e.setId(UUID.randomUUID());
    e.setEmail(name.toLowerCase() + "@x.test");
    e.setDisplayName(name);
    e.setRole(role);
    e.setActive(true);
    return employees.saveAndFlush(e);
  }

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

  @Test
  void heatmap_isNPlusOneFree_fixedOneStatement() {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    UUID do1 = definingObjectives.findAllByOrderByIdAsc().get(0).getId();
    saveHeatmapCell(mgr.getId(), saveEmployee("Alice", RoleType.IC).getId(), do1);
    saveHeatmapCell(mgr.getId(), saveEmployee("Bob", RoleType.IC).getId(), do1);
    saveHeatmapCell(mgr.getId(), saveEmployee("Carol", RoleType.IC).getId(), do1);

    Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.setStatisticsEnabled(true);
    stats.clear(); // count ONLY the read below, not the seeding

    List<HeatmapCellDto> cells = query.findCells(mgr.getId(), WEEK, null);

    assertThat(cells).hasSize(3); // all 3 cells, names + DO titles joined
    // exactly the single join query — does NOT grow with the 3 cells (no per-cell lookup, no count)
    assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
  }
}
