package com.st6.wc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Property-resolution test (NOT a boot) for the deployed {@code aws} profile (Wave-1 s3, §12 /
 * Appendix D.1/D.2). Pins two things:
 *
 * <ul>
 *   <li><strong>The datasource deploy config</strong> — under {@code aws} the profile imports the
 *       CSI secret mount ({@code spring.config.import=…configtree:/mnt/secrets/} → binds {@code
 *       spring.datasource.*}/auth0 directly), sets {@code ddl-auto=validate} (the migration Job
 *       owns the schema — forbidden-pattern #3), and a prod Hikari pool. None of these leak into
 *       base/local/demo (no mount there; the §9 test harness supplies its own datasource).
 *   <li><strong>The profile-name fix</strong> — every k8s workload runs {@code
 *       SPRING_PROFILES_ACTIVE=aws} ({@code deployment-api.yaml:40}), so the deploy config must
 *       live in {@code application-aws.yml}. It previously lived in {@code application-prod.yml}
 *       (profile {@code prod}) and therefore never loaded on the deployed stack. The {@code
 *       aws}-only logging marker proves the renamed file loads under {@code aws} and no longer
 *       under {@code prod}.
 * </ul>
 *
 * <p>Mirrors {@link FlywayProfilePropertyTest}'s {@link ApplicationContextRunner} + {@link
 * ConfigDataApplicationContextInitializer} approach. {@code optional:configtree:} keeps the runner
 * booting without a real mount.
 */
class AwsProfileConfigPropertyTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer());

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
  void awsProfile_importsSecretsConfigtreeValidateAndHikari() {
    // The aws profile binds the CSI mount + validates entities↔schema + a prod-sized pool.
    assertThat(propertyUnder("spring.config.import", "aws"))
        .as("aws imports the CSI secret config tree")
        .contains("configtree:/mnt/secrets/");
    assertThat(propertyUnder("spring.jpa.hibernate.ddl-auto", "aws"))
        .as("aws validates against the migration-owned schema")
        .isEqualTo("validate");
    assertThat(propertyUnder("spring.datasource.hikari.maximum-pool-size", "aws"))
        .as("aws uses a prod Hikari pool (not the §29 test cap)")
        .isEqualTo("10");

    // None of the deploy datasource config leaks into base/local/demo (no mount; the §9 harness
    // supplies its own datasource + ddl-auto).
    for (String[] env : new String[][] {{}, {"local"}, {"demo"}}) {
      assertThat(propertyUnder("spring.config.import", env))
          .as("no CSI configtree import outside aws")
          .isNull();
      assertThat(propertyUnder("spring.jpa.hibernate.ddl-auto", env))
          .as("no aws ddl-auto outside aws")
          .isNull();
      assertThat(propertyUnder("spring.datasource.hikari.maximum-pool-size", env))
          .as("no aws Hikari size outside aws")
          .isNull();
    }
  }

  @Test
  void awsProfileFile_loadsUnderAwsProfileName() {
    // The renamed application-aws.yml loads under SPRING_PROFILES_ACTIVE=aws (its INFO logging
    // marker
    // resolves) — this FAILS while the file is named application-prod.yml.
    assertThat(propertyUnder("logging.level.com.st6.wc", "aws"))
        .as("application-aws.yml loads under the aws profile")
        .isEqualTo("INFO");
    // …and the deploy config no longer hides under the unused `prod` profile.
    assertThat(propertyUnder("logging.level.com.st6.wc", "prod"))
        .as("no deploy config remains on the dead `prod` profile")
        .isNull();
  }

  @Test
  void awsProfile_setsAppEnvToAws() {
    // app.env feeds the SyncJobPointer's `env` field (SnsLifecyclePublisher
    // @Value("${app.env:local}"))
    // — under aws it must be "aws" so the published pointer is correct end-to-end (Wave-2 s7).
    assertThat(propertyUnder("app.env", "aws"))
        .as("aws sets the pointer env to aws")
        .isEqualTo("aws");
    // base/local/demo leave it unset → the publisher's :local default applies.
    assertThat(propertyUnder("app.env")).as("no app.env outside aws").isNull();
    assertThat(propertyUnder("app.env", "local")).as("no app.env in local").isNull();
  }

  // --- deploy-fix #7: the aws profile must keep base's always-resolvable-defaults invariant ------
  // The non-HTTP-serving Jobs (migration / generate-plan-shells / rebuild-projections) run aws but
  // never set ROOT_DOMAIN — so the nested ${ROOT_DOMAIN} on the audience (line 30) + CORS (line 50)
  // must carry the :localhost default (mirroring base application.yml) or the Job context
  // fail-boots
  // on "Could not resolve placeholder 'ROOT_DOMAIN'". RED today (the bare ${ROOT_DOMAIN}).
  @Test
  void awsProfile_noEnv_resolvesRootDomainPlaceholdersToLocalhost() {
    assertThat(propertyUnder("auth0.audience", "aws"))
        .as("aws audience resolves with no ROOT_DOMAIN env (Job context)")
        .isEqualTo("https://api.wc.localhost");
    assertThat(propertyUnder("app.cors.allowed-origins", "aws"))
        .as("aws CORS allow-list resolves with no ROOT_DOMAIN/CORS env (Job context)")
        .isEqualTo("https://wc.localhost");
  }

  @Test
  void awsProfile_withRootDomain_resolvesRealDomain() {
    // Production unchanged: the api/worker Deployments inject the real ROOT_DOMAIN → audience +
    // CORS
    // resolve to the real domain; the :localhost default only applies in the non-serving Jobs.
    final String[] holder = new String[2];
    runner
        .withPropertyValues("spring.profiles.active=aws", "ROOT_DOMAIN=example.com")
        .run(
            ctx -> {
              holder[0] = ctx.getEnvironment().getProperty("auth0.audience");
              holder[1] = ctx.getEnvironment().getProperty("app.cors.allowed-origins");
            });
    assertThat(holder[0])
        .as("real ROOT_DOMAIN wins for audience")
        .isEqualTo("https://api.wc.example.com");
    assertThat(holder[1]).as("real ROOT_DOMAIN wins for CORS").isEqualTo("https://wc.example.com");
  }
}
