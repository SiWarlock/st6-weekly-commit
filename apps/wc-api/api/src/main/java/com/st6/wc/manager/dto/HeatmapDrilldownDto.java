package com.st6.wc.manager.dto;

import java.util.List;
import java.util.UUID;

/**
 * B.12 — the E15 cell drill-down response: the Supporting-Outcome breakdown for ONE report ×
 * Defining-Objective cell (REQ-F-022). A {@code record}; only groups with ≥1 commitment for the
 * report's that-week plan are included (the report's actual SO-grouped work).
 */
public record HeatmapDrilldownDto(
    UUID cellId,
    UUID employeeId,
    UUID definingObjectiveId,
    List<DrilldownOutcomeGroup> supportingOutcomes) {

  public HeatmapDrilldownDto {
    supportingOutcomes = List.copyOf(supportingOutcomes); // defensive copy (§22)
  }
}
