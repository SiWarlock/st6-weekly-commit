package com.st6.wc.worker;

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
 * Boots {@code WcSyncWorkerApplication} (the separate deployable) under {@code local} with NO SQS
 * listener / Graph adapter / SQS-Graph env, and asserts the k8s probes are UP and the {@code
 * :shared} {@code Clock} bean is present (flag 6 — for §10 time transitions). REQ-O-014.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    // task 1.5: :shared gained spring-boot-starter-data-jpa (the JPA entity layer), which
    // propagates
    // transitively to :worker (worker -> shared) and would activate DataSourceAutoConfiguration.
    // This
    // skeleton test boots DB-less (probes + Clock, no persistence), so JPA/DataSource autoconfig is
    // excluded here; the worker wires a real datasource when it consumes the sync-record repo
    // (§10).
    properties =
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration")
@ActiveProfiles("local")
class WcSyncWorkerApplicationTest {

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
  void bootsWithoutSqsOrGraphEnv_andHasClockBean() {
    // No SQS/Graph env is set and no listener is wired yet; a green context proves a clean boot.
    assertThat(clock).isNotNull();
  }
}
