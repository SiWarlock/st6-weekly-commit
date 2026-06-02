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
    // task 1.5: spring-boot-starter-data-jpa is now on the :api classpath, which would activate
    // DataSourceAutoConfiguration. This test asserts actuator probes + the shared Clock bean (no
    // persistence) and intentionally boots DB-less, so JPA/DataSource autoconfig is excluded here.
    properties =
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration")
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
