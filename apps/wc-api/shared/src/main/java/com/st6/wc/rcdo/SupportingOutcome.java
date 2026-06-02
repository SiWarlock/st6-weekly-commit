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
 * Supporting Outcome — leaf of the RCDO hierarchy, child of a {@link DefiningObjective} (Appendix A
 * / §3 / §4), maps {@code supporting_outcome}. The mandatory link target for every locked planned
 * commitment (safety rule #1), enforced by services in Phase 3. Audited, not versioned (inline
 * {@code @Id} + {@link AbstractAuditingEntity}).
 */
@Entity
@Table(name = "supporting_outcome")
@Getter
@Setter
public class SupportingOutcome extends AbstractAuditingEntity {

  @Id
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(nullable = false)
  private UUID definingObjectiveId;

  @Column(nullable = false)
  private String title;

  private String description;

  @Column(nullable = false)
  private boolean active;
}
