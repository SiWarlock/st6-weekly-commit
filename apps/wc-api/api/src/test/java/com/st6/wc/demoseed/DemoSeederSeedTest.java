package com.st6.wc.demoseed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The seed half of the week-parameterized demo-seed Job (brief 107b, Appendix E Part 2,
 * §3/§9/§10/§17). Boots the full {@code :api} context against an <em>isolated</em> per-class PG16
 * container migrated through {@code db/migration} ONLY (V1–V4 — a CLEAN slate with RCDO but no
 * personas/fixtures), so {@link DemoSeeder#run(LocalDate)} must build the whole matrix from bare
 * migration: ensure the 7 personas + 6 relationships, reset, insert the 7-persona lifecycle matrix
 * for the target week (+ Grace/Dana at W−7), recompute projections from source, and write one
 * SYSTEM audit row. A fixed {@link Clock} pins the two SLA-sensitive review due-dates so the
 * OVERDUE/not split derives correctly at view time (Marco overdue, Priya not — §17 / rule #6).
 *
 * <p>Mirrors {@code V6FixtureStateMigrationTest}'s per-persona assertions, but against the
 * <strong>parameterized week</strong> via the <strong>Java seeder</strong> (direct row insertion,
 * NO lifecycle services — so no real Outlook syncs fire), and proves idempotency + temporal
 * portability across weeks.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@Testcontainers
class DemoSeederSeedTest {

  @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.13");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PG::getJdbcUrl);
    registry.add("spring.datasource.username", PG::getUsername);
    registry.add("spring.datasource.password", PG::getPassword);
    registry.add("spring.flyway.enabled", () -> "true");
    // CLEAN slate: migration chain ONLY (V1–V4 RCDO) — no V5 personas, no V6 matrix. The seeder
    // must build everything (persona-ensure exercised on a bare DB).
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add("spring.datasource.hikari.maximum-pool-size", () -> "2");
  }

  // Tue→Wed anchor: 2026-06-10 12:00Z (07:00 America/Chicago, Wed) → current week Mon 2026-06-08.
  private static final Instant NOW = Instant.parse("2026-06-10T12:00:00Z");
  private static final String NOW_SQL = "2026-06-10 12:00:00+00";

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  // Personas (V5 literal ids) — the seeder ensures these exist.
  private static final UUID DANA = UUID.fromString("d0000000-0000-0000-0000-000000000001");
  private static final UUID PRIYA = UUID.fromString("d0000000-0000-0000-0000-000000000002");
  private static final UUID MARCO = UUID.fromString("d0000000-0000-0000-0000-000000000003");
  private static final UUID AISHA = UUID.fromString("d0000000-0000-0000-0000-000000000004");
  private static final UUID TOMAS = UUID.fromString("d0000000-0000-0000-0000-000000000005");
  private static final UUID GRACE = UUID.fromString("d0000000-0000-0000-0000-000000000006");
  private static final UUID SAM = UUID.fromString("d0000000-0000-0000-0000-000000000007");

  // V4 SO-1.2 = the carry-forward link target (RC a / DO b / SO c convention).
  private static final String SO_1_2 = "c0000000-0000-0000-0000-000000000002";

  private static final LocalDate W = LocalDate.of(2026, 6, 8); // current week (Mon) at the anchor
  private static final LocalDate W_PRIOR = LocalDate.of(2026, 6, 1); // W−7
  private static final LocalDate W2 = LocalDate.of(2026, 6, 15); // a different week (portability)
  private static final LocalDate W2_PRIOR = LocalDate.of(2026, 6, 8);

  // The fixed, non-PII failure constants the seed sets on Marco's FAILED record (rule #7 teeth).
  private static final String FAILED_SAFE_MESSAGE = "Calendar sync failed; you can retry.";
  private static final String FAILED_FAILURE_CODE = "GRAPH_FORBIDDEN";

  @Autowired private DemoSeeder seeder;
  @Autowired private JdbcTemplate jdbc;

  /** Bare V1–V4 before each test (FK order) — every test exercises persona-ensure from scratch. */
  @org.junit.jupiter.api.BeforeEach
  void wipeToBareMigration() {
    jdbc.execute("delete from audit_event");
    jdbc.execute("delete from alignment_dispute");
    jdbc.execute("delete from manager_review");
    jdbc.execute("delete from manager_heatmap_cell");
    jdbc.execute("delete from manager_plan_summary");
    jdbc.execute("delete from outlook_calendar_sync_record");
    jdbc.execute("delete from weekly_commitment");
    jdbc.execute("delete from weekly_plan");
    jdbc.execute("delete from manager_relationship");
    jdbc.execute("delete from employee");
  }

  // --- 6. one plan per persona in the matrix state (parameterized week) --------------------------
  @Test
  void seedsSevenPersonaMatrixForTargetWeek() {
    seeder.run(W);

    assertThat(planState(PRIYA, W)).isEqualTo("LOCKED");
    assertThat(planState(MARCO, W)).isEqualTo("LOCKED");
    assertThat(planState(AISHA, W)).isEqualTo("LOCKED");
    assertThat(planState(TOMAS, W)).isEqualTo("LOCKED");
    assertThat(planState(GRACE, W)).isEqualTo("RECONCILING");
    assertThat(planState(GRACE, W_PRIOR)).isEqualTo("RECONCILED"); // carry-forward source plan
    assertThat(planState(SAM, W)).isEqualTo("DRAFT");
    assertThat(planState(DANA, W_PRIOR)).isEqualTo("RECONCILED"); // Dana's own IC plan (unmanaged)

    String priyaPlan = planId(PRIYA, W);
    assertThat(scalarInt(plannedCount(priyaPlan)))
        .as("Priya has 3 planned commitments")
        .isEqualTo(3);
    assertThat(
            scalarInt(
                "select count(*) from weekly_commitment where weekly_plan_id = '"
                    + priyaPlan
                    + "' and supporting_outcome_id is null"))
        .as("Priya's planned commitments are all linked")
        .isZero();

    String samPlan = planId(SAM, W);
    assertThat(
            scalarInt(
                "select count(*) from weekly_commitment where weekly_plan_id = '"
                    + samPlan
                    + "' and commitment_kind = 'PLANNED' and supporting_outcome_id is null"))
        .as("Sam has exactly one unlinked planned commitment (the can't-lock fixture)")
        .isEqualTo(1);
    assertThat(
            scalarInt(
                "select count(*) from weekly_commitment where weekly_plan_id = '"
                    + samPlan
                    + "' and supporting_outcome_id is not null"))
        .as("Sam also has a linked commitment")
        .isEqualTo(1);
  }

  // --- 6b. rule #1: every locked-baseline (non-DRAFT) plan has a valid linked planned baseline ---
  @Test
  void nonDraftPlansHaveLinkedPlannedBaseline_ruleOne() {
    seeder.run(W);

    // No non-DRAFT plan carries an UNLINKED planned commitment — an impossible real lock state.
    assertThat(
            scalarInt(
                "select count(*) from weekly_commitment c"
                    + " join weekly_plan p on p.id = c.weekly_plan_id"
                    + " where p.state <> 'DRAFT' and c.commitment_kind = 'PLANNED'"
                    + " and c.supporting_outcome_id is null"))
        .as("no locked-baseline plan has an unlinked planned commitment (rule #1)")
        .isZero();

    // Every non-DRAFT plan has at least one PLANNED commitment (the lock precondition).
    assertThat(
            scalarInt(
                "select count(*) from weekly_plan p where p.state <> 'DRAFT' and not exists ("
                    + "select 1 from weekly_commitment c where c.weekly_plan_id = p.id"
                    + " and c.commitment_kind = 'PLANNED')"))
        .as("every locked-baseline plan has >= 1 planned commitment (rule #1)")
        .isZero();
  }

  // --- 7. stored review states + OVERDUE derived at the Clock's now ------------------------------
  @Test
  void reviewStatesAndOverdueDeriveAtClockNow() {
    seeder.run(W);

    assertThat(reviewStatus(PRIYA, W)).isEqualTo("NOT_REVIEWED");
    assertThat(reviewStatus(MARCO, W)).isEqualTo("NOT_REVIEWED");
    assertThat(reviewStatus(AISHA, W)).isEqualTo("REVIEWED_WITH_DISPUTES");
    assertThat(reviewStatus(TOMAS, W)).isEqualTo("REVIEWED");
    assertThat(reviewStatus(GRACE, W)).isEqualTo("REVIEWED");
    assertThat(reviewStatus(SAM, W)).as("DRAFT has no review").isNull();
    assertThat(reviewStatus(DANA, W_PRIOR)).as("Dana unmanaged → no review").isNull();

    // isReviewOverdue = NOT_REVIEWED ∧ now > review_due_at — the clock-anchored split holds at NOW.
    assertThat(reviewIsOverdueAt(MARCO, W)).as("Marco derives OVERDUE at the Clock now").isTrue();
    assertThat(reviewIsOverdueAt(PRIYA, W)).as("Priya is NOT overdue at the Clock now").isFalse();
  }

  // --- 8. disputes: Aisha OPEN MISALIGNED, Tomas RESOLVED; partial-unique holds ------------------
  @Test
  void disputesOpenAndResolvedPartialUniqueHolds() {
    seeder.run(W);

    String aishaOpen = openDisputeCommitmentId(AISHA);
    assertThat(aishaOpen).as("Aisha has an OPEN dispute").isNotNull();
    assertThat(
            scalarString(
                "select d.flag_type from alignment_dispute d where d.commitment_id = '"
                    + aishaOpen
                    + "' and d.status = 'OPEN'"))
        .isEqualTo("MISALIGNED");
    assertThat(
            scalarString(
                "select c.alignment_status from weekly_commitment c where c.id = '"
                    + aishaOpen
                    + "'"))
        .as("the disputed commitment is NEEDS_REVIEW")
        .isEqualTo("NEEDS_REVIEW");
    assertThat(
            scalarInt(
                "select count(*) from alignment_dispute where commitment_id = '"
                    + aishaOpen
                    + "' and (ic_response is not null or resolved_at is not null)"))
        .as("the OPEN dispute has no IC response and is unresolved")
        .isZero();

    // Tomas: exactly one RESOLVED dispute with full lifecycle traces.
    assertThat(unresolvedDisputeCount(TOMAS, W)).as("Tomas has no unresolved dispute").isZero();
    assertThat(
            scalarInt(
                "select count(*) from alignment_dispute d"
                    + " join weekly_commitment c on c.id = d.commitment_id"
                    + " join weekly_plan p on p.id = c.weekly_plan_id"
                    + " where p.employee_id = '"
                    + TOMAS
                    + "' and d.status = 'RESOLVED'"
                    + " and d.ic_response is not null and d.resolved_at is not null"))
        .as("Tomas has one fully-resolved dispute")
        .isEqualTo(1);

    // The unresolved-dispute partial-unique rejects a 2nd OPEN dispute on Aisha's commitment.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into alignment_dispute"
                        + "(id, commitment_id, manager_employee_id, status, flag_type, manager_note,"
                        + " version) values (?, ?, ?, 'OPEN', 'NEEDS_REVISION', 'dup', 0)",
                    UUID.randomUUID(),
                    UUID.fromString(aishaOpen),
                    DANA))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // --- 9. carry-forward chain links the current commitment to the prior-week source -------------
  @Test
  void carryForwardChainLinksToPriorWeekSource() {
    seeder.run(W);

    String priorPlan = planId(GRACE, W_PRIOR);
    String currentPlan = planId(GRACE, W);

    String src =
        scalarString(
            "select id from weekly_commitment where weekly_plan_id = '"
                + priorPlan
                + "' and reconciliation_outcome = 'CARRIED_FORWARD'");
    assertThat(src).as("Grace's prior-week plan has a CARRIED_FORWARD commitment").isNotNull();
    assertThat(
            scalarString(
                "select supporting_outcome_id from weekly_commitment where id = '" + src + "'"))
        .isEqualTo(SO_1_2);

    assertThat(
            scalarInt(
                "select count(*) from weekly_commitment where weekly_plan_id = '"
                    + currentPlan
                    + "' and carry_forward_source_commitment_id = '"
                    + src
                    + "' and supporting_outcome_id = '"
                    + SO_1_2
                    + "' and commitment_kind = 'PLANNED'"))
        .as("Grace's current-week plan carries forward the source, linked SO-1.2, PLANNED")
        .isEqualTo(1);
  }

  // --- 10. sync records: Marco FAILED, Priya SYNCED, Dana MANAGER_REVIEW_BLOCK (non-PII) --------
  @Test
  void syncRecordsTerminalStatesNonPii() {
    seeder.run(W);

    // Marco: exactly one FAILED IC_PLANNING — graph_event_id null, retry≥1.
    assertThat(
            scalarInt(
                "select count(*) from outlook_calendar_sync_record where owner_employee_id = '"
                    + MARCO
                    + "' and event_kind = 'IC_PLANNING' and status = 'FAILED'"
                    + " and graph_event_id is null and retry_count >= 1"))
        .as("Marco has a FAILED IC_PLANNING sync (no graph_event_id, retried)")
        .isEqualTo(1);
    // rule #7 teeth: the failure detail EQUALS the known-safe constants — proves no persona
    // name/email leaked into the message (equality, not mere presence).
    assertThat(
            scalarString(
                "select safe_message from outlook_calendar_sync_record where owner_employee_id = '"
                    + MARCO
                    + "' and event_kind = 'IC_PLANNING' and status = 'FAILED'"))
        .as("Marco's safe_message is the fixed non-PII constant")
        .isEqualTo(FAILED_SAFE_MESSAGE);
    assertThat(
            scalarString(
                "select failure_code from outlook_calendar_sync_record where owner_employee_id = '"
                    + MARCO
                    + "' and event_kind = 'IC_PLANNING' and status = 'FAILED'"))
        .as("Marco's failure_code is the fixed constant")
        .isEqualTo(FAILED_FAILURE_CODE);

    // Priya: one SYNCED IC_PLANNING.
    assertThat(
            scalarInt(
                "select count(*) from outlook_calendar_sync_record where owner_employee_id = '"
                    + PRIYA
                    + "' and event_kind = 'IC_PLANNING' and status = 'SYNCED'"))
        .as("Priya has a SYNCED IC_PLANNING sync")
        .isEqualTo(1);

    // Dana: one SYNCED MANAGER_REVIEW_BLOCK keyed (owner=Dana, week=W).
    assertThat(
            scalarInt(
                "select count(*) from outlook_calendar_sync_record where owner_employee_id = '"
                    + DANA
                    + "' and event_kind = 'MANAGER_REVIEW_BLOCK' and status = 'SYNCED'"
                    + " and week_start_date = '"
                    + W
                    + "'"))
        .as("Dana has a SYNCED MANAGER_REVIEW_BLOCK for the target week")
        .isEqualTo(1);

    // The one-review-block-per-manager/week partial-unique rejects a 2nd block for Dana/W.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into outlook_calendar_sync_record"
                        + "(id, owner_employee_id, related_type, related_id, event_kind, status,"
                        + " week_start_date, retry_count, version)"
                        + " values (?, ?, 'MANAGER_REVIEW_WEEK', ?, 'MANAGER_REVIEW_BLOCK',"
                        + " 'PENDING_PUBLISH', ?, 0, 0)",
                    UUID.randomUUID(),
                    DANA,
                    DANA,
                    W))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // --- 11. projections recomputed from source (not hand-seeded) ---------------------------------
  @Test
  void projectionsRecomputedFromSourceNotHandSeeded() {
    seeder.run(W);

    // A summary row exists for each of Dana's 5 reviewed reports at W.
    assertThat(
            scalarInt(
                "select count(*) from manager_plan_summary where manager_employee_id = '"
                    + DANA
                    + "' and week_start_date = '"
                    + W
                    + "'"))
        .as("projections exist for Dana's reviewed reports")
        .isEqualTo(5);

    // Aisha's summary reflects the OPEN MISALIGNED dispute — derived from source, not a literal.
    assertThat(
            scalarInt(
                "select unresolved_dispute_count from manager_plan_summary where employee_id = '"
                    + AISHA
                    + "' and week_start_date = '"
                    + W
                    + "'"))
        .as("Aisha's summary shows 1 unresolved dispute")
        .isEqualTo(1);
    assertThat(
            scalarInt(
                "select misaligned_count from manager_plan_summary where employee_id = '"
                    + AISHA
                    + "' and week_start_date = '"
                    + W
                    + "'"))
        .as("Aisha's summary shows the MISALIGNED union ≥ 1")
        .isPositive();

    // Heatmap cells were produced too (not hand-seeded — the recompute populated them).
    assertThat(scalarInt("select count(*) from manager_heatmap_cell")).isPositive();
  }

  // --- 12. idempotent: run(W) twice → identical states + row counts -----------------------------
  @Test
  void idempotentReRun() {
    seeder.run(W);
    int plans = scalarInt("select count(*) from weekly_plan");
    int commitments = scalarInt("select count(*) from weekly_commitment");
    int reviews = scalarInt("select count(*) from manager_review");
    int disputes = scalarInt("select count(*) from alignment_dispute");
    int syncs = scalarInt("select count(*) from outlook_calendar_sync_record");
    int summaries = scalarInt("select count(*) from manager_plan_summary");
    int cells = scalarInt("select count(*) from manager_heatmap_cell");

    seeder.run(W); // re-run: reset clears the prior take, re-seed rebuilds the identical matrix

    assertThat(scalarInt("select count(*) from weekly_plan")).isEqualTo(plans);
    assertThat(scalarInt("select count(*) from weekly_commitment")).isEqualTo(commitments);
    assertThat(scalarInt("select count(*) from manager_review")).isEqualTo(reviews);
    assertThat(scalarInt("select count(*) from alignment_dispute")).isEqualTo(disputes);
    assertThat(scalarInt("select count(*) from outlook_calendar_sync_record")).isEqualTo(syncs);
    assertThat(scalarInt("select count(*) from manager_plan_summary")).isEqualTo(summaries);
    assertThat(scalarInt("select count(*) from manager_heatmap_cell")).isEqualTo(cells);

    // states unchanged
    assertThat(planState(GRACE, W)).isEqualTo("RECONCILING");
    assertThat(reviewStatus(AISHA, W)).isEqualTo("REVIEWED_WITH_DISPUTES");
    assertThat(scalarInt("select count(*) from employee")).as("personas idempotent").isEqualTo(7);
  }

  // --- 13. temporal portability: the matrix renders correctly for a DIFFERENT week --------------
  @Test
  void temporalPortabilityAcrossWeeks() {
    seeder.run(W2); // a different week than the anchor's current week

    assertThat(planState(GRACE, W2)).isEqualTo("RECONCILING");
    assertThat(planState(GRACE, W2_PRIOR)).isEqualTo("RECONCILED"); // carry-forward source at W2−7
    assertThat(planState(PRIYA, W2)).isEqualTo("LOCKED");
    assertThat(reviewIsOverdueAt(MARCO, W2)).as("Marco overdue at the Clock now for W2").isTrue();
    assertThat(reviewIsOverdueAt(PRIYA, W2)).as("Priya not overdue for W2").isFalse();
  }

  // --- 14. persona-ensure: the seeder builds the 7 employees + 6 relationships idempotently -----
  @Test
  void ensuresPersonasAndRelationshipsIdempotently() {
    seeder.run(W);
    assertThat(scalarInt("select count(*) from employee")).isEqualTo(7);
    assertThat(scalarInt("select count(*) from manager_relationship")).isEqualTo(6);

    seeder.run(W); // re-run does not duplicate identity rows
    assertThat(scalarInt("select count(*) from employee")).isEqualTo(7);
    assertThat(scalarInt("select count(*) from manager_relationship")).isEqualTo(6);
  }

  // --- 15. one SYSTEM (null-actor) audit row per run --------------------------------------------
  @Test
  void writesOneSystemAuditRowPerRun() {
    seeder.run(W);

    assertThat(scalarInt("select count(*) from audit_event where action = 'DEMO_SEEDED'"))
        .as("one DEMO_SEEDED audit row per run")
        .isEqualTo(1);
    assertThat(
            scalarInt(
                "select count(*) from audit_event where action = 'DEMO_SEEDED'"
                    + " and actor_employee_id is null"))
        .as("the seed audit is SYSTEM (null actor)")
        .isEqualTo(1);
  }

  // ===== helpers =====

  private String plannedCount(String planId) {
    return "select count(*) from weekly_commitment where weekly_plan_id = '"
        + planId
        + "' and commitment_kind = 'PLANNED'";
  }

  private String planState(UUID employeeId, LocalDate week) {
    return scalarString(
        "select state from weekly_plan where employee_id = '"
            + employeeId
            + "' and week_start_date = '"
            + week
            + "'");
  }

  private String planId(UUID employeeId, LocalDate week) {
    return scalarString(
        "select id from weekly_plan where employee_id = '"
            + employeeId
            + "' and week_start_date = '"
            + week
            + "'");
  }

  private String reviewStatus(UUID employeeId, LocalDate week) {
    return scalarString(
        "select mr.status from manager_review mr join weekly_plan p on p.id = mr.weekly_plan_id"
            + " where p.employee_id = '"
            + employeeId
            + "' and p.week_start_date = '"
            + week
            + "'");
  }

  private boolean reviewIsOverdueAt(UUID employeeId, LocalDate week) {
    return scalarInt(
            "select count(*) from manager_review mr join weekly_plan p on p.id = mr.weekly_plan_id"
                + " where p.employee_id = '"
                + employeeId
                + "' and p.week_start_date = '"
                + week
                + "' and mr.status = 'NOT_REVIEWED' and mr.review_due_at < timestamptz '"
                + NOW_SQL
                + "'")
        == 1;
  }

  private int unresolvedDisputeCount(UUID employeeId, LocalDate week) {
    return scalarInt(
        "select count(*) from alignment_dispute d"
            + " join weekly_commitment c on c.id = d.commitment_id"
            + " join weekly_plan p on p.id = c.weekly_plan_id"
            + " where p.employee_id = '"
            + employeeId
            + "' and p.week_start_date = '"
            + week
            + "' and d.status in ('OPEN','IC_RESPONDED')");
  }

  private String openDisputeCommitmentId(UUID employeeId) {
    return scalarString(
        "select d.commitment_id from alignment_dispute d"
            + " join weekly_commitment c on c.id = d.commitment_id"
            + " join weekly_plan p on p.id = c.weekly_plan_id"
            + " where p.employee_id = '"
            + employeeId
            + "' and d.status = 'OPEN'");
  }

  private int scalarInt(String sql) {
    Integer n = jdbc.queryForObject(sql, Integer.class);
    return n == null ? 0 : n;
  }

  private String scalarString(String sql) {
    return jdbc.query(sql, rs -> rs.next() ? rs.getString(1) : null);
  }
}
