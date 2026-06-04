package com.st6.wc.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves {@code V6__seed_fixture_plans_and_state.sql} — the demo lifecycle-state matrix (task
 * 10.3-10.5; ARCHITECTURE.md Appendix E Part 2, §3/§9/§10) — on a real PostgreSQL 16
 * (Testcontainers, never H2 — §17), against the FULL Flyway chain (V1-V6) in an <em>isolated</em>
 * per-class container (same posture as {@link V5SeedPersonasMigrationTest} — the demo seed never
 * pollutes the shared {@code SharedPostgres} harness).
 *
 * <p>Pins the SOURCE rows the lifecycle services would have produced, so the derived reads match
 * the matrix: the per-persona plan states; the STORED review statuses that yield the matrix's reads
 * (R2 OVERDUE via the read-time {@code isReviewOverdue} predicate, R3 the stored {@code
 * REVIEWED_WITH_DISPUTES}, R4 {@code REVIEWED}); the OPEN + RESOLVED disputes; the two-week
 * carry-forward chain; the FAILED/SYNCED/MANAGER_REVIEW_BLOCK sync records; R6's deliberately
 * unlinked DRAFT commitment; idempotency; and that NO projection rows are hand-seeded (the rebuild
 * Job populates them on deploy, §9/§17).
 */
@Testcontainers
class V6FixtureStateMigrationTest {

  private static final String PG_SQLSTATE_UNIQUE = "23505";

  private static final String SCHEMA_LOCATION = "classpath:db/migration";
  private static final String SEED_LOCATION = "classpath:db/demo-seed";
  private static final String SEED_RESOURCE = "/db/demo-seed/V6__seed_fixture_plans_and_state.sql";

  // Personas (V5).
  private static final String DANA = "d0000000-0000-0000-0000-000000000001";
  private static final String R1_PRIYA = "d0000000-0000-0000-0000-000000000002";
  private static final String R2_MARCO = "d0000000-0000-0000-0000-000000000003";
  private static final String R3_AISHA = "d0000000-0000-0000-0000-000000000004";
  private static final String R4_TOMAS = "d0000000-0000-0000-0000-000000000005";
  private static final String R5_GRACE = "d0000000-0000-0000-0000-000000000006";
  private static final String R6_SAM = "d0000000-0000-0000-0000-000000000007";

  // Supporting outcomes (V4): SO-1.2 = c…02 (the carry-forward link target).
  private static final String SO_1_2 = "c0000000-0000-0000-0000-000000000002";

  private static final String CURRENT_WEEK = "2026-06-01"; // Mon
  private static final String PRIOR_WEEK = "2026-05-25";
  // Anchor "now" = 2026-06-02 morning CT (08:00 CDT = 13:00Z) — the demo Clock instant.
  private static final String ANCHOR_NOW = "2026-06-02 13:00:00+00";

  @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.13");

  private static Connection conn;

  @BeforeAll
  static void migrateAndConnect() throws SQLException {
    Flyway.configure()
        .dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
        .locations(SCHEMA_LOCATION, SEED_LOCATION) // full chain incl. V5 personas + V6 fixtures
        .load()
        .migrate();
    conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
  }

  @AfterAll
  static void close() throws SQLException {
    if (conn != null) {
      conn.close();
    }
  }

  // --- 1. one plan per persona in the matrix state ----------------------------------------------
  @Test
  void v6_seedsPlanPerPersonaInMatrixState() throws SQLException {
    assertThat(planState(R1_PRIYA, CURRENT_WEEK)).isEqualTo("LOCKED");
    assertThat(planState(R2_MARCO, CURRENT_WEEK)).isEqualTo("LOCKED");
    assertThat(planState(R3_AISHA, CURRENT_WEEK)).isEqualTo("LOCKED");
    assertThat(planState(R4_TOMAS, CURRENT_WEEK)).isEqualTo("LOCKED");
    assertThat(planState(R5_GRACE, CURRENT_WEEK)).isEqualTo("RECONCILING");
    assertThat(planState(R5_GRACE, PRIOR_WEEK))
        .isEqualTo("RECONCILED"); // carry-forward source plan
    assertThat(planState(R6_SAM, CURRENT_WEEK)).isEqualTo("DRAFT");
    assertThat(planState(DANA, PRIOR_WEEK)).isEqualTo("RECONCILED");

    // R1: exactly 3 PLANNED commitments, all linked to a supporting outcome.
    String r1Plan = planId(R1_PRIYA, CURRENT_WEEK);
    assertThat(
            scalarInt(
                "select count(*) from weekly_commitment where weekly_plan_id = '"
                    + r1Plan
                    + "' and commitment_kind = 'PLANNED'"))
        .as("R1 has 3 planned commitments")
        .isEqualTo(3);
    assertThat(
            scalarInt(
                "select count(*) from weekly_commitment where weekly_plan_id = '"
                    + r1Plan
                    + "' and supporting_outcome_id is null"))
        .as("R1's planned commitments are all linked")
        .isZero();
  }

