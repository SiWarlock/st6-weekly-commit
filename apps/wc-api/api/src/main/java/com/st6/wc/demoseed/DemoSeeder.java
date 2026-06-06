package com.st6.wc.demoseed;

import com.st6.wc.common.OrgTimeConfig;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The week-parameterized demo-seed logic (brief 107, Appendix E Part 2, §3/§9/§10/§17) — the
 * runtime/ops sibling of the fixed-week V5/V6 Flyway fixture, parameterized to <em>any</em> Monday
 * week-start. {@link #run(LocalDate)} normalizes its arg to the week's Monday and (107a scope)
 * performs the <strong>reset</strong> half of the lead-approved reset-then-seed: an FK-safe delete
 * of the 7 demo personas' derived rows across the <strong>two-week footprint {W, W−7}</strong>
 * (seeding week W also writes W−7 — Grace's carry-forward source + Dana's own RECONCILED plan). The
 * 7-persona matrix re-insert + temporal correctness + projection recompute + idempotency is brief
 * 107b.
 *
 * <p>The deletes are scoped SQL (immediate + ordered, so FK constraints are honored predictably and
 * the domain repositories stay free of demo-only bulk-delete methods) bound by the persona-id list
 * + the two footprint weeks — identity (employees/relationships) and reference (RCDO) rows are
 * never touched. Activation as a one-shot Job is {@link com.st6.wc.job.DemoSeedRunner}'s job (gated
 * on {@code --app.job=seed-demo}). Mirrors the inert-logic / thin-runner split of {@code
 * ProjectionRebuilder} + {@code ProjectionRebuildRunner}.
 */
@Component
public class DemoSeeder {

  private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

  /** The 7 demo personas (V5 literal ids): Dana (manager) + her 6 IC direct reports. */
  static final List<UUID> PERSONA_IDS =
      List.of(
          UUID.fromString("d0000000-0000-0000-0000-000000000001"), // Dana Okafor (manager)
          UUID.fromString("d0000000-0000-0000-0000-000000000002"), // Priya Raman
          UUID.fromString("d0000000-0000-0000-0000-000000000003"), // Marco Bellini
          UUID.fromString("d0000000-0000-0000-0000-000000000004"), // Aisha Khan
          UUID.fromString("d0000000-0000-0000-0000-000000000005"), // Tomas Novak
          UUID.fromString("d0000000-0000-0000-0000-000000000006"), // Grace Liu
          UUID.fromString("d0000000-0000-0000-0000-000000000007")); // Sam Carter

  private final OrgTimeConfig orgTimeConfig;

  // Inject the NamedParameterJdbcOperations interface, not the concrete NamedParameterJdbcTemplate
  // —
  // the project convention (AwsSnsLifecycleGateway) that sidesteps the SpotBugs EI_EXPOSE_REP2
  // false-positive on storing an injected concrete; the auto-configured bean injects as the
  // interface.
  private final NamedParameterJdbcOperations jdbc;

  public DemoSeeder(OrgTimeConfig orgTimeConfig, NamedParameterJdbcOperations jdbc) {
    this.orgTimeConfig = orgTimeConfig;
    this.jdbc = jdbc;
  }

  /**
   * Reset-then-seed the demo matrix for the week containing {@code week} (107a: reset only). The
   * arg is normalized to its Monday, so any day in the target week resolves to the same {@code
   * week_start_date}.
   */
  @Transactional
  public void run(LocalDate week) {
    LocalDate weekStart = orgTimeConfig.weekStartDate(week);
    reset(weekStart);
    log.info(
        "Demo-seed reset complete for week {} (+ prior {}).", weekStart, weekStart.minusWeeks(1));
  }

  /**
   * FK-safe delete of the personas' derived rows across the footprint {@code {weekStart,
   * weekStart−7}}: disputes → reviews → projections → sync → commitments → plans. Scoped by persona
   * id + the two weeks; identity + reference data are never in scope.
   */
  private void reset(LocalDate weekStart) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("personas", PERSONA_IDS)
            .addValue("weeks", List.of(weekStart, weekStart.minusWeeks(1)));

    jdbc.update(
        "delete from alignment_dispute d using weekly_commitment c, weekly_plan p"
            + " where d.commitment_id = c.id and c.weekly_plan_id = p.id"
            + " and p.employee_id in (:personas) and p.week_start_date in (:weeks)",
        params);
    jdbc.update(
        "delete from manager_review mr using weekly_plan p"
            + " where mr.weekly_plan_id = p.id"
            + " and p.employee_id in (:personas) and p.week_start_date in (:weeks)",
        params);
    jdbc.update(
        "delete from manager_heatmap_cell"
            + " where employee_id in (:personas) and week_start_date in (:weeks)",
        params);
    jdbc.update(
        "delete from manager_plan_summary"
            + " where employee_id in (:personas) and week_start_date in (:weeks)",
        params);
    jdbc.update(
        "delete from outlook_calendar_sync_record"
            + " where owner_employee_id in (:personas) and week_start_date in (:weeks)",
        params);
    jdbc.update(
        "delete from weekly_commitment c using weekly_plan p"
            + " where c.weekly_plan_id = p.id"
            + " and p.employee_id in (:personas) and p.week_start_date in (:weeks)",
        params);
    jdbc.update(
        "delete from weekly_plan"
            + " where employee_id in (:personas) and week_start_date in (:weeks)",
        params);
  }
}
