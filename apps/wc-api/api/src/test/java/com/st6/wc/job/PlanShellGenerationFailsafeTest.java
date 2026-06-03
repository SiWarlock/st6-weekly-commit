package com.st6.wc.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.RoleType;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.support.AbstractAppBootTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Org-tz fail-safe proof for the generation job (task 3.2, Appendix D.4): a
 * <strong>garbage</strong> {@code app.org.timezone} must fall back to {@code America/Chicago}
 * (never UTC, never throw) via the reused {@code OrgTimeBindingConfig.resolveZone} — so the
 * generated week bounds are the Chicago week of the run instant. Booted as its own context
 * (distinct {@code app.org.timezone}); the fixed {@link Clock} pins the run instant to the demo
 * anchor (2026-06-02 Tue → Chicago week 06-01…06-07).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {"demo-auth.enabled=true", "app.org.timezone=Totally/Bogus"})
class PlanShellGenerationFailsafeTest extends AbstractAppBootTest {

  private static final Instant ANCHOR = Instant.parse("2026-06-02T17:00:00Z");
  private static final LocalDate CHICAGO_MONDAY = LocalDate.of(2026, 6, 1);
  private static final LocalDate CHICAGO_SUNDAY = LocalDate.of(2026, 6, 7);

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(ANCHOR, ZoneOffset.UTC);
    }
  }

  @Autowired private PlanShellGenerator generator;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private EmployeeRepository employees;

  @AfterEach
  void cleanup() {
    plans.deleteAll();
    employees.deleteAll();
  }

  @Test
  void orgTzFailSafe_garbageZone_fallsBackToChicago() {
    Employee e = new Employee();
    e.setId(UUID.randomUUID());
    e.setEmail("a@x.test");
    e.setDisplayName("A");
    e.setRole(RoleType.IC);
    e.setActive(true);
    employees.saveAndFlush(e);

    generator.generate(); // must not throw despite the bogus zone

    WeeklyPlan shell = plans.findAll().get(0);
    // bounds resolved in the FALL-BACK Chicago zone (not UTC, not a crash)
    assertThat(shell.getWeekStartDate()).isEqualTo(CHICAGO_MONDAY);
    assertThat(shell.getWeekEndDate()).isEqualTo(CHICAGO_SUNDAY);
  }
}
