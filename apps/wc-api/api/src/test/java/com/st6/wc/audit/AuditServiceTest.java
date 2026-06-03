package com.st6.wc.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.st6.wc.audit.repo.AuditEventRepository;
import com.st6.wc.support.AbstractJpaIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * {@code AuditService} write proof (task 2.3) against a real PG16 (reuses {@link
 * AbstractJpaIntegrationTest}). The service is constructed directly (the {@code @DataJpaTest} slice
 * doesn't scan {@code @Service}s) with an {@code AuditEventRepository} + a fixed {@link Clock}.
 * Pins: one row persisted, {@code created_at} set from the injected clock (no JPA auditing
 * populator yet), nullable actor (SYSTEM), and safe-only metadata (rule #7 — no token/PII/raw-input
 * echo).
 */
class AuditServiceTest extends AbstractJpaIntegrationTest {

  private static final Instant FIXED = Instant.parse("2026-06-02T12:00:00Z");

  @Autowired private AuditEventRepository auditEvents;
  @Autowired private TestEntityManager em;

  @Test
  void audit_service_persists_safe_event() throws Exception {
    AuditService service = new AuditService(auditEvents, Clock.fixed(FIXED, ZoneOffset.UTC));

    service.record(
        "DEMO_AUTH_REJECTED",
        "Authentication",
        null,
        null,
        "Demo identity header rejected: demo auth disabled",
        "{\"reason\":\"demo_auth_disabled\"}");
    em.flush();
    em.clear();

    assertThat(auditEvents.count()).isEqualTo(1);
    AuditEvent event = auditEvents.findAll().get(0);
    assertThat(event.getAction()).isEqualTo("DEMO_AUTH_REJECTED");
    assertThat(event.getEntityType()).isEqualTo("Authentication");
    assertThat(event.getActorEmployeeId()).isNull(); // SYSTEM
    assertThat(event.getEntityId()).isNull();
    assertThat(event.getCreatedAt()).isEqualTo(FIXED); // service-set from the injected Clock
    // jsonb normalizes whitespace, so parse-and-compare the safe metadata (not raw string
    // equality).
    assertThat(new ObjectMapper().readTree(event.getMetadataJson()).get("reason").asText())
        .isEqualTo("demo_auth_disabled");
  }
}
