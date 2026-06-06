package com.st6.wc.demoseed;

import java.time.LocalDate;

/**
 * The demo-seed entry point the one-shot {@link com.st6.wc.job.DemoSeedRunner} delegates to. Exists
 * so the runner depends on this interface rather than the concrete {@link DemoSeeder} — the project
 * convention (see {@code AwsSnsLifecycleGateway}) that sidesteps the SpotBugs {@code
 * EI_EXPOSE_REP2} false-positive on storing an injected concrete component.
 */
public interface DemoSeeding {

  /**
   * Reset-then-seed the demo lifecycle matrix for the week containing {@code week} (normalized to
   * its Monday). Returns the number of plans seeded.
   */
  int run(LocalDate week);
}