  // --- 2. stored review source yields the matrix's derived reads --------------------------------
  @Test
  void v6_derivedReviewStatesMatchMatrix() throws SQLException {
    // STORED statuses the lifecycle services would have produced (ReviewService.markReviewed /
    // DisputeService re-derive + STORE REVIEWED_WITH_DISPUTES; only isReviewOverdue is
    // read-derived).
    assertThat(reviewStatus(R1_PRIYA, CURRENT_WEEK)).isEqualTo("NOT_REVIEWED");
    assertThat(reviewStatus(R2_MARCO, CURRENT_WEEK)).isEqualTo("NOT_REVIEWED");
    assertThat(reviewStatus(R3_AISHA, CURRENT_WEEK)).isEqualTo("REVIEWED_WITH_DISPUTES");
    assertThat(reviewStatus(R4_TOMAS, CURRENT_WEEK)).isEqualTo("REVIEWED");
    assertThat(reviewStatus(R5_GRACE, CURRENT_WEEK)).isEqualTo("REVIEWED");
    // Dana is unmanaged → no manager_review row for her plan.
    assertThat(reviewStatus(DANA, PRIOR_WEEK)).isNull();

    // isReviewOverdue = (status==NOT_REVIEWED AND now > review_due_at) — ReviewMapper / rule #6.
    assertThat(reviewIsOverdueAt(R2_MARCO, CURRENT_WEEK, ANCHOR_NOW))
        .as("R2 derives OVERDUE at the anchor")
        .isTrue();
    assertThat(reviewIsOverdueAt(R1_PRIYA, CURRENT_WEEK, ANCHOR_NOW))
        .as("R1 is NOT overdue at the anchor")
        .isFalse();

    // R3 stored WITH_DISPUTES is consistent with exactly 1 unresolved dispute; R4 REVIEWED with 0.
    assertThat(unresolvedDisputeCount(R3_AISHA, CURRENT_WEEK))
        .as("R3 has one unresolved dispute (consistent with REVIEWED_WITH_DISPUTES)")
        .isEqualTo(1);
    assertThat(unresolvedDisputeCount(R4_TOMAS, CURRENT_WEEK))
        .as("R4 has no unresolved dispute (consistent with REVIEWED)")
        .isZero();
  }

