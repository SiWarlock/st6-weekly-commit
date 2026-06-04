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
 * Proves {@code V5__seed_personas_and_relationships.sql} — the deterministic demo persona seed
 * (task 10.2; ARCHITECTURE.md Appendix E Part 2 + §4/§6/§16) — on a real PostgreSQL 16
 * (Testcontainers, never H2 — §17), against the FULL Flyway chain (V1–V5) in an <em>isolated</em>
 * per-class container so the demo seed never pollutes the shared {@code SharedPostgres} integration
 * harness (the demo seed is a runtime/demo prerequisite, NOT a test-data dependency — tests seed
 * their own employees; MVP_TASKS 3.1b note).
 *
 * <p>Mirrors {@link V1CoreSchemaMigrationTest}'s shape (raw JDBC + Flyway + ephemeral container).
 * Pins: the 7 personas (Dana MANAGER + 6 IC reports) with their fixed UUIDs /
 * {@code @dreddy817.onmicrosoft.com} emails / {@code America/Chicago} tz / {@code active} /
 * populated + distinct OAuth {@code external_subject}; the 6 active (Dana → report) relationships
 * satisfying the V2 single-active-manager partial unique with Dana herself unmanaged; idempotency
 * ({@code ON CONFLICT DO NOTHING} → a re-run inserts 0 rows); and the OAuth resolution key — {@code
 * external_subject} → the active employee row (the lookup {@code
 * EmployeeRepository.findByExternalSubject} / {@code PrincipalResolver.resolve(Auth0Identity)}
 * performs, §6 / REQ-S-007).
 *
 * <p><strong>Step-2.5 contingency:</strong> {@link #SEED_LOCATION} + {@link #SEED_RESOURCE} assume
 * the demo seed lives in a dedicated {@code classpath:db/demo-seed} location (orch recommendation
 * Opt-1 — keeps demo data out of the shared test Flyway run). If the orchestrator rules Opt-2 (V5
 * stays in {@code db/migration}), both constants collapse to {@code db/migration} — the assertions
 * are unchanged.
 */
@Testcontainers
class V5SeedPersonasMigrationTest {

  private static final String PG_SQLSTATE_UNIQUE = "23505";

  private static final String SCHEMA_LOCATION = "classpath:db/migration";
  private static final String SEED_LOCATION = "classpath:db/demo-seed";
  private static final String SEED_RESOURCE =
      "/db/demo-seed/V5__seed_personas_and_relationships.sql";

  private static final String DEMO_DOMAIN = "@dreddy817.onmicrosoft.com";

  // Fixed literal UUIDs (logical order, continuing V4's per-entity prefix convention:
  // a=RC, b=DO, c=SO → d=employee, e=relationship).
  private static final String DANA = "d0000000-0000-0000-0000-000000000001";
  private static final String R1_PRIYA = "d0000000-0000-0000-0000-000000000002";
  private static final String R2_MARCO = "d0000000-0000-0000-0000-000000000003";
  private static final String R3_AISHA = "d0000000-0000-0000-0000-000000000004";
  private static final String R4_TOMAS = "d0000000-0000-0000-0000-000000000005";
  private static final String R5_GRACE = "d0000000-0000-0000-0000-000000000006";
  private static final String R6_SAM = "d0000000-0000-0000-0000-000000000007";

  private static final String DANA_SUBJECT = "st6|dana-okafor";

  @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.13");

  private static Connection conn;

  @BeforeAll
  static void migrateAndConnect() throws SQLException {
    Flyway.configure()
        .dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
        .locations(SCHEMA_LOCATION, SEED_LOCATION) // full chain incl. the V5 demo seed
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

  // --- 1. seven personas: Dana MANAGER + 6 IC reports, attributes + external_subject -------------
  @Test
  void v5_seedsDanaAndSixReports() throws SQLException {
    // exactly 7 demo employees seeded (the @dreddy817.onmicrosoft.com namespace is the demo seed's
    // own).
    assertThat(scalarInt("select count(*) from employee where email like '%" + DEMO_DOMAIN + "'"))
        .as("7 demo personas seeded")
        .isEqualTo(7);

    // Dana — the manager, owns her own plan as IC but is unmanaged.
    assertEmployee(DANA, "MANAGER", "dana.okafor@dreddy817.onmicrosoft.com", DANA_SUBJECT);
    // The 6 IC direct reports (Appendix E Part 2 names).
    assertEmployee(R1_PRIYA, "IC", "priya.raman@dreddy817.onmicrosoft.com", "st6|priya-raman");
    assertEmployee(R2_MARCO, "IC", "marco.bellini@dreddy817.onmicrosoft.com", "st6|marco-bellini");
    assertEmployee(R3_AISHA, "IC", "aisha.khan@dreddy817.onmicrosoft.com", "st6|aisha-khan");
    assertEmployee(R4_TOMAS, "IC", "tomas.novak@dreddy817.onmicrosoft.com", "st6|tomas-novak");
    assertEmployee(R5_GRACE, "IC", "grace.liu@dreddy817.onmicrosoft.com", "st6|grace-liu");
    assertEmployee(R6_SAM, "IC", "sam.carter@dreddy817.onmicrosoft.com", "st6|sam-carter");

    // every demo persona is active, America/Chicago, and carries a non-null external_subject.
    assertThat(
            scalarInt(
                "select count(*) from employee where email like '%"
                    + DEMO_DOMAIN
                    + "' and active = true and timezone = 'America/Chicago'"
                    + " and external_subject is not null"))
        .as("all 7 active, America/Chicago, external_subject populated")
        .isEqualTo(7);

    // external_subject is 1:1 (distinct) across the 7 — the resolver assumes ≤1 match.
    assertThat(
            scalarInt(
                "select count(distinct external_subject) from employee where email like '%"
                    + DEMO_DOMAIN
                    + "'"))
        .as("external_subject distinct across the 7 personas")
        .isEqualTo(7);
  }

  // --- 2. six active (Dana → report) relationships; partial-unique holds; Dana unmanaged ---------
  @Test
  void v5_seedsSixActiveDirectReportRelationships() throws SQLException {
    // exactly 6 active relationships, all managed by Dana.
    assertThat(
            scalarInt(
                "select count(*) from manager_relationship where manager_employee_id = '"
                    + DANA
                    + "' and active = true"))
        .as("6 active Dana → report relationships")
        .isEqualTo(6);
    assertThat(scalarInt("select count(*) from manager_relationship where active = true"))
        .as("no other active relationships beyond Dana's 6")
        .isEqualTo(6);

    // each of the 6 reports is an active direct report of Dana (one row apiece).
    for (String report : new String[] {R1_PRIYA, R2_MARCO, R3_AISHA, R4_TOMAS, R5_GRACE, R6_SAM}) {
      assertThat(
              scalarInt(
                  "select count(*) from manager_relationship where manager_employee_id = '"
                      + DANA
                      + "' and direct_report_employee_id = '"
                      + report
                      + "' and active = true"))
          .as("Dana → %s active relationship present", report)
          .isEqualTo(1);
    }

    // Dana is unmanaged — she is no one's direct report.
    assertThat(
            scalarInt(
                "select count(*) from manager_relationship where direct_report_employee_id = '"
                    + DANA
                    + "'"))
        .as("Dana has no manager (unmanaged)")
        .isZero();

    // the V2 single-active-manager partial unique HOLDS: a second active manager for R1 is
    // rejected.
    PSQLException ex =
        assertThrows(
            PSQLException.class,
            () ->
                exec(
                    "insert into manager_relationship"
                        + "(id, manager_employee_id, direct_report_employee_id, active) values ('"
                        + "e0000000-0000-0000-0000-0000000000ff','"
                        + R2_MARCO // any other manager
                        + "','"
                        + R1_PRIYA // already actively managed by Dana
                        + "', true)"));
    assertThat(ex.getSQLState())
        .as("uq_active_manager_per_report rejects a second active manager for a report")
        .isEqualTo(PG_SQLSTATE_UNIQUE);
  }

  // --- 3. idempotent: applying the V5 seed script a second time inserts 0 new rows ---------------
  @Test
  void v5_idempotentReRun() throws SQLException, IOException {
    int employeesBefore = scalarInt("select count(*) from employee");
    int relationshipsBefore = scalarInt("select count(*) from manager_relationship");

    // re-execute the entire V5 seed script against the already-migrated DB (ON CONFLICT DO
    // NOTHING).
    exec(readSeedScript());

    assertThat(scalarInt("select count(*) from employee"))
        .as("re-running V5 inserts no duplicate employees")
        .isEqualTo(employeesBefore);
    assertThat(scalarInt("select count(*) from manager_relationship"))
        .as("re-running V5 inserts no duplicate relationships")
        .isEqualTo(relationshipsBefore);
  }

  // --- 4. OAuth resolution key: external_subject → the active employee (the load-bearing pin)
  // -----
  @Test
  void v5_externalSubjectResolvesToEmployee() throws SQLException {
    // mirrors EmployeeRepository.findByExternalSubject + PrincipalResolver's .filter(active):
    // a seeded external_subject literal resolves to exactly one active employee (Dana).
    try (Statement s = conn.createStatement();
        ResultSet rs =
            s.executeQuery(
                "select id, role, active from employee where external_subject = '"
                    + DANA_SUBJECT
                    + "'")) {
      assertThat(rs.next()).as("Dana's external_subject resolves to a row").isTrue();
      assertThat(rs.getString("id")).isEqualTo(DANA);
      assertThat(rs.getString("role")).isEqualTo("MANAGER");
      assertThat(rs.getBoolean("active")).isTrue();
      assertThat(rs.next()).as("external_subject is 1:1 — no second match").isFalse();
    }

    // an unseeded subject resolves to nothing (the resolver returns Optional.empty → 401).
    assertThat(scalarInt("select count(*) from employee where external_subject = 'st6|nobody'"))
        .as("an unseeded external_subject resolves to no employee")
        .isZero();
  }

  // --- helpers -----------------------------------------------------------------------------------

  private void assertEmployee(String id, String role, String email, String externalSubject)
      throws SQLException {
    try (Statement s = conn.createStatement();
        ResultSet rs =
            s.executeQuery(
                "select role, email, external_subject, active, timezone from employee where id = '"
                    + id
                    + "'")) {
      assertThat(rs.next()).as("employee %s present", id).isTrue();
      assertThat(rs.getString("role")).as("%s role", id).isEqualTo(role);
      assertThat(rs.getString("email")).as("%s email", id).isEqualTo(email);
      assertThat(rs.getString("external_subject"))
          .as("%s external_subject", id)
          .isEqualTo(externalSubject);
      assertThat(rs.getBoolean("active")).as("%s active", id).isTrue();
      assertThat(rs.getString("timezone")).as("%s timezone", id).isEqualTo("America/Chicago");
    }
  }

  private static String readSeedScript() throws IOException {
    try (InputStream in = V5SeedPersonasMigrationTest.class.getResourceAsStream(SEED_RESOURCE)) {
      if (in == null) {
        throw new IOException("V5 seed script not found on classpath: " + SEED_RESOURCE);
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

  private void exec(String sql) throws SQLException {
    try (Statement s = conn.createStatement()) {
      s.execute(sql);
    }
  }
}
