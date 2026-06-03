package com.st6.wc.support;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The single PostgreSQL 16 Testcontainer shared across the whole {@code :api} test suite — the JPA
 * round-trip/constraint tests ({@link AbstractJpaIntegrationTest}) and the full-context app-boot
 * tests ({@link AbstractAppBootTest}). Started once (static initializer, reaped by the
 * Testcontainers Ryuk sidecar); one container, never one-per-class (LESSONS §5: real PG16, never
 * H2; testcontainers-bom 1.21.4 governs for Docker Engine 29 compatibility).
 */
public final class SharedPostgres {

  @SuppressWarnings("resource") // singleton, reaped by Ryuk
  public static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16.13");

  static {
    INSTANCE.start();
  }

  private SharedPostgres() {}
}
