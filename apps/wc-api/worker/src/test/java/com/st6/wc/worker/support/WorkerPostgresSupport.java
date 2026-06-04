package com.st6.wc.worker.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for the worker's full-context boot tests (Wave-2 s8): boots against the shared {@link
 * WorkerPostgres} container with Flyway re-enabled (V1–V4 from {@code :shared}) + {@code
 * ddl-auto=validate} — so the worker's JPA entity view is validated against the migration-owned
 * schema, exactly like the {@code :api} persistence harness. The worker datasource comes from here
 * in tests (the real one comes from the {@code aws} configtree mount, absent in CI).
 */
public abstract class WorkerPostgresSupport {

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", WorkerPostgres.INSTANCE::getJdbcUrl);
    registry.add("spring.datasource.username", WorkerPostgres.INSTANCE::getUsername);
    registry.add("spring.datasource.password", WorkerPostgres.INSTANCE::getPassword);
    // Re-enable Flyway in the test to create the schema (base config keeps it off);
    // ddl-auto=validate
    // then validates the entities against it. Migrations resolve from :shared (db/migration V1–V4).
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.datasource.hikari.maximum-pool-size", () -> "2"); // §29 test pool cap
  }
}
