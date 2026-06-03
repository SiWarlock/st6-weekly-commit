package com.st6.wc.projection.repo;

import com.st6.wc.projection.ManagerPlanSummary;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link ManagerPlanSummary}. The {@code (manager, employee, week)}
 * finder (task 3.5) backs the §9 synchronous upsert grain (unique per manager/report/week).
 */
public interface ManagerPlanSummaryRepository extends JpaRepository<ManagerPlanSummary, UUID> {

  Optional<ManagerPlanSummary> findByManagerEmployeeIdAndEmployeeIdAndWeekStartDate(
      UUID managerEmployeeId, UUID employeeId, LocalDate weekStartDate);
}
