package com.st6.wc.review;

import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.review.dto.ManagerReviewDto;
import com.st6.wc.review.dto.MarkReviewedRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/manager/reviews/{reviewId}/mark-reviewed} (E16, §5 / §6). Thin: {@link
 * ReviewService} authorizes manager-of-owner-only first (the chokepoint), so any
 * IC-self/non-direct-manager/missing id → codeless 404. The {@code @AuthenticationPrincipal} is the
 * 2.6-resolved caller; the body is optional ({@code summaryNote} only). Returns a DTO, never an
 * entity.
 */
@RestController
public class ReviewController {

  private final ReviewService reviewService;

  public ReviewController(ReviewService reviewService) {
    this.reviewService = reviewService;
  }

  @PostMapping("/api/manager/reviews/{reviewId}/mark-reviewed")
  public ManagerReviewDto markReviewed(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable("reviewId") UUID reviewId,
      @Valid @RequestBody(required = false) MarkReviewedRequest req) {
    return reviewService.markReviewed(principal, reviewId, req);
  }
}
