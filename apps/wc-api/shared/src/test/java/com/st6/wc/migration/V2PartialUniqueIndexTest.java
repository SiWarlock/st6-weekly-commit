package com.st6.wc.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves {@code V2__partial_unique_indexes.sql} (after V1) on a real PostgreSQL 16 (reuses the 1.2
 * Flyway+Testcontainers harness, LESSONS §5). The 3 partial uniques back single-row safety
 * invariants — single active manager per report (§6), one unresolved dispute per commitment (safety
 * rule #6), one review-block per manager/week (§10). Each test asserts BOTH the firing (23505) and
 * the non-firing/partial-scope case — the latter is the whole point of a partial index.
 */
@Testcontainers
class V2PartialUniqueIndexTest {

  private static final String PG_SQLSTATE_UNIQUE = "23505";

  @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.13");

  private static Connection conn;

  @BeforeAll
  static void migrateAndConnect() throws SQLException {
    Flyway.configure()
        .dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("2")) // validate the cumulative V1+V2 state
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

  // --- 1. V2 migrates clean after V1; the 3 partial indexes exist -----------
  @Test
  void v2_migrates_clean_after_v1() throws SQLException {
    assertThat(partialIndexDef("uq_active_manager_per_report"))
        .contains("active")
        .contains("WHERE");
    assertThat(partialIndexDef("uq_one_unresolved_dispute_per_commitment"))
        .contains("OPEN")
        .contains("IC_RESPONDED");
    assertThat(partialIndexDef("uq_one_review_block_per_manager_week"))
        .contains("MANAGER_REVIEW_BLOCK");
  }

  // --- 2. single active manager per report (partial WHERE active=true) ------
  @Test
  void single_active_manager() throws SQLException {
    UUID m1 = insertEmployee();
    UUID m2 = insertEmployee();
    UUID reportA = insertEmployee();
    UUID reportB = insertEmployee();
    // firing: two ACTIVE relationships for the same direct report
    insertRelationship(m1, reportA, true);
    assertUniqueViolation(() -> insertRelationship(m2, reportA, true));
    // non-firing: an inactive first relationship does not block a new active one (partial scope)
    insertRelationship(m1, reportB, false);
    insertRelationship(m2, reportB, true);
  }

  // --- 3. one unresolved dispute per commitment (WHERE status in OPEN/IC_RESPONDED) -
  @Test
  void one_unresolved_dispute() throws SQLException {
    UUID mgr = insertEmployee();
    UUID commitmentC = insertCommitment(insertPlan(insertEmployee(), "2026-08-03"));
    UUID commitmentD = insertCommitment(insertPlan(insertEmployee(), "2026-08-10"));
    // firing: a 2nd unresolved dispute on the same commitment
    insertDispute(commitmentC, mgr, "OPEN");
    assertUniqueViolation(() -> insertDispute(commitmentC, mgr, "IC_RESPONDED"));
    // non-firing: a RESOLVED dispute does not block a new OPEN one (partial scope)
    insertDispute(commitmentD, mgr, "RESOLVED");
    insertDispute(commitmentD, mgr, "OPEN");
  }

  // --- 4. one review-block per manager/week (WHERE event_kind=MANAGER_REVIEW_BLOCK) -
  @Test
  void one_review_block_per_manager_week() throws SQLException {
    UUID mgr = insertEmployee();
    String week = "2026-08-17";
    // firing: a 2nd MANAGER_REVIEW_BLOCK for the same (owner, week) — distinct related_id so the V1
    // full unique does NOT fire first; the V2 partial (owner, week) is what fires.
    insertReviewBlock(mgr, week, UUID.randomUUID());
    assertUniqueViolation(() -> insertReviewBlock(mgr, week, UUID.randomUUID()));
    // non-firing: IC_PLANNING / IC_RECONCILIATION for the same owner/week are not constrained
    insertSync(mgr, "WEEKLY_PLAN", UUID.randomUUID(), "IC_PLANNING", week);
    insertSync(mgr, "WEEKLY_PLAN", UUID.randomUUID(), "IC_RECONCILIATION", week);
  }

  // --- 5. the V1 full sync unique still holds (V2 didn't disturb it) ---------
  @Test
  void v1_full_sync_unique_still_holds() throws SQLException {
    UUID owner = insertEmployee();
    UUID relatedId = UUID.randomUUID();
    insertSync(owner, "WEEKLY_PLAN", relatedId, "IC_PLANNING", null);
    // same (owner, related_type, related_id, event_kind) → V1 full unique violation
    assertUniqueViolation(() -> insertSync(owner, "WEEKLY_PLAN", relatedId, "IC_PLANNING", null));
  }

  // --- helpers --------------------------------------------------------------
  private UUID insertEmployee() throws SQLException {
    UUID id = UUID.randomUUID();
    exec(
        "insert into employee(id,email,display_name,role) values ('"
            + id
            + "','"
            + id
            + "@x.test','N','MANAGER')");
    return id;
  }

  private UUID insertPlan(UUID emp, String week) throws SQLException {
    UUID id = UUID.randomUUID();
    exec(
        "insert into weekly_plan(id,employee_id,week_start_date,week_end_date,state) values ('"
            + id
            + "','"
            + emp
            + "','"
            + week
            + "','"
            + week
            + "','DRAFT')");
    return id;
  }

  private UUID insertCommitment(UUID plan) throws SQLException {
    UUID id = UUID.randomUUID();
    exec(
        "insert into weekly_commitment"
            + "(id,weekly_plan_id,commitment_kind,title,priority,work_type,confidence,alignment_status)"
            + " values ('"
            + id
            + "','"
            + plan
            + "','PLANNED','t','P1','STRATEGIC','HIGH','ALIGNED')");
    return id;
  }

  private void insertRelationship(UUID mgr, UUID report, boolean active) throws SQLException {
    exec(
        "insert into manager_relationship"
            + "(id,manager_employee_id,direct_report_employee_id,active) values ('"
            + UUID.randomUUID()
            + "','"
            + mgr
            + "','"
            + report
            + "',"
            + active
            + ")");
  }

  private void insertDispute(UUID commitment, UUID mgr, String status) throws SQLException {
    exec(
        "insert into alignment_dispute"
            + "(id,commitment_id,manager_employee_id,status,flag_type,manager_note) values ('"
            + UUID.randomUUID()
            + "','"
            + commitment
            + "','"
            + mgr
            + "','"
            + status
            + "','MISALIGNED','n')");
  }

  private void insertReviewBlock(UUID owner, String week, UUID relatedId) throws SQLException {
    insertSyncFull(owner, "MANAGER_REVIEW_WEEK", relatedId, "MANAGER_REVIEW_BLOCK", week);
  }

  private void insertSync(
      UUID owner, String relatedType, UUID relatedId, String eventKind, String week)
      throws SQLException {
    insertSyncFull(owner, relatedType, relatedId, eventKind, week);
  }

  private void insertSyncFull(
      UUID owner, String relatedType, UUID relatedId, String eventKind, String week)
      throws SQLException {
    String weekCol = week == null ? "" : ",week_start_date";
    String weekVal = week == null ? "" : ",'" + week + "'";
    exec(
        "insert into outlook_calendar_sync_record"
            + "(id,owner_employee_id,related_type,related_id,event_kind,status"
            + weekCol
            + ")"
            + " values ('"
            + UUID.randomUUID()
            + "','"
            + owner
            + "','"
            + relatedType
            + "','"
            + relatedId
            + "','"
            + eventKind
            + "','PENDING_PUBLISH'"
            + weekVal
            + ")");
  }

  private void exec(String sql) throws SQLException {
    try (Statement s = conn.createStatement()) {
      s.executeUpdate(sql);
    }
  }

  private interface SqlRunnable {
    void run() throws SQLException;
  }

  private void assertUniqueViolation(SqlRunnable r) {
    PSQLException ex = assertThrows(PSQLException.class, r::run);
    assertThat(ex.getSQLState()).isEqualTo(PG_SQLSTATE_UNIQUE);
  }

  private String partialIndexDef(String indexName) throws SQLException {
    try (Statement s = conn.createStatement();
        ResultSet rs = s.executeQuery("select pg_get_indexdef('" + indexName + "'::regclass)")) {
      return rs.next() ? rs.getString(1) : "";
    }
  }
}
