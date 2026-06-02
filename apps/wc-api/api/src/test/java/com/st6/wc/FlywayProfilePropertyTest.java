package com.st6.wc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Property-resolution test (NOT a migrate boot): {@code spring.flyway.enabled} resolves {@code
 * false} under base/local/demo and {@code true} only under {@code flyway-migrate} — the Migration
 * Job is the sole schema owner (§12 / D.2 / D.5). No Flyway dependency on the classpath yet.
 */
class FlywayProfilePropertyTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer());

  private String flywayEnabledUnder(String... profiles) {
    final String[] holder = new String[1];
    runner
        .withPropertyValues(
            profiles.length == 0
                ? new String[] {}
                : new String[] {"spring.profiles.active=" + String.join(",", profiles)})
        .run(ctx -> holder[0] = ctx.getEnvironment().getProperty("spring.flyway.enabled"));
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
}
