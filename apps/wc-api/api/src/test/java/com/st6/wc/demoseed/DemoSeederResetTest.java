package com.st6.wc.demoseed;

import static org.assertj.core.api.Assertions.assertThat;

import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.ProjectionRefresher;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The FK-safe reset half of the week-parameterized demo-seed Job (brief 107a, Appendix E Part 2,
 * §3/§9/§10). Boots the full {@code :api} context against an <em>isolated</em> per-class PG16
 * container migrated through the FULL Flyway chain V1–V6 (both {@code db/migration} + {@code
 * db/demo-seed} — §41 posture, so the demo fixtures never pollute the shared harness); a
 * {@code @BeforeEach} restores the V6 fixture deterministically so each destructive test starts
 * from the same matrix.
 *
 * <p>Proves {@link DemoSeeder#run(LocalDate)} (107a scope: reset only) normalizes its arg to the
 * week's Monday, then clears the 7 personas' derived rows across the <strong>two-week footprint {W,
 * W−7}</strong> in FK-safe order — leaving identity (employees/relationships) and reference (RCDO)
 * data untouched. The seed half (the matrix re-insert) is brief 107b.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@Testcontainers
class DemoSeederResetTest {

  @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.13");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PG::getJdbcUrl);
    registry.add("spring.datasource.username", PG::getUsername);
    registry.add("spring.datasource.password", PG::getPassword);
    registry.add("spring.flyway.enabled", () -> "true");
    // Full chain incl. V5 personas + V6 fixtures — the realistic "dirty" state the reset must
    // clear.
    registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/demo-seed");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add("spring.datasource.hikari.maximum-pool-size", () -> "2");
  }

  // Personas (V5). Dana the manager + 6 IC reports.
  private static final UUID DANA = UUID.fromString("d0000000-0000-0000-0000-000000000001");
  private static final UUID R1_PRIYA = UUID.fromString("d0000000-0000-0000-0000-000000000002");
  private static final UUID R5_GRACE = UUID.fromString("d0000000-0000-0000-0000-000000000006");
  private static final List<UUID> PERSONAS =
      List.of(
          DANA,
          R1_PRIYA,
          UUID.fromString("d0000000-0000-0000-0000-000000000003"),
          UUID.fromString("d0000000-0000-0000-0000-000000000004"),
          UUID.fromString("d0000000-0000-0000-0000-000000000005"),
          R5_GRACE,
          UUID.fromString("d0000000-0000-0000-0000-000000000007"));

  private static final LocalDate W = LocalDate.of(2026, 6, 1); // V6 current week (Mon)
  private static final LocalDate W_PRIOR = LocalDate.of(2026, 5, 25); // V6 prior week (W−7)
  private static final String V6_SEED = "/db/demo-seed/V6__seed_fixture_plans_and_state.sql";

  @Autowired private DemoSeeder seeder;
  @Autowired private ProjectionRefresher refresher;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private JdbcTemplate jdbc;

  /**
   * Restore the V6 fixture deterministically before each destructive test (identity is preserved by
   * the reset, so only derived data is wiped + re-applied).
   */
  @BeforeEach
  void restoreFixture() throws IOException {
    jdbc.execute("delete from alignment_dispute");
    jdbc.execute("delete from manager_review");
    jdbc.execute("delete from manager_heatmap_cell");
    jdbc.execute("delete from manager_plan_summary");
    jdbc.execute("delete from outlook_calendar_sync_record");
    jdbc.execute("delete from weekly_commitment");
    jdbc.execute("delete from weekly_plan");
    jdbc.execute(readResource(V6_SEED)); // ON CONFLICT DO NOTHING → restores the wiped rows
  }

  // --- normalization: a mid-week arg resolves to its Monday before the footprint is computed
  // ------
  @Test
  void normalizesWeekArgToMonday() {
    // run(Wed 2026-06-10) normalizes to Monday 2026-06-08 → footprint {2026-06-08, 2026-06-01}.
    // So V6's 2026-06-01 rows (= 06-08's W−7) are cleared, while 2026-05-25 (NOT in the 06-08
    // footprint) survives — a discriminating proof that 06-10 mapped to 06-08, not to itself.
    seeder.run(LocalDate.of(2026, 6, 10));

    assertThat(planCountAt(W)).as("06-01 cleared (the W−7 of the normalized 06-08 week)").isZero();
    assertThat(planCountAt(W_PRIOR))
        .as("05-25 untouched (not in the 06-08 footprint)")
        .isPositive();
  }

  // --- the whole {W, W−7} footprint is cleared in FK-safe order ---------------------------------
  @Test
  void resetClearsTargetWeekFootprint() {
    // Populate projections from source first so the projection-delete branch is meaningfully tested
    // (V6 hand-seeds none): recompute a couple of plans → real manager_plan_summary rows.
    WeeklyPlan priya = plans.findByEmployeeIdAndWeekStartDate(R1_PRIYA, W).orElseThrow();
    WeeklyPlan grace = plans.findByEmployeeIdAndWeekStartDate(R5_GRACE, W).orElseThrow();
    refresher.recomputeForPlan(priya);
    refresher.recomputeForPlan(grace);
    assertThat(scalarInt("select count(*) from manager_plan_summary"))
        .as("projections populated before reset")
        .isPositive();

    seeder.run(W); // footprint {2026-06-01, 2026-05-25}

    assertThat(planCountAt(W)).as("W plans gone").isZero();
    assertThat(planCountAt(W_PRIOR)).as("W−7 plans gone").isZero();
    assertThat(commitmentCountAt(W)).as("W commitments gone").isZero();
    assertThat(commitmentCountAt(W_PRIOR)).as("W−7 commitments gone").isZero();
    assertThat(reviewCountAt(W)).as("W reviews gone").isZero();
    assertThat(reviewCountAt(W_PRIOR)).as("W−7 reviews gone").isZero();
    assertThat(disputeCountAt(W)).as("W disputes gone").isZero();
    assertThat(disputeCountAt(W_PRIOR)).as("W−7 disputes gone").isZero();
    assertThat(syncCountAt(W)).as("W sync records gone").isZero();
    assertThat(syncCountAt(W_PRIOR)).as("W−7 sync records gone").isZero();
    assertThat(scalarInt("select count(*) from manager_plan_summary"))
        .as("projection summaries gone")
        .isZero();
    assertThat(scalarInt("select count(*) from manager_heatmap_cell"))
        .as("projection cells gone")
        .isZero();
  }

  // --- the reset clears DERIVED data only — never identity or reference data --------------------
  @Test
  void resetLeavesIdentityAndOtherDataIntact() {
    seeder.run(W);

    assertThat(scalarInt("select count(*) from employee")).as("7 personas intact").isEqualTo(7);
    assertThat(scalarInt("select count(*) from manager_relationship"))
        .as("6 manager relationships intact")
        .isEqualTo(6);
    assertThat(scalarInt("select count(*) from supporting_outcome"))
        .as("V4 RCDO reference data intact")
        .isPositive();
  }

  // ===== helpers =====

  private int planCountAt(LocalDate week) {
    return scalarInt(
        "select count(*) from weekly_plan where week_start_date = '"
            + week
            + "' and employee_id in ("
            + personaList()
            + ")");
  }

  private int commitmentCountAt(LocalDate week) {
    return scalarInt(
        "select count(*) from weekly_commitment c join weekly_plan p on p.id = c.weekly_plan_id"
            + " where p.week_start_date = '"
            + week
            + "' and p.employee_id in ("
            + personaList()
            + ")");
  }

  private int reviewCountAt(LocalDate week) {
    return scalarInt(
        "select count(*) from manager_review mr join weekly_plan p on p.id = mr.weekly_plan_id"
            + " where p.week_start_date = '"
            + week
            + "' and p.employee_id in ("
            + personaList()
            + ")");
  }

  private int disputeCountAt(LocalDate week) {
    return scalarInt(
        "select count(*) from alignment_dispute d join weekly_commitment c on c.id = d.commitment_id"
            + " join weekly_plan p on p.id = c.weekly_plan_id"
            + " where p.week_start_date = '"
            + week
            + "' and p.employee_id in ("
            + personaList()
            + ")");
  }

  private int syncCountAt(LocalDate week) {
    return scalarInt(
        "select count(*) from outlook_calendar_sync_record where week_start_date = '"
            + week
            + "' and owner_employee_id in ("
            + personaList()
            + ")");
  }

  private String personaList() {
    return PERSONAS.stream().map(id -> "'" + id + "'").reduce((a, b) -> a + "," + b).orElseThrow();
  }

  private int scalarInt(String sql) {
    Integer n = jdbc.queryForObject(sql, Integer.class);
    return n == null ? 0 : n;
  }

  private static String readResource(String path) throws IOException {
    try (InputStream in = DemoSeederResetTest.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IOException("resource not found on classpath: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
