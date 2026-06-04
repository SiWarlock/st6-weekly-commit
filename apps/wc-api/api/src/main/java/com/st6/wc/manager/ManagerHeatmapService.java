package com.st6.wc.manager;

import com.st6.wc.auth.DomainAuthorizationService;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.manager.dto.HeatmapResponseDto;
import com.st6.wc.manager.query.ManagerHeatmapQuery;
import com.st6.wc.rcdo.SupportingOutcome;
import com.st6.wc.rcdo.repo.SupportingOutcomeRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * E14 manager-heatmap read (task 6.5b, §6/§9/§14). Authorizes the coarse manager-only gate ({@code
 * authorizeTeamHeatmapAccess} → {@code 403 MANAGER_ROLE_REQUIRED} for an IC), then the
 * <strong>IDOR-safe direct-report scoping</strong>: the query is scoped to {@code
 * manager_employee_id = the authenticated manager} (NEVER a request value). The {@code
 * supportingOutcomeId} filter resolves to its parent Defining Objective (the cell grain is DO, not
 * SO) — a disagreeing {@code definingObjectiveId} or an unknown SO yields no cells (the counts stay
 * DO-honest). NOT paginated.
 */
@Service
public class ManagerHeatmapService {

  private final DomainAuthorizationService authz;
  private final ManagerHeatmapQuery heatmapQuery;
  private final SupportingOutcomeRepository supportingOutcomes;

  public ManagerHeatmapService(
      DomainAuthorizationService authz,
      ManagerHeatmapQuery heatmapQuery,
      SupportingOutcomeRepository supportingOutcomes) {
    this.authz = authz;
    this.heatmapQuery = heatmapQuery;
    this.supportingOutcomes = supportingOutcomes;
  }

  public HeatmapResponseDto heatmap(
      UserPrincipal principal,
      LocalDate weekStart,
      UUID definingObjectiveId,
      UUID supportingOutcomeId) {
    authz.authorizeTeamHeatmapAccess(principal); // coarse: non-manager → 403 MANAGER_ROLE_REQUIRED

    UUID effectiveDo = definingObjectiveId;
    if (supportingOutcomeId != null) {
      // the cell grain is DO — resolve the SO to its parent DO (Option A); disagree/unknown → none
      Optional<UUID> soDo =
          supportingOutcomes
              .findById(supportingOutcomeId)
              .map(SupportingOutcome::getDefiningObjectiveId);
      if (soDo.isEmpty() || (effectiveDo != null && !effectiveDo.equals(soDo.get()))) {
        return new HeatmapResponseDto(weekStart, List.of());
      }
      effectiveDo = soDo.get();
    }

    // IDOR scope: principal.employeeId() — never a request value
    return new HeatmapResponseDto(
        weekStart, heatmapQuery.findCells(principal.employeeId(), weekStart, effectiveDo));
  }
}
