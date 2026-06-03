package com.st6.wc.plan.repo;

import com.st6.wc.plan.WeeklyPlan;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link WeeklyPlan}. {@link #findByWeekStartDate(LocalDate)} (task 3.2)
 * backs the generation job's idempotency pre-filter — the employee-ids that already have a shell
 * for the target week; the {@code unique(employee_id, week_start_date)} (V1) is the DB backstop.
 */
public interface WeeklyPlanRepository extends JpaRepository<WeeklyPlan, UUID> {

  List<WeeklyPlan> findByWeekStartDate(LocalDate weekStartDate);
}
