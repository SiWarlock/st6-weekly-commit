package com.st6.wc.worker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Property-resolution test: the worker never migrates — {@code spring.flyway.enabled} resolves
 * {@code false} under every worker profile (base/local/demo/prod), §12 / D.3.
 */
class WorkerFlywayPropertyTest {

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
  void flywayDisabled_underEveryWorkerProfile() {
    assertThat(flywayEnabledUnder()).isEqualTo("false");
    assertThat(flywayEnabledUnder("local")).isEqualTo("false");
    assertThat(flywayEnabledUnder("demo")).isEqualTo("false");
    assertThat(flywayEnabledUnder("prod")).isEqualTo("false");
  }
}
