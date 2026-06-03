package com.st6.wc.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * Pins the org-time contract: default zone {@code America/Chicago} (override-ready) and a
 * Monday-anchored Mon–Sun week resolver returning {@code week_start_date} (§3 weekly cadence). Pure
 * deterministic logic.
 */
class OrgTimeConfigTest {

  private static final LocalDate WEEK_MONDAY = LocalDate.of(2026, 6, 1); // a Monday

  @Test
  void defaultTimezone_isAmericaChicago() {
    assertEquals(ZoneId.of("America/Chicago"), new OrgTimeConfig().zoneId());
  }

  @Test
  void timezone_isOverridable() {
    assertEquals(ZoneId.of("UTC"), new OrgTimeConfig(ZoneId.of("UTC")).zoneId());
  }

  @Test
  void weekResolver_mapsMidweekMondayAndSunday_toThatWeeksMonday() {
    OrgTimeConfig org = new OrgTimeConfig();
    assertEquals(DayOfWeek.MONDAY, WEEK_MONDAY.getDayOfWeek(), "fixture sanity");
    assertEquals(WEEK_MONDAY, org.weekStartDate(WEEK_MONDAY)); // the Monday itself
    assertEquals(WEEK_MONDAY, org.weekStartDate(LocalDate.of(2026, 6, 3))); // Wednesday (mid-week)
    assertEquals(WEEK_MONDAY, org.weekStartDate(LocalDate.of(2026, 6, 7))); // Sunday (week end)
  }

  @Test
  void weekResolver_fromInstant_usesOrgZone() {
    OrgTimeConfig org = new OrgTimeConfig();
    // Wednesday 2026-06-03T12:00Z → that week's Monday in the org zone.
    assertEquals(WEEK_MONDAY, org.weekStartDate(Instant.parse("2026-06-03T12:00:00Z")));
  }

  // --- 3.2 RED #7: weekEndDate is the Sunday of the week (Monday + 6), incl. a year boundary ----
  @Test
  void weekEndDate_isSundayOfWeek() {
    OrgTimeConfig org = new OrgTimeConfig();
    LocalDate sunday = LocalDate.of(2026, 6, 7); // the Sunday of the WEEK_MONDAY week
    assertEquals(DayOfWeek.SUNDAY, sunday.getDayOfWeek(), "fixture sanity");
    assertEquals(sunday, org.weekEndDate(WEEK_MONDAY)); // from the Monday
    assertEquals(sunday, org.weekEndDate(LocalDate.of(2026, 6, 3))); // from a mid-week Wednesday
    assertEquals(WEEK_MONDAY.plusDays(6), org.weekEndDate(WEEK_MONDAY)); // == Monday + 6
    // year boundary: the week of Thu 2026-12-31 ends Sun 2027-01-03
    assertEquals(LocalDate.of(2027, 1, 3), org.weekEndDate(LocalDate.of(2026, 12, 31)));
  }
}
