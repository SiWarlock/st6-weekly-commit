package com.st6.wc.support;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Shared JPA + Testcontainers PostgreSQL 16 base for the Phase-1 entity round-trip/mapping tests
 * (task 1.5). Reuses the 1.2/1.3/1.4 Testcontainers harness posture (LESSONS §5: real PG16, never
 * H2; testcontainers-bom 1.21.4 governs for Docker Engine 29 compatibility).
 *
 * <p>Harness shape (Step-2.5 question #5): a sliced {@link DataJpaTest} (JPA layer only — no web /
 * actuator) against a <em>singleton</em> PG16 container shared across all JPA test classes (started
 * once in the static initializer, reaped by Ryuk — avoids one container per class). Flyway V1–V3
 * runs first (base config sets {@code spring.flyway.enabled=false}; re-enabled here), then
 * Hibernate runs with {@code ddl-auto=validate} — making context boot itself the strongest
 * entity↔DDL fidelity proof (Appendix A / §4).
 *
 * <p>Entities + repositories live in {@code :shared} under {@code com.st6.wc.*}. A sliced
 * {@code @DataJpaTest} derives its auto-config base package from the <em>test</em> class's package,
 * not {@code WcApiApplication}'s — so the explicit {@link EntityScan}/{@link EnableJpaRepositories}
 * over {@code com.st6.wc} are required here (the "no {@code @EntityScan} needed" premise holds only
 * for a full {@code @SpringBootTest} that boots {@code WcApiApplication}). Datasource is wired via
 * {@link DynamicPropertySource} (rather than {@code @ServiceConnection}) to avoid pulling in the
 * extra {@code spring-boot-testcontainers} module and to match the landed raw-Testcontainers tests.
 */
@DataJpaTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.open-in-view=false"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan("com.st6.wc")
@EnableJpaRepositories("com.st6.wc")
public abstract class AbstractJpaIntegrationTest {

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", SharedPostgres.INSTANCE::getJdbcUrl);
    registry.add("spring.datasource.username", SharedPostgres.INSTANCE::getUsername);
    registry.add("spring.datasource.password", SharedPostgres.INSTANCE::getPassword);
  }
}
