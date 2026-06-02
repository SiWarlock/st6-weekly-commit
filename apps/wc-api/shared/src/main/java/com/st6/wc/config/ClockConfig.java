package com.st6.wc.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes an injectable {@link Clock} so derived/time-based state (§17: OVERDUE review derivation,
 * SLA due dates, weekly cadence) reads "now" from a bean that tests can replace with a fixed Clock.
 * Lives in {@code :shared} because both {@code :api} (SLA/derivation) and {@code :worker}
 * (time-based transitions) consume it.
 */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.systemDefaultZone();
  }
}
