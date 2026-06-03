package com.st6.wc.identity;

/**
 * The no-HTTP SYSTEM-actor boundary type (task 2.4, §6 / §8 / §10). Represents the actor for
 * server-initiated work that has no authenticated user — the Phase-8 plan-generation CronJob and
 * the Phase-10 sync worker. A singleton ({@link #INSTANCE}); distinct from {@link
 * AuthenticatedPrincipal} (carries no user {@code employeeId}) and exempt from the self /
 * direct-report scoping checks the {@code DomainAuthorizationService} (2.5) applies to user
 * principals. Defined here as the declared boundary; its consumers land in later phases.
 */
public final class SystemPrincipal {

  /** The canonical SYSTEM actor consumed by the cron/worker phases. */
  public static final SystemPrincipal INSTANCE = new SystemPrincipal();

  private SystemPrincipal() {}
}
