package com.st6.wc.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.st6.wc.audit.AuditEvent;
import com.st6.wc.audit.repo.AuditEventRepository;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.RoleType;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.support.AbstractAppBootTest;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Generation-logic proof for {@link PlanShellGenerator} (task 3.2, §8 / §3 / §6 / Appendix D.4)
 * against real PG16 ({@link AbstractAppBootTest}). Boots the <strong>normal web app</strong> (no
 * {@code --app.job}) so the gated {@link PlanShellGenerationRunner} is absent (proving inertness)
 * and exercises the logic by calling {@code generator.generate()} directly with a fixed {@link
 * Clock} pinned to the demo anchor (2026-06-02 Tue → week 2026-06-01…06-07 in org tz). Proves: one
 * DRAFT shell per active employee with correct Mon–Sun bounds (REQ-F-001/002); idempotent rerun (no
 * dup, no exception); zero sync records + zero commitments (REQ-I-002); exactly one SYSTEM-actor
 * (null actor) audit event per run (§3/§6). Org-tz fail-safe + the gating are covered in {@code
 * PlanShellGenerationFailsafeTest} / {@code PlanShellGenerationRunnerGatingTest}.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
class PlanShellGenerationRunnerTest extends AbstractAppBootTest {

  // Tue 2026-06-02 12:00 America/Chicago (CDT, UTC-5) → week Mon 2026-06-01 … Sun 2026-06-07.
  private static final Instant ANCHOR = Instant.parse("2026-06-02T17:00:00Z");
  private static final LocalDate EXPECTED_MONDAY = LocalDate.of(2026, 6, 1);
  private static final LocalDate EXPECTED_SUNDAY = LocalDate.of(2026, 6, 7);

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
  @Autowired private AuditEventRepository auditEvents;
  @Autowired private OutlookCalendarSyncRecordRepository syncRecords;
  @Autowired private WeeklyCommitmentRepository commitments;

  // the gated one-shot runner must NOT be a bean on a normal web boot (no --app.job) — inertness.
  @Autowired(required = false)
  private PlanShellGenerationRunner runner;

  @AfterEach
  void cleanup() {
    auditEvents.deleteAll();
    plans.deleteAll();
    employees.deleteAll();
  }

  // jsonb round-trips through Postgres canonical form (spaces after ':'), so assert the metadata
  // STRUCTURALLY rather than on an exact compact string (LESSONS §8 — compare jsonb structurally).
  private static JsonNode meta(AuditEvent ev) {
    try {
      return new ObjectMapper().readTree(ev.getMetadataJson());
    } catch (Exception e) {
      throw new IllegalStateException("audit metadata is not valid JSON", e);
    }
  }

  private Employee saveEmployee(boolean active, String email) {
    Employee e = new Employee();
    e.setId(UUID.randomUUID());
    e.setEmail(email);
    e.setDisplayName("Name " + email);
    e.setRole(RoleType.IC);
    e.setActive(active);
    return employees.saveAndFlush(e);
  }

  // --- RED #6 (inertness half): the gated runner is absent on a normal web boot ----
  @Test
  void inertWithoutAppJobArg() {
    assertThat(runner).as("PlanShellGenerationRunner must not wire without --app.job").isNull();
    assertThat(plans.count()).as("a normal boot creates no shells").isZero();
  }

  // --- RED #1: one DRAFT shell per ACTIVE employee, Mon–Sun org-tz bounds; inactive get none ----
  @Test
  void generatesOneDraftShellPerActiveEmployee() {
    Employee a1 = saveEmployee(true, "a1@x.test");
    Employee a2 = saveEmployee(true, "a2@x.test");
    saveEmployee(false, "inactive@x.test"); // gets no shell

    generator.generate();

    List<WeeklyPlan> shells = plans.findAll();
    assertThat(shells).hasSize(2);
    assertThat(shells)
        .allSatisfy(
            p -> {
              assertThat(p.getState()).isEqualTo(PlanState.DRAFT);
              assertThat(p.getWeekStartDate()).isEqualTo(EXPECTED_MONDAY);
              assertThat(p.getWeekEndDate()).isEqualTo(EXPECTED_SUNDAY);
            });
    assertThat(shells)
        .extracting(WeeklyPlan::getEmployeeId)
        .containsExactlyInAnyOrder(a1.getId(), a2.getId());
  }

  // --- RED #2: rerun is idempotent — no duplicate shell, no exception surfaced ----
  @Test
  void rerun_isIdempotent_noDuplicateNoException() {
    saveEmployee(true, "a1@x.test");
    saveEmployee(true, "a2@x.test");

    generator.generate();
    assertThatCode(() -> generator.generate()).doesNotThrowAnyException(); // second run is clean

    assertThat(plans.count()).isEqualTo(2); // still one per active employee, no duplicates
  }

  // --- RED #3: a run creates ZERO sync records and ZERO commitments — shells only (REQ-I-002) ----
  @Test
  void createsZeroSyncRecordsAndZeroCommitments() {
    saveEmployee(true, "a1@x.test");

    generator.generate();

    assertThat(plans.count()).isEqualTo(1);
    assertThat(syncRecords.count()).as("generation creates NO outlook sync records").isZero();
    assertThat(commitments.count()).as("generation creates NO commitments — shells only").isZero();
  }

  // --- RED #4: exactly one SYSTEM-actor (null actor) audit event per run, safe metadata ----
  @Test
  void writesOneSystemActorAuditEvent() {
    saveEmployee(true, "a1@x.test");
    saveEmployee(true, "a2@x.test");

    generator.generate();

    List<AuditEvent> events = auditEvents.findAll();
    assertThat(events).hasSize(1);
    AuditEvent ev = events.get(0);
    assertThat(ev.getActorEmployeeId()).as("SYSTEM actor — null employee id (§6)").isNull();
    assertThat(ev.getAction()).isEqualTo("PLAN_SHELLS_GENERATED");
    // safe metadata only (rule #7): the week + the count, never employee PII (structural assert)
    assertThat(meta(ev).get("week_start_date").asText()).isEqualTo("2026-06-01");
    assertThat(meta(ev).get("shells_created_count").asInt()).isEqualTo(2);
    assertThat(ev.getMetadataJson()).doesNotContain("@x.test");
  }

  // --- fold-in: empty active roster -> zero shells, no throw, still ONE run audit (count=0) ----
  @Test
  void emptyActiveRoster_createsNoShells_stillAuditsRun() {
    saveEmployee(false, "inactive@x.test"); // only inactive → no active roster

    assertThatCode(() -> generator.generate()).doesNotThrowAnyException();

    assertThat(plans.count()).as("no active employee → no shells").isZero();
    // the run still happened → one operational-trail audit row, count 0 (orch-pinned behavior)
    List<AuditEvent> events = auditEvents.findAll();
    assertThat(events).hasSize(1);
    assertThat(meta(events.get(0)).get("shells_created_count").asInt()).isZero();
  }
}
