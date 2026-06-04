package com.st6.wc.manager.dto;

import com.st6.wc.enums.RiskBadge;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * B.12 — one E14 heatmap cell (manager × direct-report × week × Defining Objective), mirroring
 * {@code manager_heatmap_cell} (§9) field-for-field. A {@code record} across the API boundary,
 * <strong>never</strong> the entity (no audit fields leak — forbidden-pattern #3, leak-tested).
 * {@code cellId} is the id for the E15 drill-down. The cell carries {@code weekStartDate} (the
 * enclosing {@link HeatmapResponseDto} carries {@code weekStart} — the deliberate B.12 asymmetry,
 * mirrored verbatim in the frontend {@code dtos.ts}; do not harmonize).
 */
public record HeatmapCellDto(
    UUID cellId,
    UUID managerEmployeeId,
    UUID employeeId,
    String employeeDisplayName,
    LocalDate weekStartDate,
    UUID definingObjectiveId,
    String definingObjectiveTitle,
    int commitmentCount,
    int plannedCount,
    int unplannedCount,
    int misalignedCount,
    int needsReviewCount,
    int blockedCount,
    int carryForwardCount,
    int unresolvedDisputeCount,
    List<RiskBadge> riskBadges) {

  public HeatmapCellDto {
    riskBadges = List.copyOf(riskBadges); // defensive copy (§22)
  }
}
