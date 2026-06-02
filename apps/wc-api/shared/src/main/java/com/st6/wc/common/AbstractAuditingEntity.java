package com.st6.wc.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code @MappedSuperclass} base exposing the four audit columns ({@code created_by/created_at/
 * updated_by/updated_at}) inherited by every persistent entity (Appendix C.2). Population
 * (who/when) is wired in Phase 1; this base only declares the shape. Lombok {@code @Getter/@Setter}
 * only — never {@code @Data} (breaks Hibernate identity).
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AbstractAuditingEntity {

  @Column(name = "created_by", updatable = false)
  private String createdBy;

  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  @Column(name = "updated_by")
  private String updatedBy;

  @Column(name = "updated_at")
  private Instant updatedAt;
}
