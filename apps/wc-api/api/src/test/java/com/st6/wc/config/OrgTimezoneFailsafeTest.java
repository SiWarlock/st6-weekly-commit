package com.st6.wc.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.st6.wc.common.OrgTimeConfig;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The {@code ORG_TIMEZONE} fail-safe (D.6) — the one piece of real logic in 0.4. Unset/blank/
 * unparseable ⇒ {@code America/Chicago} + WARN; never UTC, never crash. A valid IANA zone is
 * honored. Pure unit (no Spring) so the rule is pinned in isolation; the CronJob (D.4) reuses it.
 */
class OrgTimezoneFailsafeTest {

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   ", "Not/AZone", "Mars/Phobos", "garbage", "+99:00"})
  void unsetBlankOrUnparseable_fallsBackToChicago_neverUtc(String configured) {
    ZoneId resolved = OrgTimeBindingConfig.resolveZone(configured);
    assertEquals(OrgTimeConfig.DEFAULT_ZONE, resolved, "must fall back to America/Chicago");
    assertNotEquals(ZoneOffset.UTC, resolved, "must never silently default to UTC");
    assertNotEquals(ZoneId.of("UTC"), resolved, "must never silently default to UTC");
  }

  @ParameterizedTest
  @ValueSource(strings = {"America/Chicago", "Europe/London", "Asia/Tokyo", "UTC"})
  void validIanaZone_isHonored(String configured) {
    assertEquals(ZoneId.of(configured), OrgTimeBindingConfig.resolveZone(configured));
  }

  @Test
  void explicitUtc_isHonored_notTreatedAsFallback() {
    // Explicitly choosing UTC is valid and honored; the rule only forbids *silently* using UTC.
    assertEquals(ZoneId.of("UTC"), OrgTimeBindingConfig.resolveZone("UTC"));
  }
}
