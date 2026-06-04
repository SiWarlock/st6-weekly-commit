package com.st6.wc.manager;

import com.st6.wc.auth.DomainAuthorizationService;
import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.mapper.CommitmentMapper;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.manager.dto.DrilldownOutcomeGroup;
import com.st6.wc.manager.dto.HeatmapDrilldownDto;
import com.st6.wc.manager.dto.PageEnvelope;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.ManagerHeatmapCell;
import com.st6.wc.projection.repo.ManagerHeatmapCellRepository;
import com.st6.wc.rcdo.SupportingOutcome;
import com.st6.wc.rcdo.repo.SupportingOutcomeRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * E15 heatmap cell drill-down (task 6.5b, §6/§9/§14). <strong>Own-cell-only</strong>: {@code
 * authorizeHeatmapCellAccess} is the FIRST statement (the rule-#3 chokepoint) — a cell whose {@code
 * manager_employee_id} ≠ the principal renders an IDOR {@code 404} + denial audit, a genuinely
 * missing cell a {@code 404} with no audit (§25). Resolves the cell → {@code (employeeId,
 * definingObjectiveId, weekStartDate)}, the report's that-week plan (canonical {@code weekly_plan}
 * via the {@code unique(employee,week)} finder — NOT the summary projection), then groups the
 * plan's commitments by Supporting Outcome under that DO; each group is a B.20 envelope ({@code
 * priority ASC, createdAt ASC, id ASC}, F.5 + a stable tie-break). The commitments use the
 * <strong>context-free</strong> {@link CommitmentMapper#toDto(WeeklyCommitment)} → empty {@code
 * allowedActions} (dispute affordances are E3/E4-only, §35). Empty SO groups are omitted.
 */
@Service
@Transactional(readOnly = true)
public class ManagerDrilldownService {

  private static final int MAX_PAGE_SIZE = 100; // F.5 / B.20 (backstop to the resolver config)
  // F.5 sort + the id tie-break — deterministic while createdAt is unpopulated (auditing unwired).
  private static final Sort DRILLDOWN_SORT =
      Sort.by(Sort.Order.asc("priority"), Sort.Order.asc("createdAt"), Sort.Order.asc("id"));

  private final DomainAuthorizationService authz;
  private final ManagerHeatmapCellRepository heatmapCells;
  private final WeeklyPlanRepository plans;
  private final SupportingOutcomeRepository supportingOutcomes;
  private final WeeklyCommitmentRepository commitments;
  private final CommitmentMapper commitmentMapper;

  public ManagerDrilldownService(
      DomainAuthorizationService authz,
      ManagerHeatmapCellRepository heatmapCells,
      WeeklyPlanRepository plans,
      SupportingOutcomeRepository supportingOutcomes,
      WeeklyCommitmentRepository commitments,
      CommitmentMapper commitmentMapper) {
    this.authz = authz;
    this.heatmapCells = heatmapCells;
    this.plans = plans;
    this.supportingOutcomes = supportingOutcomes;
    this.commitments = commitments;
    this.commitmentMapper = commitmentMapper;
  }

  public HeatmapDrilldownDto drilldown(UserPrincipal principal, UUID cellId, Pageable pageable) {
    authz.authorizeHeatmapCellAccess(
        principal, cellId); // chokepoint FIRST — IDOR 404 + audit / missing 404
    ManagerHeatmapCell cell =
        heatmapCells.findById(cellId).orElseThrow(ResourceNotFoundOrUnauthorizedException::new);

    int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
    Pageable effective = PageRequest.of(pageable.getPageNumber(), size, DRILLDOWN_SORT);

    List<DrilldownOutcomeGroup> groups = new ArrayList<>();
    Optional<WeeklyPlan> plan =
        plans.findByEmployeeIdAndWeekStartDate(cell.getEmployeeId(), cell.getWeekStartDate());
    if (plan.isPresent()) {
      for (SupportingOutcome so :
          supportingOutcomes.findByDefiningObjectiveIdOrderByIdAsc(cell.getDefiningObjectiveId())) {
        Page<WeeklyCommitment> page =
            commitments.findByWeeklyPlanIdAndSupportingOutcomeId(
                plan.get().getId(), so.getId(), effective);
        if (page.getTotalElements()
            > 0) { // omit SOs with no commitments (the actual work breakdown)
          groups.add(
              new DrilldownOutcomeGroup(
                  so.getId(), so.getTitle(), PageEnvelope.of(page.map(commitmentMapper::toDto))));
        }
      }
    }
    return new HeatmapDrilldownDto(
        cell.getId(), cell.getEmployeeId(), cell.getDefiningObjectiveId(), groups);
  }
}
