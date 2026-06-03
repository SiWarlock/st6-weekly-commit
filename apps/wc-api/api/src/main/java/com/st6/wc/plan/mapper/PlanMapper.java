package com.st6.wc.plan.mapper;

import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.dto.RcdoBreadcrumbDto;
import com.st6.wc.commitment.dto.WeeklyCommitmentDto;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.plan.AllowedActionResolver;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.dto.WeeklyPlanDto;
import com.st6.wc.rcdo.RcdoReadService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Assembles a {@link WeeklyPlanDto} (Appendix B.5) from the plan + its commitments (task 3.3a):
 * nests each {@link WeeklyCommitmentDto} (B.6), resolves the per-commitment RC→DO→SO breadcrumb via
 * {@link RcdoReadService} (RCDO knowledge stays there), and stamps the server-authoritative {@code
 * allowedActions[]} via {@link AllowedActionResolver}. DTOs never expose the entity
 * (forbidden-pattern #3). {@code managerReview} is null while {@code DRAFT} (3.5 wires the real
 * review mapping); commitment-level {@code allowedActions} are empty in 3.3a (their actions land
 * with their enforcing slices — §15).
 */
@Component
public class PlanMapper {

  private final RcdoReadService rcdoReadService;
  private final AllowedActionResolver allowedActionResolver;

  public PlanMapper(RcdoReadService rcdoReadService, AllowedActionResolver allowedActionResolver) {
    this.rcdoReadService = rcdoReadService;
    this.allowedActionResolver = allowedActionResolver;
  }

  public WeeklyPlanDto toWeeklyPlanDto(
      WeeklyPlan plan,
      String employeeDisplayName,
      List<WeeklyCommitment> commitments,
      UUID actorEmployeeId) {

    List<WeeklyCommitmentDto> commitmentDtos =
        commitments.stream().map(c -> toWeeklyCommitmentDto(c, actorEmployeeId)).toList();
    int plannedCount =
        (int)
            commitments.stream()
                .filter(c -> c.getCommitmentKind() == CommitmentKind.PLANNED)
                .count();
    int unplannedCount = commitments.size() - plannedCount;

    return new WeeklyPlanDto(
        plan.getId(),
        plan.getEmployeeId(),
        employeeDisplayName,
        plan.getWeekStartDate(),
        plan.getWeekEndDate(),
        plan.getState(),
        plan.getGeneratedAt(),
        plan.getLockedAt(),
        plan.getReconciliationStartedAt(),
        plan.getReconciledAt(),
        plannedCount,
        unplannedCount,
        commitmentDtos,
        null, // managerReview — null while DRAFT (B.5); 3.5 wires the entity→DTO mapping
        allowedActionResolver.planActions(actorEmployeeId, plan, commitments),
        plan.getVersion());
  }

  public WeeklyCommitmentDto toWeeklyCommitmentDto(WeeklyCommitment c, UUID actorEmployeeId) {
    RcdoBreadcrumbDto breadcrumb =
        c.getSupportingOutcomeId() == null
            ? null
            : rcdoReadService.resolveBreadcrumb(c.getSupportingOutcomeId());
    return new WeeklyCommitmentDto(
        c.getId(),
        c.getWeeklyPlanId(),
        c.getCommitmentKind(),
        c.getTitle(),
        c.getDescription(),
        c.getSupportingOutcomeId(),
        breadcrumb,
        c.getPriority(),
        c.getWorkType(),
        c.getConfidence(),
        c.getAlignmentStatus(),
        c.getManagerAlignmentNote(),
        c.getReconciliationOutcome(),
        c.getOutcomeNote(),
        c.getCarryForwardSourceCommitmentId(),
        List.of(), // commitment-level affordances land with their enforcing slices (§15)
        c.getVersion());
  }
}
