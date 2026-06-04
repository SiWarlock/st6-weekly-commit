package com.st6.wc.review.mapper;

import com.st6.wc.action.AllowedAction;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.review.ManagerReview;
import com.st6.wc.review.dto.ManagerReviewDto;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps a {@link ManagerReview} entity to its {@link ManagerReviewDto} (task 3.5, Appendix B.7) with
 * a <strong>derived {@code isOverdue}</strong> computed at read time over the injectable {@link
 * Clock} — {@code now > reviewDueAt AND status = NOT_REVIEWED} (§3 / rule #6); never stored (there
 * is no {@code ReviewStatus.OVERDUE}). {@code REVIEWED} / {@code REVIEWED_WITH_DISPUTES} are never
 * overdue, even past the due date (§9). {@code allowedActions} emits {@code MARK_REVIEWED} iff the
 * caller passes {@code canMarkReviewed} (task 6.8 — the active direct manager viewing a markable
 * {@code NOT_REVIEWED} review; the §31 subset of E16's enforcement, computed in {@code PlanMapper}
 * via {@code AllowedActionResolver.canMarkReviewed}). The unresolved-dispute count is passed by the
 * caller (derived from the plan read's loaded nested disputes, 6.8).
 */
@Component
public class ReviewMapper {

  private final Clock clock;

  public ReviewMapper(Clock clock) {
    this.clock = clock;
  }

  public ManagerReviewDto toDto(
      ManagerReview review, int unresolvedDisputeCount, boolean canMarkReviewed) {
    boolean overdue =
        review.getStatus() == ReviewStatus.NOT_REVIEWED
            && clock.instant().isAfter(review.getReviewDueAt());
    List<AllowedAction> allowedActions =
        canMarkReviewed ? List.of(AllowedAction.MARK_REVIEWED) : List.of();
    return new ManagerReviewDto(
        review.getId(),
        review.getWeeklyPlanId(),
        review.getManagerEmployeeId(),
        review.getStatus(),
        review.getReviewDueAt(),
        overdue,
        review.getReviewedAt(),
        review.getSummaryNote(),
        unresolvedDisputeCount,
        allowedActions,
        review.getVersion());
  }
}
