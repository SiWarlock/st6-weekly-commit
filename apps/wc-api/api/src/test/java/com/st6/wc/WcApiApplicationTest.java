package com.st6.wc;

import static org.assertj.core.api.Assertions.assertThat;

import com.st6.wc.support.AbstractAppBootTest;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots {@code WcApiApplication} under {@code local} against the shared Testcontainers PG16 ({@link
 * AbstractAppBootTest}) and asserts the k8s actuator probes are UP (§15 / E24 / REQ-O-009) and the
 * {@code :shared} {@code Clock} bean is in the production context (flag 6 — ClockConfig is
 * component-scanned, not just test-wired). As of Phase 2 the app is DB-dependent in every mode
 * (DemoAuthFilter/AuditService), so this boots with a real DB — only the <em>security</em>
 * autoconfig is excluded (the real SecurityFilterChain is 2.6; the JwtDecoder is gated off anyway —
 * local is demo mode).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties =
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration,"
            + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration")
@ActiveProfiles("local")
class WcApiApplicationTest extends AbstractAppBootTest {

  @Autowired TestRestTemplate rest;
  @Autowired Clock clock;

  @Test
  void readinessProbe_isUp() {
    ResponseEntity<String> r = rest.getForEntity("/actuator/health/readiness", String.class);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(r.getBody()).contains("UP");
  }

  @Test
  void livenessProbe_isUp() {
    ResponseEntity<String> r = rest.getForEntity("/actuator/health/liveness", String.class);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(r.getBody()).contains("UP");
  }

  @Test
  void sharedClockBean_isInProductionContext() {
    assertThat(clock).isNotNull();
    assertThat(clock.instant()).isNotNull();
  }
}
