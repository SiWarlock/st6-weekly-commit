package com.st6.wc.manager;

import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.WorkType;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.manager.dto.HeatmapDrilldownDto;
import com.st6.wc.manager.dto.HeatmapResponseDto;
import com.st6.wc.manager.dto.ManagerCommandCenterRowDto;
import com.st6.wc.manager.dto.PageEnvelope;
import com.st6.wc.manager.dto.ReviewStateFilter;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manager read surface (task 6.5a, §5 E13). Thin controller — the coarse authn/role gate + the
 * IDOR-safe scoped query live in {@link ManagerQueryService} (§8, controllers never authorize).
 * {@code weekStart} is required (absent → {@code 400}); {@code Pageable} resolves via Spring Data
 * Web (size default 25 / max 100, {@code spring.data.web.pageable}). All seven REQ-F-023 filters
 * are declared (task 6.5a-2): the three summary-level ({@code employeeId} / {@code planState} /
 * {@code reviewState}) plus the four cross-table ({@code definingObjectiveId} / {@code priority} /
 * {@code workType} / {@code alignmentStatus}); a mistyped enum/UUID value fails binding → {@code
 * 400 VALIDATION_ERROR}.
 */
@RestController
public class ManagerController {

  private final ManagerQueryService queryService;
  private final ManagerHeatmapService heatmapService;
  private final ManagerDrilldownService drilldownService;

  public ManagerController(
      ManagerQueryService queryService,
      ManagerHeatmapService heatmapService,
      ManagerDrilldownService drilldownService) {
    this.queryService = queryService;
    this.heatmapService = heatmapService;
    this.drilldownService = drilldownService;
  }

  @GetMapping("/api/manager/command-center")
  public PageEnvelope<ManagerCommandCenterRowDto> commandCenter(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam("weekStart") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
      @RequestParam(value = "employeeId", required = false) UUID employeeId,
      @RequestParam(value = "planState", required = false) PlanState planState,
      @RequestParam(value = "reviewState", required = false) ReviewStateFilter reviewState,
      @RequestParam(value = "definingObjectiveId", required = false) UUID definingObjectiveId,
      @RequestParam(value = "priority", required = false) Priority priority,
      @RequestParam(value = "workType", required = false) WorkType workType,
      @RequestParam(value = "alignmentStatus", required = false) AlignmentStatus alignmentStatus,
      Pageable pageable) {
    return queryService.commandCenter(
        principal,
        weekStart,
        employeeId,
        planState,
        reviewState,
        definingObjectiveId,
        priority,
        workType,
        alignmentStatus,
        pageable);
  }

  /** E14 — the manager × direct-report × week × Defining-Objective heatmap grid (NOT paginated). */
  @GetMapping("/api/manager/heatmap")
  public HeatmapResponseDto heatmap(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam("weekStart") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
      @RequestParam(value = "definingObjectiveId", required = false) UUID definingObjectiveId,
      @RequestParam(value = "supportingOutcomeId", required = false) UUID supportingOutcomeId) {
    return heatmapService.heatmap(principal, weekStart, definingObjectiveId, supportingOutcomeId);
  }

  /**
   * E15 — the Supporting-Outcome breakdown for ONE report × Defining-Objective cell (own-cell
   * only).
   */
  @GetMapping("/api/manager/heatmap/{cellId}/drilldown")
  public HeatmapDrilldownDto drilldown(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable("cellId") UUID cellId,
      Pageable pageable) {
    return drilldownService.drilldown(principal, cellId, pageable);
  }
}
