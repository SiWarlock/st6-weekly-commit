package com.st6.wc.review;

import com.st6.wc.common.OrgTimeConfig;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Service;

/**
 * Computes the manager-review SLA due date (task 3.5, Appendix F.3) — <strong>17:00 org-tz on the
 * next business day</strong> (weekday-only) after the lock instant. Pure + deterministic over the
 * passed {@code lockedAt} interpreted in the org zone ({@link OrgTimeConfig}); the caller passes
 * {@code clock.instant()} at lock. A Friday lock skips the weekend to Monday 17:00 (§17
 * weekday-only across a weekend); a late-in-the-day lock is still the <em>next</em> business day,
 * never same-day.
 */
@Service
public class ReviewSlaService {

  private static final LocalTime DUE_TIME = LocalTime.of(17, 0);

  private final OrgTimeConfig orgTimeConfig;

  public ReviewSlaService(OrgTimeConfig orgTimeConfig) {
    this.orgTimeConfig = orgTimeConfig;
  }

  public Instant reviewDueAt(Instant lockedAt) {
    ZoneId zone = orgTimeConfig.zoneId();
    LocalDate due = LocalDate.ofInstant(lockedAt, zone).plusDays(1);
    while (due.getDayOfWeek() == DayOfWeek.SATURDAY || due.getDayOfWeek() == DayOfWeek.SUNDAY) {
      due = due.plusDays(1);
    }
    return ZonedDateTime.of(due, DUE_TIME, zone).toInstant();
  }
}
