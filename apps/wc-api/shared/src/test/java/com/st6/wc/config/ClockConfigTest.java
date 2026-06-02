package com.st6.wc.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Proves {@code ClockConfig} exposes an injectable {@code java.time.Clock} bean (§17 — derived
 * state such as OVERDUE is computed via an injectable Clock so it is deterministically testable).
 */
@SpringJUnitConfig(ClockConfig.class)
class ClockConfigTest {

  @Autowired Clock clock;

  @Test
  void clockBean_isInjectableAndUsable() {
    assertNotNull(clock, "ClockConfig must expose a Clock bean");
    assertNotNull(clock.instant());
  }
}
