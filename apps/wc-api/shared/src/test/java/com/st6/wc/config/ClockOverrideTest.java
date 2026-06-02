package com.st6.wc.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Proves the {@code ClockConfig} Clock bean is overridable with a fixed Clock in tests — the
 * mechanism every later derived-state test (OVERDUE, SLA, week cadence) relies on for deterministic
 * time (§17).
 */
@SpringJUnitConfig({ClockConfig.class, ClockOverrideTest.FixedClockConfig.class})
class ClockOverrideTest {

  static final Instant FIXED = Instant.parse("2026-06-01T00:00:00Z");

  @Autowired Clock clock;

  @Test
  void fixedTestClock_overridesSystemClock() {
    assertEquals(FIXED, clock.instant());
  }

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(FIXED, ZoneOffset.UTC);
    }
  }
}
