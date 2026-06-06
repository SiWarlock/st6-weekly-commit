package com.st6.wc.job;

import com.st6.wc.common.OrgTimeConfig;
import com.st6.wc.demoseed.DemoSeeding;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * One-shot activation shell for the week-parameterized demo-seed job (brief 107, §23). Wires as a
 * bean <strong>only</strong> under {@code --app.job=seed-demo} ({@link ConditionalOnProperty}) — so
 * it is inert in the normal {@code wc-api} web image; the SAME image launched with that arg (the
 * infra-owned {@code job-seed-demo.yaml} Job) runs {@link DemoSeeding#run(LocalDate)} once for the
 * target week.
 *
 * <p>Parses {@code --week=YYYY-MM-DD} and normalizes it to its Monday via {@link OrgTimeConfig}; an
 * absent {@code --week} resolves to the current week's Monday from the injected {@link Clock},
 * interpreted in the org timezone (the {@code Instant} overload — correct across week boundaries
 * regardless of the JVM zone). The seeder re-normalizes defensively, so direct callers are covered
 * too.
 *
 * <p>One-shot termination via {@code --spring.main.web-application-type=none} (infra-owned
 * manifest): with no web server, once this {@code ApplicationRunner} returns, {@code main} exits.
 * The runner deliberately holds no {@code ApplicationContext} and does not call {@code
 * SpringApplication.exit} (avoids the {@code EI_EXPOSE_REP2} stored singleton). Thin by design —
 * the logic lives behind {@link DemoSeeding} (implemented by {@code DemoSeeder}); this runner's
 * gating + arg-parsing + delegation are proven via {@code ApplicationContextRunner}. Mirrors {@code
 * ProjectionRebuildRunner} / {@code PlanShellGenerationRunner}.
 */
@Component
@ConditionalOnProperty(name = "app.job", havingValue = "seed-demo")
public class DemoSeedRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoSeedRunner.class);
  private static final String WEEK_OPTION = "week";

  private final DemoSeeding seeder;
  private final OrgTimeConfig orgTimeConfig;
  private final Clock clock;

  public DemoSeedRunner(DemoSeeding seeder, OrgTimeConfig orgTimeConfig, Clock clock) {
    this.seeder = seeder;
    this.orgTimeConfig = orgTimeConfig;
    this.clock = clock;
  }

  @Override
  public void run(ApplicationArguments args) {
    List<String> values = args.getOptionValues(WEEK_OPTION);
    LocalDate weekStart =
        (values == null || values.isEmpty())
            ? orgTimeConfig.weekStartDate(clock.instant()) // current week's Monday in org tz
            : orgTimeConfig.weekStartDate(LocalDate.parse(values.get(0)));
    int seeded = seeder.run(weekStart);
    log.info("Demo-seed job complete for week {} ({} plans); one-shot exit.", weekStart, seeded);
  }
}
