package com.st6.wc.relationship;

import com.st6.wc.common.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Manager → direct-report relationship (Appendix A / §4 / §6), maps {@code manager_relationship}.
 * The single-active-manager-per-report invariant (§6) is the V2 partial unique index; this is the
 * structural mapping. Audited, not versioned (inline {@code @Id} + {@link AbstractAuditingEntity}).
 */
@Entity
@Table(name = "manager_relationship")
@Getter
@Setter
public class ManagerRelationship extends AbstractAuditingEntity {

  @Id
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(nullable = false)
  private UUID managerEmployeeId;

  @Column(nullable = false)
  private UUID directReportEmployeeId;

  @Column(nullable = false)
  private boolean active;
}
