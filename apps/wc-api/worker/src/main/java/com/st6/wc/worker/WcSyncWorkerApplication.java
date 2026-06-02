package com.st6.wc.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * {@code wc-sync-worker} Spring Boot entry point (task 0.5) — a separate deployable from {@code
 * wc-api} (REQ-O-014). The cross-module {@code :shared} {@code ClockConfig} is wired via {@link
 * com.st6.wc.worker.config.WorkerSharedConfig} (sibling package, outside this app's default scan).
 * No SQS listener / Graph adapter / in-process dispatcher yet — those land in a later phase.
 */
@SpringBootApplication
public class WcSyncWorkerApplication {

  public static void main(String[] args) {
    SpringApplication.run(WcSyncWorkerApplication.class, args);
  }
}
