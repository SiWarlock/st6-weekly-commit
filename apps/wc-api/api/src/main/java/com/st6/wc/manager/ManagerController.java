package com.st6.wc.manager;

import com.st6.wc.enums.PlanState;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.manager.dto.ManagerCommandCenterRowDto;
import com.st6.wc.manager.dto.PageEnvelope;
import com.st6.wc.manager.dto.ReviewStateFilter;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manager read surface (task 6.5a, §5 E13). Thin controller — the coarse authn/role gate + the
 * IDOR-safe scoped query live in {@link ManagerQueryService} (§8, controllers never authorize).
 * {@code weekStart} is required (absent → {@code 400}); {@code Pageable} resolves via Spring Data
 * Web (size default 25 / max 100, {@code spring.data.web.pageable}). The four 6.5a-2 cross-table
 * filters ({@code definingObjectiveId} / {@code priority} / {@code workType} / {@code
 * alignmentStatus}) are <strong>NOT declared</strong> here — Spring silently ignores undeclared
 * query params, so the API honestly applies only the filters it serves until 6.5a-2 adds them.
 */
@RestController
public class ManagerController {

  private final ManagerQueryService queryService;

  public ManagerController(ManagerQueryService queryService) {
    this.queryService = queryService;
  }

  @GetMapping("/api/manager/command-center")
  public PageEnvelope<ManagerCommandCenterRowDto> commandCenter(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam("weekStart") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
      @RequestParam(value = "employeeId", required = false) UUID employeeId,
      @RequestParam(value = "planState", required = false) PlanState planState,
      @RequestParam(value = "reviewState", required = false) ReviewStateFilter reviewState,
      Pageable pageable) {
    return queryService.commandCenter(
        principal, weekStart, employeeId, planState, reviewState, pageable);
  }
}
