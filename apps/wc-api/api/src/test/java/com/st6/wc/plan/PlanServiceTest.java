package com.st6.wc.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.st6.wc.auth.DomainAuthorizationService;
import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.common.OrgTimeConfig;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.RoleType;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.dto.WeeklyPlanDto;
import com.st6.wc.plan.mapper.PlanMapper;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit proof of {@link PlanService#getCurrentPlan} (task 3.3a, §5 E3 / §6 self-scope). Resolves the
 * caller's <strong>own</strong> current-week plan by {@code (employeeId=caller, weekStartDate=now)}
 * — no {@code DomainAuthorizationService} call (self-scoped, like {@code /me}); an absent shell →
 * {@link PlanNotFoundException} (404 PLAN_NOT_FOUND, no create-on-GET — generation owns shell
 * creation). Repos + mapper mocked; the week is pinned via a fixed {@link Clock} + real {@link
 * OrgTimeConfig}.
 */
class PlanServiceTest {

  private static final Instant ANCHOR = Instant.parse("2026-06-02T17:00:00Z"); // Chicago Tue
  private static final LocalDate WEEK_MONDAY = LocalDate.of(2026, 6, 1);
  private static final UUID ACTOR = UUID.randomUUID();

  private final WeeklyPlanRepository plans = mock(WeeklyPlanRepository.class);
  private final WeeklyCommitmentRepository commitments = mock(WeeklyCommitmentRepository.class);
  private final EmployeeRepository employees = mock(EmployeeRepository.class);
  private final PlanMapper planMapper = mock(PlanMapper.class);
  private final DomainAuthorizationService authz = mock(DomainAuthorizationService.class);
  private final PlanService service =
      new PlanService(
          plans,
          commitments,
          employees,
          new OrgTimeConfig(ZoneId.of("America/Chicago")),
          Clock.fixed(ANCHOR, ZoneOffset.UTC),
          planMapper,
          authz);

  private UserPrincipal actor() {
    return new UserPrincipal(ACTOR, RoleType.IC, false);
  }

  private static Employee employee() {
    Employee e = new Employee();
    e.setId(ACTOR);
    e.setEmail("ada@x.test");
    e.setDisplayName("Ada");
    e.setRole(RoleType.IC);
    e.setActive(true);
    return e;
  }

  private static WeeklyPlan plan() {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(ACTOR);
    p.setWeekStartDate(WEEK_MONDAY);
    p.setWeekEndDate(WEEK_MONDAY.plusDays(6));
    p.setState(PlanState.DRAFT);
    p.setVersion(0L);
    return p;
  }

  // --- returns the caller's OWN current-week plan (resolved by employeeId=caller + week-of-now)
  // ----
  @Test
  void getCurrentPlan_returnsOwnCurrentWeekPlan() {
    WeeklyPlan plan = plan();
    WeeklyPlanDto expected =
        new WeeklyPlanDto(
            plan.getId(),
            ACTOR,
            "Ada",
            WEEK_MONDAY,
            WEEK_MONDAY.plusDays(6),
            PlanState.DRAFT,
            null,
            null,
            null,
            null,
            0,
            0,
            List.of(),
            null,
            List.of(),
            0L);
    when(plans.findByEmployeeIdAndWeekStartDate(ACTOR, WEEK_MONDAY)).thenReturn(Optional.of(plan));
    when(commitments.findByWeeklyPlanIdOrderByIdAsc(plan.getId())).thenReturn(List.of());
    when(employees.findById(ACTOR)).thenReturn(Optional.of(employee()));
    when(planMapper.toWeeklyPlanDto(eq(plan), eq("Ada"), eq(List.of()), eq(ACTOR)))
        .thenReturn(expected);

    WeeklyPlanDto result = service.getCurrentPlan(actor());

    assertThat(result).isSameAs(expected); // self-scoped resolution by (caller, week-of-now)
  }

  // --- RED #8 (service half): absent current-week shell → PlanNotFoundException (404, no create)
  // ----
  @Test
  void getCurrentPlan_absentShell_throwsPlanNotFound() {
    when(plans.findByEmployeeIdAndWeekStartDate(ACTOR, WEEK_MONDAY)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getCurrentPlan(actor()))
        .isInstanceOf(PlanNotFoundException.class);
  }

  // --- E4: getPlanById authorizes (chokepoint) then maps the plan for an authorized actor ----
  @Test
  void getPlanById_authorizedActor_mapsPlan() {
    WeeklyPlan plan = plan();
    WeeklyPlanDto expected =
        new WeeklyPlanDto(
            plan.getId(),
            ACTOR,
            "Ada",
            WEEK_MONDAY,
            WEEK_MONDAY.plusDays(6),
            PlanState.DRAFT,
            null,
            null,
            null,
            null,
            0,
            0,
            List.of(),
            null,
            List.of(),
            0L);
    // authz passes (void, no throw) → then load + map
    when(plans.findById(plan.getId())).thenReturn(Optional.of(plan));
    when(commitments.findByWeeklyPlanIdOrderByIdAsc(plan.getId())).thenReturn(List.of());
    when(employees.findById(ACTOR)).thenReturn(Optional.of(employee()));
    when(planMapper.toWeeklyPlanDto(eq(plan), eq("Ada"), eq(List.of()), eq(ACTOR)))
        .thenReturn(expected);

    WeeklyPlanDto result = service.getPlanById(actor(), plan.getId());

    assertThat(result).isSameAs(expected);
    verify(authz).authorizePlanAccess(actor(), plan.getId()); // the per-resource chokepoint ran
  }

  // --- E4 rule #3: a denied authorize is the CHOKEPOINT — no plan is loaded into a response path
  // ----
  @Test
  void getPlanById_deniedByAuthorizer_neverLoadsPlan() {
    UUID planId = UUID.randomUUID();
    doThrow(new ResourceNotFoundOrUnauthorizedException())
        .when(authz)
        .authorizePlanAccess(any(), eq(planId));

    assertThatThrownBy(() -> service.getPlanById(actor(), planId))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    // authorize threw FIRST → the plan was never read into a response path (rule #3 chokepoint)
    verify(plans, never()).findById(planId);
  }
}
