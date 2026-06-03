package com.st6.wc.plan.repo;

import com.st6.wc.plan.WeeklyPlan;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Bare Spring Data repository for {@link WeeklyPlan} (task 1.5). Finder queries land in 1.6. */
public interface WeeklyPlanRepository extends JpaRepository<WeeklyPlan, UUID> {}
