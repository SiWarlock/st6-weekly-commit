package com.st6.wc.review.repo;

import com.st6.wc.review.ManagerReview;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Bare Spring Data repository for {@link ManagerReview} (task 1.5). Finder queries land in 1.6. */
public interface ManagerReviewRepository extends JpaRepository<ManagerReview, UUID> {}
