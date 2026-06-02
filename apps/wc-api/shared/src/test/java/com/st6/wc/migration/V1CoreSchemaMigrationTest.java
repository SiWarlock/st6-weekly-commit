package com.st6.wc.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommentTargetType;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.DisputeStatus;
import com.st6.wc.enums.EventKind;
import com.st6.wc.enums.FlagType;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.ReconciliationOutcome;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.SyncRelatedType;
import com.st6.wc.enums.SyncStatus;
import com.st6.wc.enums.WorkType;
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
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.postgresql.util.PSQLException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves {@code V1__core_schema.sql} on a real PostgreSQL 16 (Testcontainers, never H2 — §17): all
 * 12 core tables migrate clean; status columns are VARCHAR+CHECK over the exact 0.3 enum sets
 * (REQ-D-010); the 4 contract deltas hold; unique/FK/CHECK constraints reject violations; the
 * SYSTEM (null actor) audit path works; and every CHECK allowed-set equals its enum.values()
 * (LESSONS §3 extended to the DB layer — enum↔CHECK can't silently drift).
 */
@Testcontainers
class V1CoreSchemaMigrationTest {

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
        .target(MigrationVersion.fromVersion("1")) // validate the V1 schema state specifically
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

  // --- 1. all 12 tables migrate clean ---------------------------------------
  @Test
  void v1_migrates_clean_all_tables() throws SQLException {
    Set<String> tables = new LinkedHashSet<>();
    try (Statement s = conn.createStatement();
        ResultSet rs =
            s.executeQuery(
                "select table_name from information_schema.tables where table_schema='public'")) {
      while (rs.next()) {
        tables.add(rs.getString(1));
      }
    }
    assertThat(tables)
        .contains(
            "employee",
            "manager_relationship",
            "rally_cry",
            "defining_objective",
            "supporting_outcome",
            "weekly_plan",
            "weekly_commitment",
            "manager_review",
            "alignment_dispute",
            "comment",
            "outlook_calendar_sync_record",
            "audit_event");
    // Out of scope for V1: projection tables (1.4/V3).
    assertThat(tables).doesNotContain("manager_plan_summary", "manager_heatmap_cell");
  }

  // --- 2. CHECK constraints reject out-of-vocabulary values -----------------
  @Test
  void check_constraints_reject_out_of_vocab() throws SQLException {
    UUID emp = insertEmployee();
    UUID plan = insertPlan(emp, "2026-06-01", PlanState.DRAFT.name());
    assertCheckViolation(
        "insert into weekly_plan(id,employee_id,week_start_date,week_end_date,state) values ('"
            + UUID.randomUUID()
            + "','"
            + emp
            + "','2026-06-08','2026-06-14','ARCHIVED')");
    assertCheckViolation(
        "insert into comment(id,target_type,target_id,author_employee_id,body) values ('"
            + UUID.randomUUID()
            + "','MANAGER_REVIEW','"
            + UUID.randomUUID()
            + "','"
            + emp
            + "','x')");
    assertCheckViolation(
        "insert into manager_review(id,weekly_plan_id,manager_employee_id,status,review_due_at)"
            + " values ('"
            + UUID.randomUUID()
            + "','"
            + plan
            + "','"
            + emp
            + "','OVERDUE', now())");
    assertCheckViolation(
        "insert into outlook_calendar_sync_record"
            + "(id,owner_employee_id,related_type,related_id,event_kind,status) values ('"
            + UUID.randomUUID()
            + "','"
            + emp
            + "','MANAGER_REVIEW','"
            + UUID.randomUUID()
            + "','IC_PLANNING','PENDING_PUBLISH')");
  }

  // --- 3. the 4 contract deltas ---------------------------------------------
  @Test
  void contract_deltas() throws SQLException {
    assertThat(columnExists("weekly_commitment", "manager_alignment_note")).isTrue(); // delta 1
    assertThat(columnExists("weekly_commitment", "progress_status")).isFalse(); // delta 2
    assertThat(columnDefault("comment", "depth")).contains("0"); // delta 3
    assertThat(columnNullable("comment", "parent_comment_id")).isTrue();
    assertThat(columnNullable("comment", "path")).isTrue();
    assertThat(columnExists("outlook_calendar_sync_record", "week_start_date")).isTrue(); // delta 4
    // delta 4: MANAGER_REVIEW_WEEK is accepted
    UUID emp = insertEmployee();
    exec(
        "insert into outlook_calendar_sync_record"
            + "(id,owner_employee_id,related_type,related_id,event_kind,status,week_start_date)"
            + " values ('"
            + UUID.randomUUID()
            + "','"
            + emp
            + "','MANAGER_REVIEW_WEEK','"
            + emp
            + "','MANAGER_REVIEW_BLOCK','PENDING_PUBLISH','2026-06-01')");
  }

  // --- 4. unique + FK + cross-column CHECK violations -----------------------
  @Test
  void unique_and_fk_violations() throws SQLException {
    UUID emp = insertEmployee();
    insertPlan(emp, "2026-07-06", PlanState.DRAFT.name());
    assertSqlState(
        "insert into weekly_plan(id,employee_id,week_start_date,week_end_date,state) values ('"
            + UUID.randomUUID()
            + "','"
            + emp
            + "','2026-07-06','2026-07-12','DRAFT')",
        PG_SQLSTATE_UNIQUE); // duplicate (employee_id, week_start_date)
    UUID plan = insertPlan(emp, "2026-07-13", PlanState.DRAFT.name());
    assertSqlState(
        "insert into weekly_commitment"
            + "(id,weekly_plan_id,commitment_kind,title,priority,work_type,confidence,"
            + "alignment_status,supporting_outcome_id) values ('"
            + UUID.randomUUID()
            + "','"
            + plan
            + "','PLANNED','t','P1','STRATEGIC','HIGH','ALIGNED','"
            + UUID.randomUUID()
            + "')",
        PG_SQLSTATE_FK); // non-existent supporting_outcome_id
    assertSqlState(
        "insert into manager_relationship(id,manager_employee_id,direct_report_employee_id)"
            + " values ('"
            + UUID.randomUUID()
            + "','"
            + emp
            + "','"
            + emp
            + "')",
        PG_SQLSTATE_CHECK); // manager == report
  }

  // --- 5. SYSTEM actor (null) audit insert ----------------------------------
  @Test
  void system_actor_null_insert() throws SQLException {
    exec(
        "insert into audit_event(id,actor_employee_id,action,entity_type,summary,created_at)"
            + " values ('"
            + UUID.randomUUID()
            + "',null,'PLAN_SHELL_GENERATED','weekly_plan',"
            + "'system action', now())");
  }

  // --- 6. enum <-> CHECK alignment (every status column) --------------------
  static Stream<Arguments> statusColumns() {
    return Stream.of(
        Arguments.of("employee", "role", names(RoleType.class)),
        Arguments.of("weekly_plan", "state", names(PlanState.class)),
        Arguments.of("weekly_commitment", "commitment_kind", names(CommitmentKind.class)),
        Arguments.of("weekly_commitment", "priority", names(Priority.class)),
        Arguments.of("weekly_commitment", "work_type", names(WorkType.class)),
        Arguments.of("weekly_commitment", "confidence", names(Confidence.class)),
        Arguments.of("weekly_commitment", "alignment_status", names(AlignmentStatus.class)),
        Arguments.of(
            "weekly_commitment", "reconciliation_outcome", names(ReconciliationOutcome.class)),
        Arguments.of("manager_review", "status", names(ReviewStatus.class)),
        Arguments.of("alignment_dispute", "status", names(DisputeStatus.class)),
        Arguments.of("alignment_dispute", "flag_type", names(FlagType.class)),
        Arguments.of("comment", "target_type", names(CommentTargetType.class)),
        Arguments.of("outlook_calendar_sync_record", "related_type", names(SyncRelatedType.class)),
        Arguments.of("outlook_calendar_sync_record", "event_kind", names(EventKind.class)),
        Arguments.of("outlook_calendar_sync_record", "status", names(SyncStatus.class)));
  }

  @ParameterizedTest(name = "{0}.{1} CHECK == enum values")
  @MethodSource("statusColumns")
  void enum_check_matches_enum_values(String table, String column, Set<String> expected)
      throws SQLException {
    Set<String> allowed = checkAllowedValues(table, column);
    assertThat(allowed)
        .as("%s.%s CHECK allowed-set must equal the enum value set", table, column)
        .isEqualTo(expected);
  }

  // --- helpers --------------------------------------------------------------
  private static Set<String> names(Class<? extends Enum<?>> e) {
    return Arrays.stream(e.getEnumConstants())
        .map(Enum::name)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static final Pattern QUOTED = Pattern.compile("'([A-Z][A-Z0-9_]*)'");

  /** Reads the column's CHECK constraint def from the catalog and extracts the allowed literals. */
  private Set<String> checkAllowedValues(String table, String column) throws SQLException {
    String sql =
        "select pg_get_constraintdef(c.oid) from pg_constraint c "
            + "join pg_class t on t.oid=c.conrelid "
            + "where t.relname='"
            + table
            + "' and c.contype='c' "
            + "and pg_get_constraintdef(c.oid) like '%"
            + column
            + "%'";
    Set<String> values = new LinkedHashSet<>();
    try (Statement s = conn.createStatement();
        ResultSet rs = s.executeQuery(sql)) {
      while (rs.next()) {
        Matcher m = QUOTED.matcher(rs.getString(1));
        while (m.find()) {
          values.add(m.group(1));
        }
      }
    }
    return values;
  }

  private UUID insertEmployee() throws SQLException {
    UUID id = UUID.randomUUID();
    exec(
        "insert into employee(id,email,display_name,role) values ('"
            + id
            + "','"
            + id
            + "@x.test','N','IC')");
    return id;
  }

  private UUID insertPlan(UUID emp, String weekStart, String state) throws SQLException {
    UUID id = UUID.randomUUID();
    exec(
        "insert into weekly_plan(id,employee_id,week_start_date,week_end_date,state) values ('"
            + id
            + "','"
            + emp
            + "','"
            + weekStart
            + "','"
            + weekStart
            + "','"
            + state
            + "')");
    return id;
  }

  private void exec(String sql) throws SQLException {
    try (Statement s = conn.createStatement()) {
      s.executeUpdate(sql);
    }
  }

  private void assertCheckViolation(String sql) {
    assertSqlState(sql, PG_SQLSTATE_CHECK);
  }

  private void assertSqlState(String sql, String expectedState) {
    PSQLException ex = assertThrows(PSQLException.class, () -> exec(sql));
    assertThat(ex.getSQLState()).as("SQLState for: %s", sql).isEqualTo(expectedState);
  }

  private boolean columnExists(String table, String column) throws SQLException {
    return queryBool(
        "select exists(select 1 from information_schema.columns where table_name='"
            + table
            + "' and column_name='"
            + column
            + "')");
  }

  private boolean columnNullable(String table, String column) throws SQLException {
    return queryBool(
        "select is_nullable='YES' from information_schema.columns where table_name='"
            + table
            + "' and column_name='"
            + column
            + "'");
  }

  private String columnDefault(String table, String column) throws SQLException {
    try (Statement s = conn.createStatement();
        ResultSet rs =
            s.executeQuery(
                "select coalesce(column_default,'') from information_schema.columns where"
                    + " table_name='"
                    + table
                    + "' and column_name='"
                    + column
                    + "'")) {
      return rs.next() ? rs.getString(1) : "";
    }
  }

  private boolean queryBool(String sql) throws SQLException {
    try (Statement s = conn.createStatement();
        ResultSet rs = s.executeQuery(sql)) {
      return rs.next() && rs.getBoolean(1);
    }
  }
}
