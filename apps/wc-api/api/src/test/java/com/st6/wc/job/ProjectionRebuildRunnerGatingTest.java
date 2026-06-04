package com.st6.wc.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.st6.wc.projection.ProjectionRebuilder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Gating proof for {@link ProjectionRebuildRunner} (task 6.7, §8/§23): the one-shot runner wires as
 * a bean <strong>only</strong> under {@code --app.job=rebuild-projections} (inert in the normal web
 * image) and, when active, {@code run()} delegates to {@link ProjectionRebuilder}. Uses {@link
 * ApplicationContextRunner} (evaluates {@code @ConditionalOnProperty} without auto-invoking {@code
 * ApplicationRunner}s) with a mocked rebuilder — so {@code run()} is invoked explicitly and
 * verified without a real rebuild or a JVM exit. No DB needed. Mirrors {@code
 * PlanShellGenerationRunnerGatingTest}.
 */
class ProjectionRebuildRunnerGatingTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(ProjectionRebuilder.class, () -> mock(ProjectionRebuilder.class))
          .withUserConfiguration(ProjectionRebuildRunner.class);

  @Test
  void runnerAbsent_withoutAppJobProperty() {
    contextRunner.run(ctx -> assertThat(ctx).doesNotHaveBean(ProjectionRebuildRunner.class));
  }

  @Test
  void runnerPresent_withAppJobProperty_andRunDelegatesToRebuilder() throws Exception {
    contextRunner
        .withPropertyValues("app.job=rebuild-projections")
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(ProjectionRebuildRunner.class);
              ProjectionRebuildRunner runner = ctx.getBean(ProjectionRebuildRunner.class);
              ProjectionRebuilder rebuilder = ctx.getBean(ProjectionRebuilder.class);

              runner.run(new DefaultApplicationArguments());

              verify(rebuilder).rebuild(); // run() delegated to the rebuild logic
            });
  }
}
