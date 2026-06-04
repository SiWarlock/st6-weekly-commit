package com.st6.wc.job;

import com.st6.wc.projection.ProjectionRebuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * One-shot activation shell for the projection-rebuild job (task 6.7, §8/§9/§23). Wires as a bean
 * <strong>only</strong> under {@code --app.job=rebuild-projections} ({@link ConditionalOnProperty})
 * — inert in the normal {@code wc-api} web image; the SAME image launched with that arg runs {@link
 * ProjectionRebuilder#rebuild()} once. One-shot termination via {@code
 * --spring.main.web-application-type=none} (infra-owned manifest): the runner holds no context and
 * does not call {@code SpringApplication.exit} (avoids the {@code EI_EXPOSE_REP2}
 * stored-singleton). Thin by design — the logic lives in {@link ProjectionRebuilder}; this runner's
 * gating + delegation are proven via {@code ApplicationContextRunner}. Mirrors {@code
 * PlanShellGenerationRunner} (§23).
 */
@Component
@ConditionalOnProperty(name = "app.job", havingValue = "rebuild-projections")
public class ProjectionRebuildRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(ProjectionRebuildRunner.class);

  private final ProjectionRebuilder rebuilder;

  public ProjectionRebuildRunner(ProjectionRebuilder rebuilder) {
    this.rebuilder = rebuilder;
  }

  @Override
  public void run(ApplicationArguments args) {
    int processed = rebuilder.rebuild();
    log.info("Projection rebuild job complete ({} plan(s) processed); one-shot exit.", processed);
  }
}