  // --- 3. disputes: R3 OPEN MISALIGNED, R4 full RESOLVED; partial-unique holds -------------------
  @Test
  void v6_disputesOpenAndResolved() throws SQLException {
    // R3: exactly one OPEN dispute, flag_type MISALIGNED, ic_response NULL, on a NEEDS_REVIEW
    // commitment.
    try (Statement s = conn.createStatement();
        ResultSet rs =
            s.executeQuery(
                "select d.flag_type, d.ic_response, d.resolved_at, c.alignment_status"
                    + " from alignment_dispute d"
                    + " join weekly_commitment c on c.id = d.commitment_id"
                    + " join weekly_plan p on p.id = c.weekly_plan_id"
                    + " where p.employee_id = '"
                    + R3_AISHA
                    + "' and d.status = 'OPEN'")) {
      assertThat(rs.next()).as("R3 has an OPEN dispute").isTrue();
      assertThat(rs.getString("flag_type")).isEqualTo("MISALIGNED");
      assertThat(rs.getString("ic_response")).as("OPEN dispute has no IC response").isNull();
      assertThat(rs.getTimestamp("resolved_at")).as("OPEN dispute is unresolved").isNull();
      assertThat(rs.getString("alignment_status")).isEqualTo("NEEDS_REVIEW");
      assertThat(rs.next()).as("R3 has exactly one OPEN dispute").isFalse();
    }

    // R4: exactly one RESOLVED dispute with the full lifecycle traces (resolved_at + ic_response
    // set).
    try (Statement s = conn.createStatement();
        ResultSet rs =
            s.executeQuery(
                "select d.ic_response, d.resolved_at"
                    + " from alignment_dispute d"
                    + " join weekly_commitment c on c.id = d.commitment_id"
                    + " join weekly_plan p on p.id = c.weekly_plan_id"
                    + " where p.employee_id = '"
                    + R4_TOMAS
                    + "' and d.status = 'RESOLVED'")) {
      assertThat(rs.next()).as("R4 has a RESOLVED dispute").isTrue();
      assertThat(rs.getString("ic_response"))
          .as("RESOLVED dispute kept the IC response")
          .isNotNull();
      assertThat(rs.getTimestamp("resolved_at")).as("RESOLVED dispute is stamped").isNotNull();
      assertThat(rs.next()).as("R4 has exactly one RESOLVED dispute").isFalse();
    }

    // The unresolved-dispute partial-unique holds: a 2nd OPEN dispute on R3's commitment is
    // rejected.
    String r3DisputedCommitment = openDisputeCommitmentId(R3_AISHA);
    PSQLException ex =
        assertThrows(
            PSQLException.class,
            () ->
                exec(
                    "insert into alignment_dispute"
                        + "(id, commitment_id, manager_employee_id, status, flag_type, manager_note)"
                        + " values ('13000000-0000-0000-0000-0000000000ff','"
                        + r3DisputedCommitment
                        + "','"
                        + DANA
                        + "','OPEN','NEEDS_REVISION','dup')"));
    assertThat(ex.getSQLState())
        .as("uq_one_unresolved_dispute_per_commitment rejects a 2nd unresolved dispute")
        .isEqualTo(PG_SQLSTATE_UNIQUE);
  }

  // --- 4. carry-forward chain links the new commitment to the prior-week source ------------------
  @Test
  void v6_carryForwardChainLinksToSource() throws SQLException {
    String priorPlan = planId(R5_GRACE, PRIOR_WEEK);
    String currentPlan = planId(R5_GRACE, CURRENT_WEEK);

    // C_src: prior-week, CARRIED_FORWARD, linked SO-1.2.
    String cSrc;
    try (Statement s = conn.createStatement();
        ResultSet rs =
            s.executeQuery(
                "select id, supporting_outcome_id from weekly_commitment where weekly_plan_id = '"
                    + priorPlan
                    + "' and reconciliation_outcome = 'CARRIED_FORWARD'")) {
      assertThat(rs.next()).as("R5 prior-week plan has a CARRIED_FORWARD commitment").isTrue();
      cSrc = rs.getString("id");
      assertThat(rs.getString("supporting_outcome_id")).isEqualTo(SO_1_2);
      assertThat(rs.next()).as("exactly one carry-forward source").isFalse();
    }

    // C_next: current-week, carry_forward_source_commitment_id == C_src.id, linked SO-1.2, PLANNED.
    try (Statement s = conn.createStatement();
        ResultSet rs =
            s.executeQuery(
                "select carry_forward_source_commitment_id, supporting_outcome_id, commitment_kind"
                    + " from weekly_commitment where weekly_plan_id = '"
                    + currentPlan
                    + "' and carry_forward_source_commitment_id is not null")) {
      assertThat(rs.next()).as("R5 current-week plan has the carry-forward target").isTrue();
      assertThat(rs.getString("carry_forward_source_commitment_id")).isEqualTo(cSrc);
      assertThat(rs.getString("supporting_outcome_id")).isEqualTo(SO_1_2);
      assertThat(rs.getString("commitment_kind")).isEqualTo("PLANNED");
      assertThat(rs.next()).as("exactly one carry-forward target").isFalse();
    }
  }

