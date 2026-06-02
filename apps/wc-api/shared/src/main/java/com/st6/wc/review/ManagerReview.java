package com.st6.wc.review;

import com.st6.wc.common.PersistableUuidEntity;
import com.st6.wc.enums.ReviewStatus;
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
 * Manager review — mutable lifecycle entity (Appendix A / §3 / §4), maps {@code manager_review}.
 * The stored {@code status} vocabulary has NO {@code OVERDUE} (safety rule #6: OVERDUE is derived
 * at read time, never persisted). {@code @Version} via {@link PersistableUuidEntity}.
 */
@Entity
@Table(name = "manager_review")
@Getter
@Setter
public class ManagerReview extends PersistableUuidEntity {

  @Column(nullable = false)
  private UUID weeklyPlanId;

  @Column(nullable = false)
  private UUID managerEmployeeId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ReviewStatus status;

  @Column(nullable = false)
  private Instant reviewDueAt;

  private Instant reviewedAt;

  private String summaryNote;
}
