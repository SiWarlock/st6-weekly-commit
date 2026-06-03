package com.st6.wc.review.mapper;

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
 * overdue, even past the due date (§9). {@code allowedActions} is empty in this phase (the manager
 * MARK_REVIEWED affordance is emitted by the manager-review slice that enforces it — §15). The
 * unresolved-dispute count is passed by the caller (0 until the disputes slice wires it).
 */
@Component
public class ReviewMapper {

  private final Clock clock;

  public ReviewMapper(Clock clock) {
    this.clock = clock;
  }

  public ManagerReviewDto toDto(ManagerReview review, int unresolvedDisputeCount) {
    boolean overdue =
        review.getStatus() == ReviewStatus.NOT_REVIEWED
            && clock.instant().isAfter(review.getReviewDueAt());
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
        List.of(),
        review.getVersion());
  }
}
