package com.st6.wc.projection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.ManagerReview;
import com.st6.wc.review.repo.ManagerReviewRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit proof of {@link ProjectionRefresher} (task 6.3a) — the source-loading projection-refresh
 * entrypoint extracted from the 5 mutation sites (§8/§9, the §28 no-manager/no-review skip).
 * Resolves the manager from the <strong>plan owner</strong>, no-ops when there is no active manager
 * or no review row, else loads the plan's commitments + review and delegates to {@link
 * ProjectionService#recompute}. Repos + {@code ProjectionService} mocked; the count derivation is
 * proven in {@code ProjectionServiceTest}.
 */
class ProjectionRefresherTest {

  private final ManagerRelationshipRepository relationships =
      mock(ManagerRelationshipRepository.class);
  private final ManagerReviewRepository reviews = mock(ManagerReviewRepository.class);
  private final WeeklyCommitmentRepository commitments = mock(WeeklyCommitmentRepository.class);
  private final ProjectionService projectionService = mock(ProjectionService.class);
  private final ProjectionRefresher refresher =
      new ProjectionRefresher(relationships, reviews, commitments, projectionService);

  private static final UUID MGR = UUID.randomUUID();
  private static final UUID IC = UUID.randomUUID();
  private static final UUID PLAN = UUID.randomUUID();

  private static WeeklyPlan plan() {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(PLAN);
    p.setEmployeeId(IC);
    p.setWeekStartDate(LocalDate.of(2026, 6, 1));
    p.setState(PlanState.LOCKED);
    return p;
  }

  private static ManagerRelationship relationship() {
    ManagerRelationship r = new ManagerRelationship();
    r.setId(UUID.randomUUID());
    r.setManagerEmployeeId(MGR);
    r.setDirectReportEmployeeId(IC);
    r.setActive(true);
    return r;
  }

  private static ManagerReview review() {
    ManagerReview r = new ManagerReview();
    r.setId(UUID.randomUUID());
    r.setWeeklyPlanId(PLAN);
    r.setManagerEmployeeId(MGR);
    r.setStatus(ReviewStatus.NOT_REVIEWED);
    r.setReviewDueAt(Instant.parse("2026-06-08T22:00:00Z"));
    return r;
  }

  // --- owner has an active manager + a review → loads source + recomputes with those args ----
  @Test
  void recomputeForPlan_withManagerAndReview_loadsSourceAndRecomputes() {
    WeeklyPlan plan = plan();
    ManagerReview review = review();
    List<WeeklyCommitment> loaded = List.of(new WeeklyCommitment(), new WeeklyCommitment());
    when(relationships.findByDirectReportEmployeeIdAndActiveTrue(IC))
        .thenReturn(Optional.of(relationship()));
    when(reviews.findByWeeklyPlanId(PLAN)).thenReturn(Optional.of(review));
    when(commitments.findByWeeklyPlanIdOrderByIdAsc(PLAN)).thenReturn(loaded);

    refresher.recomputeForPlan(plan);

    verify(projectionService).recompute(eq(plan), eq(MGR), eq(loaded), eq(review));
  }

  // --- §28: no active manager → no projection (no load, no recompute) ----
  @Test
  void recomputeForPlan_noActiveManager_isNoOp() {
    when(relationships.findByDirectReportEmployeeIdAndActiveTrue(IC)).thenReturn(Optional.empty());

    refresher.recomputeForPlan(plan());

    verify(reviews, never()).findByWeeklyPlanId(any());
    verify(projectionService, never()).recompute(any(), any(), any(), any());
  }

  // --- active manager but no review row yet → no projection (nothing to project) ----
  @Test
  void recomputeForPlan_noReviewRow_isNoOp() {
    when(relationships.findByDirectReportEmployeeIdAndActiveTrue(IC))
        .thenReturn(Optional.of(relationship()));
    when(reviews.findByWeeklyPlanId(PLAN)).thenReturn(Optional.empty());

    refresher.recomputeForPlan(plan());

    verify(commitments, never()).findByWeeklyPlanIdOrderByIdAsc(any());
    verify(projectionService, never()).recompute(any(), any(), any(), any());
  }
}
