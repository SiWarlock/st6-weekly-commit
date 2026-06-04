package com.st6.wc.worker.support;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The single PostgreSQL 16 Testcontainer shared across the worker's JPA boot tests (Wave-2 s8 — the
 * worker now reads {@code OutlookCalendarSyncRecord}). Started once (static initializer, reaped by
 * Ryuk); real PG16, never H2 (§17 / LESSONS §5; testcontainers-bom 1.21.4 for Docker Engine 29).
 * Mirrors the {@code :api} {@code SharedPostgres} — a worker-local copy since test code can't cross
 * the module boundary.
 */
public final class WorkerPostgres {

  @SuppressWarnings("resource") // singleton, reaped by Ryuk
  public static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16.13");

  static {
    INSTANCE.start();
  }

  private WorkerPostgres() {}
}
