package com.st6.wc.review.repo;

import com.st6.wc.review.ManagerReview;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link ManagerReview}. {@link #findByWeeklyPlanId(UUID)} (task 3.5)
 * resolves a locked plan's review for the {@code WeeklyPlanDto} mapping (≤1 row — a plan has at
 * most one review).
 */
public interface ManagerReviewRepository extends JpaRepository<ManagerReview, UUID> {

  Optional<ManagerReview> findByWeeklyPlanId(UUID weeklyPlanId);
}
