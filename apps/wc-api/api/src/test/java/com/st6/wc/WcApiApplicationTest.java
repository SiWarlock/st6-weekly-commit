package com.st6.wc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots {@code WcApiApplication} under {@code local} with a real servlet container and asserts the
 * k8s actuator probes are UP (§15 / E24 / REQ-O-009) and the {@code :shared} {@code Clock} bean is
 * in the production context (flag 6 — ClockConfig is component-scanned, not just test-wired).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    // task 1.5: spring-boot-starter-data-jpa activates DataSourceAutoConfiguration; task 2.1:
    // spring-boot-starter-oauth2-resource-server activates Spring Security's default chain (which
    // would secure the actuator probes). This test asserts actuator probes + the shared Clock bean
    // (no persistence, no auth) and intentionally boots DB-less + security-less, so both autoconfig
    // families are excluded. The real SecurityFilterChain is task 2.6; the JwtDecoder is gated off
    // here anyway because the local profile is demo mode (demo-auth.enabled=true).
    properties =
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration,"
            + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration")
@ActiveProfiles("local")
class WcApiApplicationTest {

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
