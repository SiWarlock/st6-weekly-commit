package com.st6.wc.plan;

import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.common.OrgTimeConfig;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.dto.WeeklyPlanDto;
import com.st6.wc.plan.mapper.PlanMapper;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read service for IC plan retrieval (task 3.3a, §5 E3 / §6). {@link #getCurrentPlan} returns the
 * caller's <strong>own</strong> current-week {@link WeeklyPlanDto}, resolved by {@code
 * (employeeId=caller, weekStartDate=week-of-now)} — <strong>self-scoped, no {@code
 * DomainAuthorizationService} call</strong> (there is no {@code {id}} IDOR surface here; the by-id
 * E4 read + its authorizer is 3.3b). An absent shell → {@link PlanNotFoundException} (404
 * PLAN_NOT_FOUND); a GET never creates a shell (generation, 3.2, owns that). The current week
 * reuses {@link OrgTimeConfig#weekStartDate} over the injectable {@link Clock}. Read-only
 * transaction.
 */
@Service
@Transactional(readOnly = true)
public class PlanService {

  private final WeeklyPlanRepository plans;
  private final WeeklyCommitmentRepository commitments;
  private final EmployeeRepository employees;
  private final OrgTimeConfig orgTimeConfig;
  private final Clock clock;
  private final PlanMapper planMapper;

  public PlanService(
      WeeklyPlanRepository plans,
      WeeklyCommitmentRepository commitments,
      EmployeeRepository employees,
      OrgTimeConfig orgTimeConfig,
      Clock clock,
      PlanMapper planMapper) {
    this.plans = plans;
    this.commitments = commitments;
    this.employees = employees;
    this.orgTimeConfig = orgTimeConfig;
    this.clock = clock;
    this.planMapper = planMapper;
  }

  public WeeklyPlanDto getCurrentPlan(UserPrincipal actor) {
    LocalDate weekStart = orgTimeConfig.weekStartDate(clock.instant());
    WeeklyPlan plan =
        plans
            .findByEmployeeIdAndWeekStartDate(actor.employeeId(), weekStart)
            .orElseThrow(PlanNotFoundException::new);
    List<WeeklyCommitment> planCommitments =
        commitments.findByWeeklyPlanIdOrderByIdAsc(plan.getId());
    // self-read: the owner's display name (B.5 denormalizes it for the manager view too)
    String displayName =
        employees
            .findById(plan.getEmployeeId())
            .orElseThrow(ResourceNotFoundOrUnauthorizedException::new)
            .getDisplayName();
    return planMapper.toWeeklyPlanDto(plan, displayName, planCommitments, actor.employeeId());
  }
}
