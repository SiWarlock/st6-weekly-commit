package com.st6.wc.projection;

import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.repo.ManagerReviewRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The single source-loading projection-refresh entrypoint (task 6.3a, §8/§9) — extracted from the 5
 * mutation sites that kept this block byte-identical (lock 3.5, start/close 4.2/4.5, carry-forward
 * 4.4, commitment-outcome 4.1). Resolves the manager from the <strong>plan owner</strong>'s active
 * relationship, <strong>no-ops</strong> when there is no active manager OR no review row (nothing
 * to project, §28), else loads the plan's commitments + review from source and delegates the §9
 * count derivation to {@link ProjectionService#recompute}. Called inside each mutation's
 * {@code @Version} transaction; also the entrypoint the 6.7 rebuild job reuses (it has only plan
 * rows). The count derivation itself lives in {@link ProjectionService} (task 6.2).
 */
@Service
public class ProjectionRefresher {

  private final ManagerRelationshipRepository relationships;
  private final ManagerReviewRepository reviews;
  private final WeeklyCommitmentRepository commitments;
  private final ProjectionService projectionService;

  public ProjectionRefresher(
      ManagerRelationshipRepository relationships,
      ManagerReviewRepository reviews,
      WeeklyCommitmentRepository commitments,
      ProjectionService projectionService) {
    this.relationships = relationships;
    this.reviews = reviews;
    this.commitments = commitments;
    this.projectionService = projectionService;
  }

  /**
   * Synchronously refresh the manager projection for {@code plan} from source. A no-op when the
   * plan owner has no active manager or no review row yet (the §28 skip — nothing to project).
   */
  public void recomputeForPlan(WeeklyPlan plan) {
    UUID managerId =
        relationships
            .findByDirectReportEmployeeIdAndActiveTrue(plan.getEmployeeId())
            .map(ManagerRelationship::getManagerEmployeeId)
            .orElse(null);
    if (managerId == null) {
      return; // no active manager → no projection (§28)
    }
    final UUID resolvedManagerId = managerId;
    reviews
        .findByWeeklyPlanId(plan.getId())
        .ifPresent(
            review -> {
              List<WeeklyCommitment> all = commitments.findByWeeklyPlanIdOrderByIdAsc(plan.getId());
              projectionService.recompute(plan, resolvedManagerId, all, review);
            });
  }
}
