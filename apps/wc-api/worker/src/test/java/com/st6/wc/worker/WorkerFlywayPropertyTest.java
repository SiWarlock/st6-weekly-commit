package com.st6.wc.worker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Property-resolution test: the worker never migrates — {@code spring.flyway.enabled} resolves
 * {@code false} under every worker profile (base/local/demo/aws), §12 / D.3. Also pins the §42 fix
 * (task 092): the deploy config lives in {@code application-aws.yml} (the profile the worker
 * Deployment runs — {@code SPRING_PROFILES_ACTIVE=aws}), not the dead {@code application-prod.yml}.
 */
class WorkerFlywayPropertyTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer());

  private String flywayEnabledUnder(String... profiles) {
    return propertyUnder("spring.flyway.enabled", profiles);
  }

  private String propertyUnder(String key, String... profiles) {
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
  void flywayDisabled_underEveryWorkerProfile() {
    assertThat(flywayEnabledUnder()).isEqualTo("false");
    assertThat(flywayEnabledUnder("local")).isEqualTo("false");
    assertThat(flywayEnabledUnder("demo")).isEqualTo("false");
    assertThat(flywayEnabledUnder("aws")).isEqualTo("false");
  }

  @Test
  void awsProfileFile_loadsUnderAwsProfileName() {
    // application-aws.yml loads under SPRING_PROFILES_ACTIVE=aws (its INFO logging marker resolves)
    // — this FAILS while the file is named application-prod.yml (§42 / the api's 090 fix mirrored).
    assertThat(propertyUnder("logging.level.com.st6.wc.worker", "aws"))
        .as("application-aws.yml loads under the aws profile")
        .isEqualTo("INFO");
    // …and no deploy config remains on the dead `prod` profile.
    assertThat(propertyUnder("logging.level.com.st6.wc.worker", "prod"))
        .as("no deploy config remains on the dead `prod` profile")
        .isNull();
  }
}