  // --- 5. sync records: R2 FAILED, R1 SYNCED, Dana MANAGER_REVIEW_BLOCK --------------------------
  @Test
  void v6_syncRecordsFailedSyncedAndReviewBlock() throws SQLException {
    // R2: one FAILED IC_PLANNING — safe_message present, graph_event_id NULL, failure_code, retry.
    try (Statement s = conn.createStatement();
        ResultSet rs =
            s.executeQuery(
                "select safe_message, graph_event_id, failure_code, retry_count"
                    + " from outlook_calendar_sync_record"
                    + " where owner_employee_id = '"
                    + R2_MARCO
                    + "' and event_kind = 'IC_PLANNING' and status = 'FAILED'")) {
      assertThat(rs.next()).as("R2 has a FAILED IC_PLANNING sync").isTrue();
      assertThat(rs.getString("safe_message")).as("safe_message present").isNotNull();
      assertThat(rs.getString("graph_event_id")).as("no graph_event_id on failure").isNull();
      assertThat(rs.getString("failure_code")).isEqualTo("GRAPH_FORBIDDEN");
      assertThat(rs.getInt("retry_count")).isEqualTo(1);
    }

    // R1: one SYNCED IC_PLANNING (success contrast).
    assertThat(
            scalarInt(
                "select count(*) from outlook_calendar_sync_record where owner_employee_id = '"
                    + R1_PRIYA
                    + "' and event_kind = 'IC_PLANNING' and status = 'SYNCED'"))
        .as("R1 has a SYNCED IC_PLANNING sync")
        .isEqualTo(1);

    // Dana: one MANAGER_REVIEW_BLOCK keyed (owner=Dana, week=current), SYNCED.
    assertThat(
            scalarInt(
                "select count(*) from outlook_calendar_sync_record where owner_employee_id = '"
                    + DANA
                    + "' and event_kind = 'MANAGER_REVIEW_BLOCK' and week_start_date = '"
                    + CURRENT_WEEK
                    + "' and status = 'SYNCED'"))
        .as("Dana has a SYNCED MANAGER_REVIEW_BLOCK for the current week")
        .isEqualTo(1);

    // The one-review-block-per-manager/week partial-unique holds (a 2nd is rejected).
    PSQLException ex =
        assertThrows(
            PSQLException.class,
            () ->
                exec(
                    "insert into outlook_calendar_sync_record"
                        + "(id, owner_employee_id, related_type, related_id, event_kind, status,"
                        + " week_start_date) values ('14000000-0000-0000-0000-0000000000ff','"
                        + DANA
                        + "','MANAGER_REVIEW_WEEK','"
                        + DANA
                        + "','MANAGER_REVIEW_BLOCK','PENDING_PUBLISH','"
                        + CURRENT_WEEK
                        + "')"));
    assertThat(ex.getSQLState())
        .as("uq_one_review_block_per_manager_week rejects a 2nd block for the same manager/week")
        .isEqualTo(PG_SQLSTATE_UNIQUE);
  }

  // --- 6. R6 DRAFT plan has a deliberately-unlinked planned commitment --------------------------
  @Test
  void v6_r6DraftHasUnlinkedCommitment() throws SQLException {
    String r6Plan = planId(R6_SAM, CURRENT_WEEK);
    assertThat(
            scalarInt(
                "select count(*) from weekly_commitment where weekly_plan_id = '"
                    + r6Plan
                    + "' and commitment_kind = 'PLANNED' and supporting_outcome_id is null"))
        .as("R6 has exactly one unlinked planned commitment (the can't-lock fixture)")
        .isEqualTo(1);
    assertThat(
            scalarInt(
                "select count(*) from weekly_commitment where weekly_plan_id = '"
                    + r6Plan
                    + "' and supporting_outcome_id is not null"))
        .as("R6 also has a linked commitment")
        .isEqualTo(1);
  }

  // --- 7. idempotent: re-applying V6 inserts 0 new rows -----------------------------------------
  @Test
  void v6_idempotentReRun() throws SQLException, IOException {
    int plans = scalarInt("select count(*) from weekly_plan");
    int commitments = scalarInt("select count(*) from weekly_commitment");
    int reviews = scalarInt("select count(*) from manager_review");
    int disputes = scalarInt("select count(*) from alignment_dispute");
    int syncs = scalarInt("select count(*) from outlook_calendar_sync_record");

    exec(readSeedScript());

    assertThat(scalarInt("select count(*) from weekly_plan")).isEqualTo(plans);
    assertThat(scalarInt("select count(*) from weekly_commitment")).isEqualTo(commitments);
    assertThat(scalarInt("select count(*) from manager_review")).isEqualTo(reviews);
    assertThat(scalarInt("select count(*) from alignment_dispute")).isEqualTo(disputes);
    assertThat(scalarInt("select count(*) from outlook_calendar_sync_record")).isEqualTo(syncs);
  }

