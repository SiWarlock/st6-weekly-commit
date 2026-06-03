package com.st6.wc.plan;

import com.st6.wc.auth.DomainAuthorizationService;
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
import java.util.UUID;
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
  private final DomainAuthorizationService authz;

  public PlanService(
      WeeklyPlanRepository plans,
      WeeklyCommitmentRepository commitments,
      EmployeeRepository employees,
      OrgTimeConfig orgTimeConfig,
      Clock clock,
      PlanMapper planMapper,
      DomainAuthorizationService authz) {
    this.plans = plans;
    this.commitments = commitments;
    this.employees = employees;
    this.orgTimeConfig = orgTimeConfig;
    this.clock = clock;
    this.planMapper = planMapper;
    this.authz = authz;
  }

  public WeeklyPlanDto getCurrentPlan(UserPrincipal actor) {
    LocalDate weekStart = orgTimeConfig.weekStartDate(clock.instant());
    WeeklyPlan plan =
        plans
            .findByEmployeeIdAndWeekStartDate(actor.employeeId(), weekStart)
            .orElseThrow(PlanNotFoundException::new);
    return toDto(plan, actor.employeeId());
  }

  /**
   * E4 by-id read (§5 / §6 rule #3): authorize <strong>first</strong> — the chokepoint, so no plan
   * data reaches a response path before authorization — then load + map (reusing {@link
   * PlanMapper}). A cross-owner/cross-team denial OR a genuinely-missing id both surface as the
   * codeless IDOR {@link ResourceNotFoundOrUnauthorizedException} 404 (NOT the named {@code
   * PLAN_NOT_FOUND}, which would leak existence — that is E3 only); the authorizer writes the
   * {@code REQUIRES_NEW} denial audit on a genuine denial only (genuinely-missing is not audited).
   */
  public WeeklyPlanDto getPlanById(UserPrincipal actor, UUID planId) {
    authz.authorizePlanAccess(actor, planId);
    WeeklyPlan plan =
        plans.findById(planId).orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
    return toDto(plan, actor.employeeId());
  }

  /** Shared load-commitments + owner-display-name + map step for the E3/E4 plan reads. */
  private WeeklyPlanDto toDto(WeeklyPlan plan, UUID actorEmployeeId) {
    List<WeeklyCommitment> planCommitments =
        commitments.findByWeeklyPlanIdOrderByIdAsc(plan.getId());
    // the owner's display name (B.5 denormalizes it for the manager view too)
    String displayName =
        employees
            .findById(plan.getEmployeeId())
            .orElseThrow(ResourceNotFoundOrUnauthorizedException::new)
            .getDisplayName();
    return planMapper.toWeeklyPlanDto(plan, displayName, planCommitments, actorEmployeeId);
  }
}
