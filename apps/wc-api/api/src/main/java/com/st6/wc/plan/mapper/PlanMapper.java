package com.st6.wc.plan.mapper;

import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.dto.WeeklyCommitmentDto;
import com.st6.wc.commitment.mapper.CommitmentMapper;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.plan.AllowedActionResolver;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.dto.WeeklyPlanDto;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Assembles a {@link WeeklyPlanDto} (Appendix B.5) from the plan + its commitments (task 3.3a):
 * delegates each {@link WeeklyCommitmentDto} to {@link CommitmentMapper} (the single B.6 shape +
 * RC→DO→SO breadcrumb source — extracted at 3.4a, reused by the commitment endpoints too) and
 * stamps the server-authoritative {@code allowedActions[]} via {@link AllowedActionResolver}. DTOs
 * never expose the entity (forbidden-pattern #3). {@code managerReview} is null while {@code DRAFT}
 * (3.5 wires the real review mapping).
 */
@Component
public class PlanMapper {

  private final CommitmentMapper commitmentMapper;
  private final AllowedActionResolver allowedActionResolver;

  public PlanMapper(
      CommitmentMapper commitmentMapper, AllowedActionResolver allowedActionResolver) {
    this.commitmentMapper = commitmentMapper;
    this.allowedActionResolver = allowedActionResolver;
  }

  public WeeklyPlanDto toWeeklyPlanDto(
      WeeklyPlan plan,
      String employeeDisplayName,
      List<WeeklyCommitment> commitments,
      UUID actorEmployeeId) {

    List<WeeklyCommitmentDto> commitmentDtos =
        commitments.stream().map(commitmentMapper::toDto).toList();
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
}
