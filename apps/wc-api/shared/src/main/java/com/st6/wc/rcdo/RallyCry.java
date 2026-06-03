package com.st6.wc.rcdo;

import com.st6.wc.common.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Rally Cry — top of the read-only seeded RCDO strategy hierarchy (Appendix A / §3 / §4), maps
 * {@code rally_cry}. Audited, not versioned (inline {@code @Id} + {@link AbstractAuditingEntity}).
 */
@Entity
@Table(name = "rally_cry")
@Getter
@Setter
public class RallyCry extends AbstractAuditingEntity {

  @Id
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(nullable = false)
  private String title;

  private String description;

  @Column(nullable = false)
  private boolean active;
}
