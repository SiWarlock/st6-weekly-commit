package com.st6.wc.job;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.st6.wc.audit.AuditService;
import com.st6.wc.common.OrgTimeConfig;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.PlanState;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates one DRAFT {@link WeeklyPlan} shell per {@code active=true} employee for the org-tz week
 * containing "now" (task 3.2, §8 / §3 / §6 / Appendix D.4). Runs as the {@link
 * com.st6.wc.identity.SystemPrincipal} actor — no user request, exempt from self/direct-report
 * scoping, and its writes are limited to plan-shell rows (it never touches commitments or sync
 * records, REQ-I-002). <strong>Idempotent</strong> via an existence pre-filter (the employee-ids
 * that already have a shell for the target week are skipped); the {@code unique(employee_id,
 * week_start_date)} constraint (V1) is the DB backstop. Writes one SYSTEM-actor (null {@code
 * actor_employee_id}) {@code audit_event} per run with safe-only metadata (rule #7 — the week + a
 * count, never employee PII). The whole run is one transaction so the shells + the audit row commit
 * atomically.
 *
 * <p>This is the inert logic component; activation as a one-shot job is {@link
 * PlanShellGenerationRunner}'s job (gated on {@code --app.job=generate-plan-shells}).
 */
@Component
public class PlanShellGenerator {

  static final String AUDIT_ACTION = "PLAN_SHELLS_GENERATED";

  private static final Logger log = LoggerFactory.getLogger(PlanShellGenerator.class);

  private final EmployeeRepository employees;
  private final WeeklyPlanRepository plans;
  private final OrgTimeConfig orgTimeConfig;
  private final Clock clock;
  private final AuditService auditService;

  public PlanShellGenerator(
      EmployeeRepository employees,
      WeeklyPlanRepository plans,
      OrgTimeConfig orgTimeConfig,
      Clock clock,
      AuditService auditService) {
    this.employees = employees;
    this.plans = plans;
    this.orgTimeConfig = orgTimeConfig;
    this.clock = clock;
    this.auditService = auditService;
  }

  /**
   * Generates the missing DRAFT shells for the current org-tz week and writes the run audit.
   *
   * @return the number of shells created this run (0 on a rerun or an empty active roster).
   */
  @Transactional
  public int generate() {
    Instant now = clock.instant();
    LocalDate weekStart = orgTimeConfig.weekStartDate(now); // Monday, org tz (fail-safe-resolved)
    LocalDate weekEnd = orgTimeConfig.weekEndDate(weekStart); // Sunday

    // idempotency pre-filter: employees who already have a shell for this week are skipped.
    Set<UUID> alreadyHasShell =
        plans.findByWeekStartDate(weekStart).stream()
            .map(WeeklyPlan::getEmployeeId)
            .collect(Collectors.toSet());

    int created = 0;
    for (Employee employee : employees.findByActiveTrue()) {
      if (alreadyHasShell.contains(employee.getId())) {
        continue;
      }
      WeeklyPlan shell = new WeeklyPlan();
      shell.setId(UUID.randomUUID());
      shell.setEmployeeId(employee.getId());
      shell.setWeekStartDate(weekStart);
      shell.setWeekEndDate(weekEnd);
      shell.setState(PlanState.DRAFT);
      shell.setGeneratedAt(now);
      plans.save(shell);
      created++;
    }

    auditRun(weekStart, created);
    log.info("Plan-shell generation: {} DRAFT shell(s) created for week {}", created, weekStart);
    return created;
  }

  /** One SYSTEM-actor (null actor) run audit; safe metadata built via an escaped JSON node. */
  private void auditRun(LocalDate weekStart, int created) {
    String safeMetadata =
        JsonNodeFactory.instance
            .objectNode()
            .put("week_start_date", weekStart.toString())
            .put("shells_created_count", created)
            .toString();
    auditService.record(
        AUDIT_ACTION,
        "WeeklyPlan",
        null, // run-level — no single entity id
        null, // SYSTEM actor (§6): null actor_employee_id
        "Generated " + created + " weekly plan shell(s) for week " + weekStart,
        safeMetadata);
  }
}
