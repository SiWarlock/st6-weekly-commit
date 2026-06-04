package com.st6.wc.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import com.st6.wc.worker.support.WorkerPostgresSupport;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Deployment-fidelity boot test (Wave-2 s8 — inverts the 092 bean-ABSENCE pin): boots {@code
 * WcSyncWorkerApplication} under {@code @ActiveProfiles("aws")} (the profile the worker Deployment
 * runs) with a real Testcontainers datasource ({@link WorkerPostgresSupport}) and proves the worker
 * is now JPA-backed:
 *
 * <ul>
 *   <li>a {@code DataSource} + {@code EntityManagerFactory} bean exist (the 092 exclude is GONE —
 *       the worker reloads {@code OutlookCalendarSyncRecord});
 *   <li>the {@code OutlookCalendarSyncRecordRepository} is injectable (the {@code
 *       WorkerSharedConfig} {@code @EnableJpaRepositories} scan reaches the shared repo).
 * </ul>
 *
 * <p>{@code app.sqs.queue-url} is set to a dummy + {@code
 * spring.cloud.aws.sqs.listener.auto-startup =false} so the {@code @ConditionalOnProperty}-gated
 * {@code SyncMessageListener} bean wires but never polls a (nonexistent) queue — the test exercises
 * only the JPA wiring + boot.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.sqs.queue-url=https://sqs.us-east-1.amazonaws.com/000000000000/wc-sync-test",
      "spring.cloud.aws.sqs.listener.auto-startup=false"
    })
@ActiveProfiles("aws")
class WorkerAwsProfileBootTest extends WorkerPostgresSupport {

  @Autowired ApplicationContext ctx;
  @Autowired OutlookCalendarSyncRecordRepository syncRecords;

  @Test
  void worker_awsProfile_hasDataSourceAndJpa() {
    assertThat(ctx.getBeanNamesForType(DataSource.class))
        .as("worker now has a DataSource (the 092 exclude is removed — it reloads the SyncRecord)")
        .isNotEmpty();
    assertThat(ctx.containsBean("entityManagerFactory"))
        .as("worker now has an EntityManagerFactory (JPA auto-config active)")
        .isTrue();
  }

  @Test
  void worker_syncRecordRepository_isInjectable() {
    // the @EnableJpaRepositories("com.st6.wc.sync.repo") scan reaches the shared repo + it queries
    // the migration-created schema (ddl-auto=validate already proved the entity↔schema fidelity).
    assertThat(syncRecords).isNotNull();
    assertThat(syncRecords.count()).isZero();
  }
}