  // --- 8. NO projection rows seeded (the rebuild Job populates them on deploy) -------------------
  @Test
  void v6_noProjectionRowsSeeded() throws SQLException {
    assertThat(scalarInt("select count(*) from manager_plan_summary"))
        .as("V6 hand-seeds no plan-summary projection rows")
        .isZero();
    assertThat(scalarInt("select count(*) from manager_heatmap_cell"))
        .as("V6 hand-seeds no heatmap projection rows")
        .isZero();
  }

  // --- helpers -----------------------------------------------------------------------------------

  private String planState(String employeeId, String weekStart) throws SQLException {
    return scalarString(
        "select state from weekly_plan where employee_id = '"
            + employeeId
            + "' and week_start_date = '"
            + weekStart
            + "'");
  }

  private String planId(String employeeId, String weekStart) throws SQLException {
    return scalarString(
        "select id from weekly_plan where employee_id = '"
            + employeeId
            + "' and week_start_date = '"
            + weekStart
            + "'");
  }

  /** The stored manager_review.status for a persona's plan, or null when no review row exists. */
  private String reviewStatus(String employeeId, String weekStart) throws SQLException {
    return scalarString(
        "select mr.status from manager_review mr join weekly_plan p on p.id = mr.weekly_plan_id"
            + " where p.employee_id = '"
            + employeeId
            + "' and p.week_start_date = '"
            + weekStart
            + "'");
  }

  /** Mirrors ReviewMapper.isOverdue: status==NOT_REVIEWED AND now > review_due_at. */
  private boolean reviewIsOverdueAt(String employeeId, String weekStart, String nowInstant)
      throws SQLException {
    return scalarInt(
            "select count(*) from manager_review mr join weekly_plan p on p.id = mr.weekly_plan_id"
                + " where p.employee_id = '"
                + employeeId
                + "' and p.week_start_date = '"
                + weekStart
                + "' and mr.status = 'NOT_REVIEWED' and mr.review_due_at < timestamptz '"
                + nowInstant
                + "'")
        == 1;
  }

  /** Mirrors ReviewStatusDeriver: count of OPEN/IC_RESPONDED disputes on the plan's commitments. */
  private int unresolvedDisputeCount(String employeeId, String weekStart) throws SQLException {
    return scalarInt(
        "select count(*) from alignment_dispute d"
            + " join weekly_commitment c on c.id = d.commitment_id"
            + " join weekly_plan p on p.id = c.weekly_plan_id"
            + " where p.employee_id = '"
            + employeeId
            + "' and p.week_start_date = '"
            + weekStart
            + "' and d.status in ('OPEN','IC_RESPONDED')");
  }

  private String openDisputeCommitmentId(String employeeId) throws SQLException {
    return scalarString(
        "select d.commitment_id from alignment_dispute d"
            + " join weekly_commitment c on c.id = d.commitment_id"
            + " join weekly_plan p on p.id = c.weekly_plan_id"
            + " where p.employee_id = '"
            + employeeId
            + "' and d.status = 'OPEN'");
  }

  private static String readSeedScript() throws IOException {
    try (InputStream in = V6FixtureStateMigrationTest.class.getResourceAsStream(SEED_RESOURCE)) {
      if (in == null) {
        throw new IOException("V6 seed script not found on classpath: " + SEED_RESOURCE);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private int scalarInt(String sql) throws SQLException {
    try (Statement s = conn.createStatement();
        ResultSet rs = s.executeQuery(sql)) {
      rs.next();
      return rs.getInt(1);
    }
  }

  private String scalarString(String sql) throws SQLException {
    try (Statement s = conn.createStatement();
        ResultSet rs = s.executeQuery(sql)) {
      return rs.next() ? rs.getString(1) : null;
    }
  }

  private void exec(String sql) throws SQLException {
    try (Statement s = conn.createStatement()) {
      s.execute(sql);
    }
  }
}
