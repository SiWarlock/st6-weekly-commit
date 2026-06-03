package com.st6.wc.review.dto;

import com.st6.wc.action.AllowedAction;
import com.st6.wc.enums.ReviewStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Manager-review across the API boundary (Appendix B.7; nested in {@link
 * com.st6.wc.plan.dto.WeeklyPlanDto}, null while {@code DRAFT}). A record, never an entity.
 *
 * <p>Introduced at task 3.3a only as the <strong>type</strong> {@code WeeklyPlanDto.managerReview}
 * needs to compile — 3.3a always maps it null (DRAFT shells have no review row). The entity→DTO
 * {@code ReviewMapper} + the derived {@code isOverdue} ({@code now > reviewDueAt AND NOT_REVIEWED},
 * injectable {@code Clock}, §3/§17) land at task 3.5 (the lock creates the review row). {@code
 * isOverdue} is derived, never stored (no {@code ReviewStatus.OVERDUE}).
 */
public record ManagerReviewDto(
    UUID id,
    UUID weeklyPlanId,
    UUID managerEmployeeId,
    ReviewStatus status,
    Instant reviewDueAt,
    boolean isOverdue,
    Instant reviewedAt,
    String summaryNote,
    int unresolvedDisputeCount,
    List<AllowedAction> allowedActions,
    long version) {

  public ManagerReviewDto {
    allowedActions = List.copyOf(allowedActions);
  }
}
