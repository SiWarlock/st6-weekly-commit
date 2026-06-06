package com.st6.wc.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.st6.wc.common.OrgTimeConfig;
import com.st6.wc.demoseed.DemoSeeder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Gating + arg-parsing proof for {@link DemoSeedRunner} (brief 107a, §23): the one-shot runner
 * wires as a bean <strong>only</strong> under {@code --app.job=seed-demo} (inert in the normal web
 * image) and, when active, {@code run()} parses {@code --week=YYYY-MM-DD} (normalized to its Monday
 * via {@link OrgTimeConfig}) — or, absent, the current week's Monday from the injected {@link
 * Clock} — and delegates the resolved {@code weekStart} to {@link DemoSeeder#run(LocalDate)}. Uses
 * {@link ApplicationContextRunner} (evaluates {@code @ConditionalOnProperty} without auto-firing
 * {@code ApplicationRunner}s) with a mocked seeder + a fixed Clock — so {@code run()} is invoked
 * explicitly and the delegated week verified, with no real seed or JVM exit. No DB needed. Mirrors
 * {@code ProjectionRebuildRunnerGatingTest}.
 */
class DemoSeedRunnerGatingTest {

  // Fri 2026-06-12 12:00Z → 07:00 America/Chicago → current-week Monday = 2026-06-08.
  private static final Instant NOW = Instant.parse("2026-06-12T12:00:00Z");
  private static final LocalDate CURRENT_WEEK_MONDAY = LocalDate.of(2026, 6, 8);

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(DemoSeeder.class, () -> mock(DemoSeeder.class))
          .withBean(OrgTimeConfig.class, OrgTimeConfig::new)
          .withBean(Clock.class, () -> Clock.fixed(NOW, ZoneOffset.UTC))
          .withUserConfiguration(DemoSeedRunner.class);

  @Test
  void runnerAbsent_withoutAppJobProperty() {
    contextRunner.run(ctx -> assertThat(ctx).doesNotHaveBean(DemoSeedRunner.class));
  }

  @Test
  void runnerPresent_withWeekArg_delegatesParsedWeekMonday() {
    contextRunner
        .withPropertyValues("app.job=seed-demo")
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(DemoSeedRunner.class);
              DemoSeedRunner runner = ctx.getBean(DemoSeedRunner.class);
              DemoSeeder seeder = ctx.getBean(DemoSeeder.class);

              // A NON-Monday arg (Wed 2026-06-10) → pins runner-side normalization to its Monday.
              runner.run(new DefaultApplicationArguments("--week=2026-06-10"));

              verify(seeder).run(eq(LocalDate.of(2026, 6, 8)));
            });
  }

  @Test
  void runnerWithoutWeekArg_delegatesCurrentWeekMonday() {
    contextRunner
        .withPropertyValues("app.job=seed-demo")
        .run(
            ctx -> {
              DemoSeedRunner runner = ctx.getBean(DemoSeedRunner.class);
              DemoSeeder seeder = ctx.getBean(DemoSeeder.class);

              runner.run(new DefaultApplicationArguments()); // no --week → current week from Clock

              verify(seeder).run(eq(CURRENT_WEEK_MONDAY));
            });
  }
}
