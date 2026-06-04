package com.st6.wc.plan.mapper;

import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.dto.WeeklyCommitmentDto;
import com.st6.wc.commitment.mapper.CommitmentMapper;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.plan.AllowedActionResolver;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.dto.WeeklyPlanDto;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.dto.ManagerReviewDto;
import com.st6.wc.review.mapper.ReviewMapper;
import com.st6.wc.review.repo.ManagerReviewRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Assembles a {@link WeeklyPlanDto} (Appendix B.5) from the plan + its commitments (task 3.3a):
 * delegates each {@link WeeklyCommitmentDto} to {@link CommitmentMapper} (the single B.6 shape +
 * RC→DO→SO breadcrumb source — extracted at 3.4a, reused by the commitment endpoints too) and
 * stamps the server-authoritative {@code allowedActions[]} via {@link AllowedActionResolver}. DTOs
 * never expose the entity (forbidden-pattern #3). {@code managerReview} is null while {@code DRAFT}
 * (3.5 wires the real review mapping).
 */
@Component
public class PlanMapper {

  private final CommitmentMapper commitmentMapper;
  private final AllowedActionResolver allowedActionResolver;
  private final ManagerReviewRepository reviews;
  private final ReviewMapper reviewMapper;
  private final ManagerRelationshipRepository relationships;

  public PlanMapper(
      CommitmentMapper commitmentMapper,
      AllowedActionResolver allowedActionResolver,
      ManagerReviewRepository reviews,
      ReviewMapper reviewMapper,
      ManagerRelationshipRepository relationships) {
    this.commitmentMapper = commitmentMapper;
    this.allowedActionResolver = allowedActionResolver;
    this.reviews = reviews;
    this.reviewMapper = reviewMapper;
    this.relationships = relationships;
  }

  public WeeklyPlanDto toWeeklyPlanDto(
      WeeklyPlan plan,
      String employeeDisplayName,
      List<WeeklyCommitment> commitments,
      UUID actorEmployeeId) {

    // viewerIsDirectManager: determined ONCE per plan read (one relationship lookup, 5.5b) — the
    // viewer is the plan owner's active direct manager. Threaded to the per-commitment +
    // per-dispute
    // affordances (OPEN_DISPUTE / RESOLVE_DISPUTE). The owning IC is never their own manager →
    // false.
    boolean viewerIsDirectManager =
        relationships
            .findByDirectReportEmployeeIdAndActiveTrue(plan.getEmployeeId())
            .map(r -> r.getManagerEmployeeId().equals(actorEmployeeId))
            .orElse(false);

    List<WeeklyCommitmentDto> commitmentDtos =
        commitments.stream()
            .map(
                c ->
                    commitmentMapper.toDto(
                        c, plan, actorEmployeeId, viewerIsDirectManager)) // 4.4b/5.5b affordances
            .toList();
    int plannedCount =
        (int)
            commitments.stream()
                .filter(c -> c.getCommitmentKind() == CommitmentKind.PLANNED)
                .count();
    int unplannedCount = commitments.size() - plannedCount;

    // The review exists once the plan is LOCKED+ (3.5); null while DRAFT. unresolvedDisputeCount is
    // 0 until the disputes slice wires it. RC→DO→SO/review knowledge stays in the review service.
    ManagerReviewDto managerReview =
        reviews
            .findByWeeklyPlanId(plan.getId())
            .map(review -> reviewMapper.toDto(review, 0))
            .orElse(null);

    return new WeeklyPlanDto(
        plan.getId(),
        plan.getEmployeeId(),
        employeeDisplayName,
        plan.getWeekStartDate(),
        plan.getWeekEndDate(),
        plan.getState(),
        plan.getGeneratedAt(),
        plan.getLockedAt(),
        plan.getReconciliationStartedAt(),
        plan.getReconciledAt(),
        plannedCount,
        unplannedCount,
        commitmentDtos,
        managerReview,
        allowedActionResolver.planActions(actorEmployeeId, plan, commitments),
        plan.getVersion());
  }
}
