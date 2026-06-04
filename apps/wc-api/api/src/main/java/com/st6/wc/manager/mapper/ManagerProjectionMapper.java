package com.st6.wc.manager.mapper;

import com.st6.wc.manager.dto.HeatmapCellDto;
import com.st6.wc.manager.dto.ManagerCommandCenterRowDto;
import com.st6.wc.projection.ManagerHeatmapCell;
import com.st6.wc.projection.ManagerPlanSummary;
import org.springframework.stereotype.Component;

/**
 * Maps a command-center query row (the {@code manager_plan_summary} projection + the joined report
 * display name) to the B.11 {@link ManagerCommandCenterRowDto} — the single place the projection
 * entity becomes a boundary DTO (record, never the entity; the projection-row {@code id} is
 * dropped).
 */
@Component
public class ManagerProjectionMapper {

  public ManagerCommandCenterRowDto toRowDto(ManagerPlanSummary s, String employeeDisplayName) {
    return new ManagerCommandCenterRowDto(
        s.getManagerEmployeeId(),
        s.getEmployeeId(),
        employeeDisplayName,
        s.getWeeklyPlanId(),
        s.getWeekStartDate(),
        s.getPlanState(),
        s.getReviewStatus(),
        s.getReviewDueAt(),
        s.isReviewOverdue(),
        s.getPlannedCount(),
        s.getUnplannedCount(),
        s.getMisalignedCount(),
        s.getNeedsReviewCount(),
        s.getBlockedCount(),
        s.getCarryForwardCount(),
        s.getUnresolvedDisputeCount(),
        s.getUpdatedAt());
  }

  /**
   * Maps a heatmap cell (the {@code manager_heatmap_cell} projection + the joined report display
   * name + Defining-Objective title) to the B.12 {@link HeatmapCellDto} — a record, never the
   * entity (the projection-row audit fields are dropped). The joins are resolved query-side
   * (N+1-free); this mapper does no lookups.
   */
  public HeatmapCellDto toHeatmapCellDto(
      ManagerHeatmapCell c, String employeeDisplayName, String definingObjectiveTitle) {
    return new HeatmapCellDto(
        c.getId(),
        c.getManagerEmployeeId(),
        c.getEmployeeId(),
        employeeDisplayName,
        c.getWeekStartDate(),
        c.getDefiningObjectiveId(),
        definingObjectiveTitle,
        c.getCommitmentCount(),
        c.getPlannedCount(),
        c.getUnplannedCount(),
        c.getMisalignedCount(),
        c.getNeedsReviewCount(),
        c.getBlockedCount(),
        c.getCarryForwardCount(),
        c.getUnresolvedDisputeCount(),
        c.getRiskBadges());
  }
}
