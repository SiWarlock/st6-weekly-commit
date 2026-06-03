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
 * Defining Objective — second tier of the RCDO hierarchy, child of a {@link RallyCry} (Appendix A /
 * §3 / §4), maps {@code defining_objective}. Audited, not versioned (inline {@code @Id} + {@link
 * AbstractAuditingEntity}).
 */
@Entity
@Table(name = "defining_objective")
@Getter
@Setter
public class DefiningObjective extends AbstractAuditingEntity {

  @Id
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(nullable = false)
  private UUID rallyCryId;

  @Column(nullable = false)
  private String title;

  private String description;

  @Column(nullable = false)
  private boolean active;
}
