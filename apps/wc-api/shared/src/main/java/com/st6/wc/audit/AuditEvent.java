package com.st6.wc.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Audit event — append-only audit record (Appendix A / §4 / §15), maps {@code audit_event}. NOT an
 * {@code AbstractAuditingEntity} (no audit quartet, no {@code @Version}): it owns a single non-null
 * {@code createdAt}. {@code actorEmployeeId} is nullable (SYSTEM actor). {@code metadataJson} is a
 * {@code jsonb} column carrying safe-only metadata (safety rule #7 — no secrets/PII), mapped via
 * Hibernate's {@code SqlTypes.JSON}.
 */
@Entity
@Table(name = "audit_event")
@Getter
@Setter
public class AuditEvent {

  @Id
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  private UUID actorEmployeeId;

  @Column(nullable = false)
  private String action;

  @Column(nullable = false)
  private String entityType;

  private UUID entityId;

  @Column(nullable = false)
  private String summary;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata_json", columnDefinition = "jsonb")
  private String metadataJson;

  @Column(nullable = false)
  private Instant createdAt;
}
