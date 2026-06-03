package com.st6.wc.plan;

import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.dto.WeeklyPlanDto;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/plans/current} (task 3.3a, §5 E3) — returns the authenticated IC's own
 * current-week {@link WeeklyPlanDto}. <strong>Self-scoped</strong>: the plan is resolved by {@code
 * employeeId=caller}, so this carries only the coarse authn gate (the 2.6 chain stops
 * unauthenticated requests with a 401 before they reach here) and makes <strong>no {@code
 * DomainAuthorizationService} call</strong> — same posture as {@code GET /api/me}. The by-id E4
 * read ({@code /api/plans/{id}}) with its per-resource IDOR authorizer is task 3.3b. Returns a DTO,
 * never an entity.
 */
@RestController
public class PlanController {

  private final PlanService planService;

  public PlanController(PlanService planService) {
    this.planService = planService;
  }

  @GetMapping("/api/plans/current")
  public WeeklyPlanDto current(@AuthenticationPrincipal UserPrincipal principal) {
    return planService.getCurrentPlan(principal);
  }

  /**
   * {@code GET /api/plans/{id}} (E4, §6 rule #3) — a per-resource read. Thin: the service calls the
   * central authorizer first (the chokepoint), so any unauthorized/missing id → codeless 404. The
   * {@code @AuthenticationPrincipal} is the 2.6-resolved caller.
   */
  @GetMapping("/api/plans/{id}")
  public WeeklyPlanDto byId(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable("id") UUID id) {
    return planService.getPlanById(principal, id);
  }
}
