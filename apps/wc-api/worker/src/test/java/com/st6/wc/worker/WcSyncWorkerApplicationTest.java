package com.st6.wc.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.st6.wc.worker.support.WorkerPostgresSupport;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots {@code WcSyncWorkerApplication} (the separate deployable) under {@code local} and asserts
 * the k8s probes are UP and the {@code :shared} {@code Clock} bean is present (flag 6 — for §10
 * time transitions). REQ-O-014.
 *
 * <p>Wave-2 s8: the worker is now JPA-active (the 092 exclude is gone — it reloads {@code
 * OutlookCalendarSyncRecord}), so the boot needs a datasource — supplied by the Testcontainers
 * {@link WorkerPostgresSupport} harness (real PG16, never H2). No {@code app.sqs.queue-url} is set,
 * so the {@code @ConditionalOnProperty}-gated {@code SyncMessageListener} stays inactive (no queue
 * poll).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class WcSyncWorkerApplicationTest extends WorkerPostgresSupport {

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
