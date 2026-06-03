package com.st6.wc.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.st6.wc.enums.RiskBadge;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
 * Proves {@code V3__projection_tables.sql} (after V1/V2) on a real PostgreSQL 16 (reuses the
 * 1.2/1.3 harness, LESSONS §5). The 2 §9 synchronous projection read-models (manager_plan_summary,
 * manager_heatmap_cell) — count columns, the {@code risk_badges text[]} enumerated vocabulary
 * (DB-CHECK via array containment), uniques, FKs. DDL only — population is the ProjectionService
 * phase. {@code is_review_overdue} here is just the §9 read-model column (NOT safety-rule-#6).
 */
@Testcontainers
class V3ProjectionTablesTest {

  private static final String PG_SQLSTATE_CHECK = "23514";
  private static final String PG_SQLSTATE_FK = "23503";
  private static final String PG_SQLSTATE_UNIQUE = "23505";

  @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.13");

  private static Connection conn;

  @BeforeAll
  static void migrateAndConnect() throws SQLException {
    Flyway.configure()
        .dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("3")) // validate the cumulative V1+V2+V3 state
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

  // --- 1. V3 migrates clean after V1/V2; both tables + risk_badges text[] ----
  @Test
  void v3_migrates_clean_after_v1_v2() throws SQLException {
    for (String col :
        new String[] {
          "planned_count",
          "unplanned_count",
          "misaligned_count",
          "needs_review_count",
          "blocked_count",
          "carry_forward_count",
          "unresolved_dispute_count"
        }) {
      assertThat(columnExists("manager_plan_summary", col)).as("summary.%s", col).isTrue();
      assertThat(columnExists("manager_heatmap_cell", col)).as("heatmap.%s", col).isTrue();
    }
    assertThat(columnExists("manager_plan_summary", "is_review_overdue")).isTrue();
    assertThat(columnExists("manager_heatmap_cell", "commitment_count")).isTrue();
    // risk_badges is a Postgres array (text[])
    assertThat(columnDataType("manager_heatmap_cell", "risk_badges")).isEqualTo("ARRAY");
  }

  // --- 2. risk_badges vocabulary (round-trip + default + reject out-of-vocab) -
  @Test
  void risk_badges_vocabulary() throws SQLException {
    UUID mgr = insertEmployee();
    UUID emp = insertEmployee();
    UUID dobj = insertDefiningObjective();
    // valid array round-trips
    insertHeatmap(mgr, emp, "2026-09-07", dobj, "'{MISALIGNED,OVERDUE_REVIEW}'");
    // empty defaults to '{}'
    UUID emp2 = insertEmployee();
    insertHeatmap(mgr, emp2, "2026-09-07", dobj, "default");
    try (Statement s = conn.createStatement();
        ResultSet rs =
            s.executeQuery(
                "select array_length(risk_badges,1) from manager_heatmap_cell where employee_id='"
                    + emp2
                    + "'")) {
      rs.next();
      assertThat(rs.getObject(1)).as("empty default risk_badges").isNull();
    }
    // out-of-vocab element rejected
    UUID emp3 = insertEmployee();
    assertSqlState(
        () -> insertHeatmap(mgr, emp3, "2026-09-07", dobj, "'{NOT_A_BADGE}'"), PG_SQLSTATE_CHECK);
  }

  // --- 3. summary unique (manager, employee, week) --------------------------
  @Test
  void summary_unique() throws SQLException {
    UUID mgr = insertEmployee();
    UUID emp = insertEmployee();
    UUID plan = insertPlan(emp, "2026-09-14");
    insertSummary(mgr, emp, plan, "2026-09-14");
    assertSqlState(() -> insertSummary(mgr, emp, plan, "2026-09-14"), PG_SQLSTATE_UNIQUE);
  }

  // --- 4. heatmap unique (manager, employee, week, defining_objective) ------
  @Test
  void heatmap_unique() throws SQLException {
    UUID mgr = insertEmployee();
    UUID emp = insertEmployee();
    UUID dobj = insertDefiningObjective();
    insertHeatmap(mgr, emp, "2026-09-21", dobj, "default");
    assertSqlState(
        () -> insertHeatmap(mgr, emp, "2026-09-21", dobj, "default"), PG_SQLSTATE_UNIQUE);
  }

  // --- 5. projection FKs ----------------------------------------------------
  @Test
  void projection_fks() throws SQLException {
    UUID mgr = insertEmployee();
    UUID emp = insertEmployee();
    // summary with a non-existent weekly_plan_id
    assertSqlState(() -> insertSummary(mgr, emp, UUID.randomUUID(), "2026-09-28"), PG_SQLSTATE_FK);
    // heatmap with a non-existent defining_objective_id
    assertSqlState(
        () -> insertHeatmap(mgr, emp, "2026-09-28", UUID.randomUUID(), "default"), PG_SQLSTATE_FK);
  }

  // --- 6. risk_badges CHECK <-> RiskBadge.values() (array-vocab pin) ---------
  @Test
  void risk_badges_check_matches_riskbadge_values() throws SQLException {
    Set<String> allowed = new LinkedHashSet<>();
    String sql =
        "select pg_get_constraintdef(c.oid) from pg_constraint c "
            + "join pg_class t on t.oid=c.conrelid "
            + "where t.relname='manager_heatmap_cell' and c.contype='c' "
            + "and pg_get_constraintdef(c.oid) like '%risk_badges%'";
    Pattern quoted = Pattern.compile("'([A-Z][A-Z0-9_]*)'");
    try (Statement s = conn.createStatement();
        ResultSet rs = s.executeQuery(sql)) {
      while (rs.next()) {
        Matcher m = quoted.matcher(rs.getString(1));
        while (m.find()) {
          allowed.add(m.group(1));
        }
      }
    }
    Set<String> expected =
        Arrays.stream(RiskBadge.values())
            .map(Enum::name)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    assertThat(allowed).as("risk_badges CHECK must equal RiskBadge.values()").isEqualTo(expected);
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

  private UUID insertDefiningObjective() throws SQLException {
    UUID rc = UUID.randomUUID();
    exec("insert into rally_cry(id,title) values ('" + rc + "','RC')");
    UUID dobj = UUID.randomUUID();
    exec(
        "insert into defining_objective(id,rally_cry_id,title) values ('"
            + dobj
            + "','"
            + rc
            + "','DO')");
    return dobj;
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

  private void insertSummary(UUID mgr, UUID emp, UUID plan, String week) throws SQLException {
    exec(
        "insert into manager_plan_summary"
            + "(id,manager_employee_id,employee_id,weekly_plan_id,week_start_date,plan_state,updated_at)"
            + " values ('"
            + UUID.randomUUID()
            + "','"
            + mgr
            + "','"
            + emp
            + "','"
            + plan
            + "','"
            + week
            + "','LOCKED', now())");
  }

  private void insertHeatmap(UUID mgr, UUID emp, String week, UUID dobj, String riskBadges)
      throws SQLException {
    String badgeCol = "default".equals(riskBadges) ? "" : ",risk_badges";
    String badgeVal = "default".equals(riskBadges) ? "" : "," + riskBadges;
    exec(
        "insert into manager_heatmap_cell"
            + "(id,manager_employee_id,employee_id,week_start_date,defining_objective_id,updated_at"
            + badgeCol
            + ") values ('"
            + UUID.randomUUID()
            + "','"
            + mgr
            + "','"
            + emp
            + "','"
            + week
            + "','"
            + dobj
            + "', now()"
            + badgeVal
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

  private void assertSqlState(SqlRunnable r, String expected) {
    PSQLException ex = assertThrows(PSQLException.class, r::run);
    assertThat(ex.getSQLState()).isEqualTo(expected);
  }

  private boolean columnExists(String table, String column) throws SQLException {
    try (Statement s = conn.createStatement();
        ResultSet rs =
            s.executeQuery(
                "select exists(select 1 from information_schema.columns where table_name='"
                    + table
                    + "' and column_name='"
                    + column
                    + "')")) {
      return rs.next() && rs.getBoolean(1);
    }
  }

  private String columnDataType(String table, String column) throws SQLException {
    try (Statement s = conn.createStatement();
        ResultSet rs =
            s.executeQuery(
                "select data_type from information_schema.columns where table_name='"
                    + table
                    + "' and column_name='"
                    + column
                    + "'")) {
      return rs.next() ? rs.getString(1) : "";
    }
  }
}
