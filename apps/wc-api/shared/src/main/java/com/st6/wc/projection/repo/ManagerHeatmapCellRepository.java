package com.st6.wc.projection.repo;

import com.st6.wc.projection.ManagerHeatmapCell;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link ManagerHeatmapCell}. The {@code (manager, employee, week)}
 * finder (task 3.5) returns a report's per-Defining-Objective cells for the §9 synchronous upsert
 * (each cell unique per manager/report/week/DO).
 */
public interface ManagerHeatmapCellRepository extends JpaRepository<ManagerHeatmapCell, UUID> {

  List<ManagerHeatmapCell> findByManagerEmployeeIdAndEmployeeIdAndWeekStartDate(
      UUID managerEmployeeId, UUID employeeId, LocalDate weekStartDate);
}
