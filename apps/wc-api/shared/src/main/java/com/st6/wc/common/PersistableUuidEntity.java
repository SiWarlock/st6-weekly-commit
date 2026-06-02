package com.st6.wc.common;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code @MappedSuperclass} base providing a UUID primary key + a {@code @Version} optimistic-lock
 * token, on top of the audit columns (Appendix C.2). Concrete domain entities (Phase 1) extend this
 * to inherit PK + version + audit from a single base. Lombok {@code @Getter/@Setter} only.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class PersistableUuidEntity extends AbstractAuditingEntity {

  @Id
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;
}
