package com.st6.wc.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Gating proof for {@link PlanShellGenerationRunner} (task 3.2, §8): the one-shot runner wires as a
 * bean <strong>only</strong> under {@code --app.job=generate-plan-shells} (so it is inert in the
 * normal web image) and, when active, {@code run()} delegates to {@link PlanShellGenerator}. Uses
 * {@link ApplicationContextRunner} (which evaluates {@code @ConditionalOnProperty} but does NOT
 * auto-invoke {@code ApplicationRunner}s) with a mocked generator — so {@code run()} can be invoked
 * explicitly and verified without a real generation or a JVM exit. No DB needed.
 */
class PlanShellGenerationRunnerGatingTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(PlanShellGenerator.class, () -> mock(PlanShellGenerator.class))
          .withUserConfiguration(PlanShellGenerationRunner.class);

  // --- gating: absent without the activation property (inert in the normal web image) ----
  @Test
  void runnerAbsent_withoutAppJobProperty() {
    contextRunner.run(ctx -> assertThat(ctx).doesNotHaveBean(PlanShellGenerationRunner.class));
  }

  // --- gating: present ONLY with --app.job=generate-plan-shells; run() delegates to the generator
  // -
  @Test
  void runnerPresent_withAppJobProperty_andRunDelegatesToGenerator() throws Exception {
    contextRunner
        .withPropertyValues("app.job=generate-plan-shells")
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(PlanShellGenerationRunner.class);
              PlanShellGenerationRunner runner = ctx.getBean(PlanShellGenerationRunner.class);
              PlanShellGenerator generator = ctx.getBean(PlanShellGenerator.class);

              runner.run(new DefaultApplicationArguments());

              verify(generator).generate(); // run() delegated to the generation logic
            });
  }
}
