package com.st6.wc.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * {@code wc-sync-worker} Spring Boot entry point (task 0.5) — a separate deployable from {@code
 * wc-api} (REQ-O-014). The cross-module {@code :shared} {@code ClockConfig} + the JPA entity/repo
 * scan are wired via {@link com.st6.wc.worker.config.WorkerSharedConfig} (the shared packages sit
 * outside this app's {@code com.st6.wc.worker} default scan).
 *
 * <p>The worker reloads {@code OutlookCalendarSyncRecord} from the DB (Wave-2 s8), so the JPA/
 * datasource auto-config is NO LONGER excluded (the 092 Wave-1 exclude is removed — the worker now
 * has a datasource via the {@code aws} configtree mount; tests use a Testcontainers PG). It still
 * never migrates (flyway off; {@code ddl-auto=validate}).
 */
@SpringBootApplication
public class WcSyncWorkerApplication {

  public static void main(String[] args) {
    SpringApplication.run(WcSyncWorkerApplication.class, args);
  }
}
