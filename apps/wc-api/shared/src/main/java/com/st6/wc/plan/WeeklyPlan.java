package com.st6.wc.plan;

import com.st6.wc.common.PersistableUuidEntity;
import com.st6.wc.enums.PlanState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Weekly plan — the mutable lifecycle aggregate root (Appendix A / §3 / §4). Maps the {@code
 * weekly_plan} V1 table; carries {@code @Version} (via {@link PersistableUuidEntity}) for
 * optimistic locking on the {@code DRAFT→LOCKED→RECONCILING→RECONCILED} transitions (enforced by
 * services in later phases — this is the structural mapping only).
 */
@Entity
@Table(name = "weekly_plan")
@Getter
@Setter
public class WeeklyPlan extends PersistableUuidEntity {

  @Column(nullable = false)
  private UUID employeeId;

  @Column(nullable = false)
  private LocalDate weekStartDate;

  @Column(nullable = false)
  private LocalDate weekEndDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PlanState state;

  private Instant generatedAt;
  private Instant lockedAt;
  private Instant reconciliationStartedAt;
  private Instant reconciledAt;
}
