package com.st6.wc.common;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * Organization time settings: the org timezone (default {@code America/Chicago}, override-ready via
 * constructor — bound to {@code APP_ORG_TIMEZONE} at app-config time) and the Monday-anchored
 * Mon–Sun week resolver returning {@code week_start_date} (§3 weekly cadence). Pure, deterministic.
 */
public final class OrgTimeConfig {

  /** Default organization timezone when none is configured. */
  public static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Chicago");

  private final ZoneId zoneId;

  public OrgTimeConfig() {
    this(DEFAULT_ZONE);
  }

  public OrgTimeConfig(ZoneId zoneId) {
    this.zoneId = zoneId;
  }

  public ZoneId zoneId() {
    return zoneId;
  }

  /**
   * @return the Monday that starts the Mon–Sun week containing {@code date}.
   */
  public LocalDate weekStartDate(LocalDate date) {
    return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  /**
   * @return the {@code week_start_date} (Monday) for {@code instant}, interpreted in the org zone.
   */
  public LocalDate weekStartDate(Instant instant) {
    return weekStartDate(LocalDate.ofInstant(instant, zoneId));
  }

  /**
   * @return the {@code week_end_date} (Sunday) that ends the Mon–Sun week containing {@code date} —
   *     the Sunday pair to {@link #weekStartDate(LocalDate)} (task 3.2 / §3 weekly cadence).
   */
  public LocalDate weekEndDate(LocalDate date) {
    return weekStartDate(date).plusDays(6);
  }
}
