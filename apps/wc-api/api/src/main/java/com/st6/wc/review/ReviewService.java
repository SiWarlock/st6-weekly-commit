package com.st6.wc.review;

import com.st6.wc.audit.AuditService;
import com.st6.wc.auth.DomainAuthorizationService;
import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.enums.PlanState;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.review.dto.ManagerReviewDto;
import com.st6.wc.review.dto.MarkReviewedRequest;
import com.st6.wc.review.mapper.ReviewMapper;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.web.IllegalStateTransitionException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manager-review writes (task 5.2, §5 E16 / §6 / §3). {@link #markReviewed} authorizes the review
 * <strong>manager-of-owner-only</strong> FIRST (the chokepoint — {@link
 * DomainAuthorizationService#authorizeReviewMutation}; the IC owner cannot mark their own review),
 * guards the plan state, derives the status server-side via {@link ReviewStatusDeriver} (never
 * client-set), stamps {@code reviewedAt} from the injected {@code Clock}, applies the optional
 * {@code summaryNote}, persists ({@code @Version}-guarded), and emits a note-body-free {@code
 * REVIEW_MARKED} audit (§15).
 */
@Service
public class ReviewService {

  private final DomainAuthorizationService authz;
  private final ManagerReviewRepository reviews;
  private final WeeklyPlanRepository plans;
  private final ReviewStatusDeriver deriver;
  private final ReviewMapper reviewMapper;
  private final AuditService auditService;
  private final Clock clock;

  public ReviewService(
      DomainAuthorizationService authz,
      ManagerReviewRepository reviews,
      WeeklyPlanRepository plans,
      ReviewStatusDeriver deriver,
      ReviewMapper reviewMapper,
      AuditService auditService,
      Clock clock) {
    this.authz = authz;
    this.reviews = reviews;
    this.plans = plans;
    this.deriver = deriver;
    this.reviewMapper = reviewMapper;
    this.auditService = auditService;
    this.clock = clock;
  }

  @Transactional
  public ManagerReviewDto markReviewed(
      UserPrincipal actor, UUID reviewId, MarkReviewedRequest req) {
    authz.authorizeReviewMutation(actor, reviewId); // chokepoint: manager-of-owner only (IC→404)
    ManagerReview review =
        reviews.findById(reviewId).orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
    WeeklyPlan plan =
        plans
            .findById(review.getWeeklyPlanId())
            .orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
    if (plan.getState() == PlanState.DRAFT) {
      throw new IllegalStateTransitionException(); // 409 — a review is markable only post-lock
    }

    int unresolvedCount = deriver.unresolvedDisputeCount(review.getWeeklyPlanId());
    review.setStatus(
        ReviewStatusDeriver.statusFor(unresolvedCount)); // server-derived, never client
    review.setReviewedAt(clock.instant());
    if (req != null && req.summaryNote() != null) {
      review.setSummaryNote(req.summaryNote());
    }
    reviews.save(review); // @Version-guarded — concurrent conflict → 409 at the handler

    auditService.record(
        "REVIEW_MARKED",
        "ManagerReview",
        review.getId(),
        actor.employeeId(),
        "Review marked",
        "{}"); // safe metadata only — no summaryNote body (§15 / REQ-S-006)
    return reviewMapper.toDto(review, unresolvedCount);
  }
}
