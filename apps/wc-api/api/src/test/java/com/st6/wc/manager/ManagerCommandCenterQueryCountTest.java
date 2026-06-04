package com.st6.wc.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.RoleType;
import com.st6.wc.manager.dto.ManagerCommandCenterRowDto;
import com.st6.wc.manager.query.CommandCenterFilters;
import com.st6.wc.manager.query.ManagerCommandCenterQuery;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.ManagerPlanSummary;
import com.st6.wc.projection.repo.ManagerPlanSummaryRepository;
import com.st6.wc.support.AbstractAppBootTest;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * N+1-free proof for the E13 command-center read (task 6.5a, §14/REQ-NF-003): the Criteria query
 * issues a FIXED number of JDBC statements — ONE page query (the projection + the display-name
 * cross-join) + ONE count — regardless of the number of report rows. Seeds 3 reports and asserts
 * Hibernate's {@code prepareStatementCount == 2} (an N+1 read would be {@code 2 + 3}). Distinct
 * context (the {@code generate_statistics} property) — its own class, shared PG (LESSONS §29).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "demo-auth.enabled=true",
      "spring.jpa.properties.hibernate.generate_statistics=true"
    })
class ManagerCommandCenterQueryCountTest extends AbstractAppBootTest {

  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);

  @Autowired private EntityManagerFactory emf;
  @Autowired private EmployeeRepository employees;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private ManagerPlanSummaryRepository summaries;
  @Autowired private ManagerCommandCenterQuery query;

  @AfterEach
  void cleanup() {
    summaries.deleteAll();
    plans.deleteAll();
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

  private void saveSummary(UUID managerId, UUID reportId) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(reportId);
    p.setWeekStartDate(WEEK);
    p.setWeekEndDate(WEEK.plusDays(6));
    p.setState(PlanState.LOCKED);
    plans.saveAndFlush(p);
    ManagerPlanSummary s = new ManagerPlanSummary();
    s.setId(UUID.randomUUID());
    s.setManagerEmployeeId(managerId);
    s.setEmployeeId(reportId);
    s.setWeeklyPlanId(p.getId());
    s.setWeekStartDate(WEEK);
    s.setPlanState(PlanState.LOCKED);
    s.setReviewStatus(ReviewStatus.NOT_REVIEWED);
    s.setReviewDueAt(Instant.parse("2026-06-04T22:00:00Z"));
    s.setReviewOverdue(false);
    s.setUpdatedAt(Instant.parse("2026-06-03T12:00:00Z"));
    summaries.saveAndFlush(s);
  }

  @Test
  void commandCenter_isNPlusOneFree_fixedTwoStatements() {
    Employee mgr = saveEmployee("Manager", RoleType.MANAGER);
    saveSummary(mgr.getId(), saveEmployee("Alice", RoleType.IC).getId());
    saveSummary(mgr.getId(), saveEmployee("Bob", RoleType.IC).getId());
    saveSummary(mgr.getId(), saveEmployee("Carol", RoleType.IC).getId());

    Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.setStatisticsEnabled(true);
    stats.clear(); // count ONLY the read below, not the seeding

    Page<ManagerCommandCenterRowDto> page =
        query.findCommandCenter(
            mgr.getId(),
            WEEK,
            new CommandCenterFilters(null, null, null, null),
            PageRequest.of(
                0,
                25,
                Sort.by(Sort.Order.desc("weekStartDate"), Sort.Order.asc("employeeDisplayName"))));

    assertThat(page.getContent()).hasSize(3); // sanity: all 3 reports, names joined
    // exactly the page query + the count query — does NOT grow with the 3 rows (no per-row lookup)
    assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
  }
}
