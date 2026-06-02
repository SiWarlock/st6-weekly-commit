package com.st6.wc.projection;

import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.ReviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Manager plan summary — synchronous projection read-model (Appendix A / §9), maps {@code
 * manager_plan_summary}. Recomputed wholesale by the ProjectionService (later phase): NO audit
 * quartet, NO {@code @Version} — owns a single non-null {@code updatedAt}. {@code planState}/{@code
 * reviewStatus} are denormalized {@code @Enumerated(STRING)} mirrors over CHECK-less VARCHAR
 * columns (the source-of-truth CHECKs live on {@code weekly_plan.state}/{@code
 * manager_review.status}). {@code isReviewOverdue} is the §9 projection column (NOT safety rule
 * #6).
 */
@Entity
@Table(name = "manager_plan_summary")
@Getter
@Setter
public class ManagerPlanSummary {

  @Id
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(nullable = false)
  private UUID managerEmployeeId;

  @Column(nullable = false)
  private UUID employeeId;

  @Column(nullable = false)
  private UUID weeklyPlanId;

  @Column(nullable = false)
  private LocalDate weekStartDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PlanState planState;

  @Enumerated(EnumType.STRING)
  private ReviewStatus reviewStatus;

  private Instant reviewDueAt;

  @Column(name = "is_review_overdue", nullable = false)
  private boolean reviewOverdue;

  @Column(nullable = false)
  private int plannedCount;

  @Column(nullable = false)
  private int unplannedCount;

  @Column(nullable = false)
  private int misalignedCount;

  @Column(nullable = false)
  private int needsReviewCount;

  @Column(nullable = false)
  private int blockedCount;

  @Column(nullable = false)
  private int carryForwardCount;

  @Column(nullable = false)
  private int unresolvedDisputeCount;

  @Column(nullable = false)
  private Instant updatedAt;
}
