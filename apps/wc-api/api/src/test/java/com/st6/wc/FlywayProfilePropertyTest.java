package com.st6.wc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Property-resolution test (NOT a migrate boot): {@code spring.flyway.enabled} resolves {@code
 * false} under base/local/demo and {@code true} only under {@code flyway-migrate} — the Migration
 * Job is the sole schema owner (§12 / D.2 / D.5). Also pins {@code spring.flyway.locations}: the
 * migrate Job loads BOTH the schema/reference migrations ({@code db/migration}) and the demo seed
 * ({@code db/demo-seed}, Opt-1), while every other profile leaves it unset — so the deploy Job
 * seeds the demo personas but the shared integration-test harness (which re-enables Flyway on the
 * DEFAULT {@code db/migration} only) never does (task 10.2).
 */
class FlywayProfilePropertyTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer());

  private String flywayEnabledUnder(String... profiles) {
    return flywayPropertyUnder("spring.flyway.enabled", profiles);
  }

  private String flywayLocationsUnder(String... profiles) {
    return flywayPropertyUnder("spring.flyway.locations", profiles);
  }

  private String flywayPropertyUnder(String key, String... profiles) {
    final String[] holder = new String[1];
    runner
        .withPropertyValues(
            profiles.length == 0
                ? new String[] {}
                : new String[] {"spring.profiles.active=" + String.join(",", profiles)})
        .run(ctx -> holder[0] = ctx.getEnvironment().getProperty(key));
    return holder[0];
  }

  @Test
  void flywayDisabled_inBaseAndLocalAndDemo() {
    assertThat(flywayEnabledUnder()).isEqualTo("false");
    assertThat(flywayEnabledUnder("local")).isEqualTo("false");
    assertThat(flywayEnabledUnder("demo")).isEqualTo("false");
  }

  @Test
  void flywayEnabled_onlyUnderMigrateProfile() {
    assertThat(flywayEnabledUnder("flyway-migrate")).isEqualTo("true");
  }

  @Test
  void flywayLocations_demoSeedAddedOnlyUnderMigrateProfile() {
    // the migrate Job (Opt-1) loads the schema/reference migrations AND the demo seed.
    assertThat(flywayLocationsUnder("flyway-migrate"))
        .isEqualTo("classpath:db/migration,classpath:db/demo-seed");
    // every other profile sets no locations key — Flyway is off there, and the shared test harness
    // re-enables it on the DEFAULT db/migration only, so the demo seed never pollutes tests.
    assertThat(flywayLocationsUnder()).isNull();
    assertThat(flywayLocationsUnder("local")).isNull();
    assertThat(flywayLocationsUnder("demo")).isNull();
  }
}
