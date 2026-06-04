package com.st6.wc.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

/**
 * {@code wc-sync-worker} Spring Boot entry point (task 0.5) — a separate deployable from {@code
 * wc-api} (REQ-O-014). The cross-module {@code :shared} {@code ClockConfig} is wired via {@link
 * com.st6.wc.worker.config.WorkerSharedConfig} (sibling package, outside this app's default scan).
 * No SQS listener / Graph adapter / in-process dispatcher yet — those land in a later phase.
 *
 * <p>The worker is <strong>DB-less in every profile in Wave-1</strong>: {@code :shared} carries the
 * {@code spring-boot-starter-data-jpa} entity layer (task 1.5), which would transitively activate
 * {@code DataSourceAutoConfiguration} + {@code HibernateJpaAutoConfiguration} here — but the worker
 * mounts graph-only secrets (no {@code spring.datasource.*}), so that auto-config would crashloop
 * in the deployed {@code aws} profile ("Failed to determine a suitable driver class"). Excluding
 * both at the production level (not a test-only property — LESSONS §9) keeps every profile DB-less.
 * The Wave-2 SQS-consumer slice (which reloads {@code SyncRecord} by id) removes this exclude +
 * wires the worker's datasource — a deliberate one-line diff.
 */
@SpringBootApplication(
    exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
public class WcSyncWorkerApplication {

  public static void main(String[] args) {
    SpringApplication.run(WcSyncWorkerApplication.class, args);
  }
}
