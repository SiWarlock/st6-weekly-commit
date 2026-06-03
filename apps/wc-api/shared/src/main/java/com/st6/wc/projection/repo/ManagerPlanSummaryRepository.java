package com.st6.wc.projection.repo;

import com.st6.wc.projection.ManagerPlanSummary;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Bare Spring Data repository for {@link ManagerPlanSummary} (task 1.5). Finders land in 1.6. */
public interface ManagerPlanSummaryRepository extends JpaRepository<ManagerPlanSummary, UUID> {}
