package com.st6.wc.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Deployment-fidelity boot test (task 092): boots {@code WcSyncWorkerApplication} under
 * {@code @ActiveProfiles("aws")} — the EXACT profile the worker Deployment runs ({@code
 * SPRING_PROFILES_ACTIVE=aws}) — with NO {@code spring.autoconfigure.exclude} test property, and
 * proves the worker is DB-less by construction:
 *
 * <ul>
 *   <li>the context loads + the k8s readiness probe is UP (the crashloop path is the boot under
 *       {@code aws} with the graph-only secret mount — no {@code spring.datasource.*});
 *   <li><strong>no {@code DataSource} bean and no {@code EntityManagerFactory} bean</strong> exist
 *       — the JPA/datasource auto-config (transitive via {@code :shared}'s data-jpa starter, task
 *       1.5) is excluded in PRODUCTION on the app class, not by a test property (LESSONS §9). This
 *       bean absence makes the Wave-2 worker-datasource wiring a deliberate, visible change.
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("aws")
class WorkerAwsProfileBootTest {

  @Autowired TestRestTemplate rest;
  @Autowired ApplicationContext ctx;
  @Autowired Clock clock;

  @Test
  void worker_awsProfile_bootsDbLess() {
    ResponseEntity<String> r = rest.getForEntity("/actuator/health/readiness", String.class);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(r.getBody()).contains("UP");
    assertThat(clock).as(":shared Clock bean present in the aws-profile worker").isNotNull();
  }

  @Test
  void worker_hasNoDataSourceOrJpaBeans() {
    assertThat(ctx.getBeanNamesForType(DataSource.class))
        .as("worker has no DataSource bean (DataSourceAutoConfiguration excluded in production)")
        .isEmpty();
    // jakarta.persistence is a runtime-only transitive dep of :shared (not on the worker's compile
    // classpath), so assert by the conventional autoconfig bean name rather than the type.
    assertThat(ctx.containsBean("entityManagerFactory"))
        .as("worker has no EntityManagerFactory bean (HibernateJpaAutoConfiguration excluded)")
        .isFalse();
  }
}
