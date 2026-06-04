package com.st6.wc.manager.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * B.12 — the E14 heatmap response: the manager's active-direct-report cells for {@code weekStart}.
 * <strong>Not paginated</strong> (bounded by reports × Defining Objectives). Carries {@code
 * weekStart} (the per-cell field is {@code weekStartDate} — the deliberate B.12 asymmetry).
 */
public record HeatmapResponseDto(LocalDate weekStart, List<HeatmapCellDto> cells) {

  public HeatmapResponseDto {
    cells = List.copyOf(cells); // defensive copy (§22)
  }
}
