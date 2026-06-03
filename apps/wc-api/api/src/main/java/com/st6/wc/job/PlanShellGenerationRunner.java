package com.st6.wc.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * One-shot activation shell for the weekly-shell generation job (task 3.2, §8). Wires as a bean
 * <strong>only</strong> under {@code --app.job=generate-plan-shells} ({@link
 * ConditionalOnProperty}) — so it is inert in the normal {@code wc-api} web image; the SAME image
 * launched with that arg (the EKS CronJob) runs {@link PlanShellGenerator#generate()} once.
 *
 * <p>One-shot termination is achieved by launching the job with {@code
 * --spring.main.web-application-type=none} (infra-owned CronJob manifest): with no web server, once
 * this {@code ApplicationRunner} returns, {@code SpringApplication.run} returns, {@code main}
 * exits, and the JVM terminates (only daemon threads remain). The runner deliberately does NOT hold
 * the {@code ApplicationContext} or call {@code SpringApplication.exit} — that would store an
 * externally-mutable singleton (SpotBugs {@code EI_EXPOSE_REP2}) for no benefit over the
 * web-type=none termination. (A k8s {@code activeDeadlineSeconds} is the operational backstop.)
 *
 * <p>Thin by design: all generation logic lives in {@link PlanShellGenerator} (a plain component),
 * keeping it directly testable while this runner's gating + delegation are proven via {@code
 * ApplicationContextRunner} — an {@code ApplicationRunner} otherwise auto-fires at startup, which a
 * {@code @SpringBootTest} can't host without it running mid-test.
 */
@Component
@ConditionalOnProperty(name = "app.job", havingValue = "generate-plan-shells")
public class PlanShellGenerationRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(PlanShellGenerationRunner.class);

  private final PlanShellGenerator generator;

  public PlanShellGenerationRunner(PlanShellGenerator generator) {
    this.generator = generator;
  }

  @Override
  public void run(ApplicationArguments args) {
    int created = generator.generate();
    log.info("Plan-shell generation job complete ({} shell(s) created); one-shot exit.", created);
  }
}
