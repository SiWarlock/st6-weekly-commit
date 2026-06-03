package com.st6.wc.dispute;

import com.st6.wc.common.PersistableUuidEntity;
import com.st6.wc.enums.DisputeStatus;
import com.st6.wc.enums.FlagType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Alignment dispute — mutable lifecycle entity (Appendix A / §3 / §4), maps {@code
 * alignment_dispute}. The one-unresolved-dispute-per-commitment invariant (safety rule #6) is the
 * V2 partial unique index; this is the structural mapping. {@code @Version} via {@link
 * PersistableUuidEntity}.
 */
@Entity
@Table(name = "alignment_dispute")
@Getter
@Setter
public class AlignmentDispute extends PersistableUuidEntity {

  @Column(nullable = false)
  private UUID commitmentId;

  @Column(nullable = false)
  private UUID managerEmployeeId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DisputeStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private FlagType flagType;

  @Column(nullable = false)
  private String managerNote;

  private String icResponse;

  private Instant resolvedAt;
}
