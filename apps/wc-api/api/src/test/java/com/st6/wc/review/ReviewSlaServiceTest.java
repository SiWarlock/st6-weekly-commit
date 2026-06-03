package com.st6.wc.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.st6.wc.common.OrgTimeConfig;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/**
 * {@code ReviewSlaService} unit proof (task 3.5, Appendix F.3) — the manager-review SLA due date is
 * <strong>17:00 org-tz on the next business day</strong> (weekday-only) after {@code lockedAt}.
 * Pure + deterministic over a passed {@code lockedAt} {@link Instant} interpreted in the org zone
 * (the caller passes {@code clock.instant()} at lock). The weekend skip (Friday lock → Monday
 * 17:00) is the load-bearing case (§17 weekday-only across a weekend).
 */
class ReviewSlaServiceTest {

  private static final ZoneId CT = OrgTimeConfig.DEFAULT_ZONE; // America/Chicago
  private final ReviewSlaService sla = new ReviewSlaService(new OrgTimeConfig());

  private static Instant ct(int y, int mo, int d, int h, int mi) {
    return ZonedDateTime.of(y, mo, d, h, mi, 0, 0, CT).toInstant();
  }

  private static Instant ctDue(int y, int mo, int d) {
    return ZonedDateTime.of(java.time.LocalDate.of(y, mo, d), LocalTime.of(17, 0), CT).toInstant();
  }

  // --- a weekday lock → 17:00 the NEXT day (Mon 06-01 → Tue 06-02 17:00) ----
  @Test
  void mondayLock_dueNextDay1700() {
    assertThat(sla.reviewDueAt(ct(2026, 6, 1, 10, 0))).isEqualTo(ctDue(2026, 6, 2));
  }

  // --- a late weekday lock is still the NEXT business day, never same-day (Mon 23:00 → Tue 17:00)
  @Test
  void lateMondayLock_stillNextDay() {
    assertThat(sla.reviewDueAt(ct(2026, 6, 1, 23, 0))).isEqualTo(ctDue(2026, 6, 2));
  }

  // --- Friday lock skips the weekend → Monday 17:00 (06-05 Fri → 06-08 Mon) ----
  @Test
  void fridayLock_skipsWeekend_dueMonday1700() {
    assertThat(sla.reviewDueAt(ct(2026, 6, 5, 10, 0))).isEqualTo(ctDue(2026, 6, 8));
  }

  // --- Saturday lock → Monday 17:00 (next business day) ----
  @Test
  void saturdayLock_dueMonday1700() {
    assertThat(sla.reviewDueAt(ct(2026, 6, 6, 9, 0))).isEqualTo(ctDue(2026, 6, 8));
  }

  // --- Sunday lock → Monday 17:00 ----
  @Test
  void sundayLock_dueMonday1700() {
    assertThat(sla.reviewDueAt(ct(2026, 6, 7, 9, 0))).isEqualTo(ctDue(2026, 6, 8));
  }
}
