package com.st6.wc.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for the full-context {@code @SpringBootTest} app-boot tests (task 2.3, Option A). From Phase
 * 2 the api app is <strong>genuinely DB-dependent in every mode</strong> — demo mode resolves the
 * demo id + writes audits against PG ({@code DemoAuthFilter}/{@code AuditService}); real mode
 * resolves {@code external_subject} (2.4) + writes denial audits (2.5). So the app-boot tests no
 * longer run DB-less: they boot against the shared {@link SharedPostgres} container with Flyway
 * V1–V3 + {@code ddl-auto=validate}, exactly like the JPA tests.
 *
 * <p>Subclasses declare their own {@code @SpringBootTest(webEnvironment)} + {@code @ActiveProfiles}
 * and keep excluding only the <em>security</em> autoconfig (the real {@code SecurityFilterChain} is
 * task 2.6) — they no longer exclude DataSource/Hibernate.
 */
public abstract class AbstractAppBootTest {

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", SharedPostgres.INSTANCE::getJdbcUrl);
    registry.add("spring.datasource.username", SharedPostgres.INSTANCE::getUsername);
    registry.add("spring.datasource.password", SharedPostgres.INSTANCE::getPassword);
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
  }
}
