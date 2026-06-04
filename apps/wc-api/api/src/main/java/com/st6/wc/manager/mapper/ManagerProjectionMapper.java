package com.st6.wc.manager.mapper;

import com.st6.wc.manager.dto.ManagerCommandCenterRowDto;
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
}
