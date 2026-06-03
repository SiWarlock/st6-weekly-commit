package com.st6.wc.commitment.mapper;

import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.dto.RcdoBreadcrumbDto;
import com.st6.wc.commitment.dto.WeeklyCommitmentDto;
import com.st6.wc.rcdo.RcdoReadService;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The single commitment→DTO mapper (task 3.4a, Appendix B.6) — extracted so both {@code PlanMapper}
 * (commitments nested in a plan) and {@code CommitmentController} (a single created/edited
 * commitment) share one place for the B.6 shape + the RC→DO→SO breadcrumb (resolved via {@link
 * RcdoReadService} when linked, null when unlinked). The DTO intentionally omits the dispute field
 * (B.6-minus, Option-A at the disputes slice); commitment-level {@code allowedActions} are empty in
 * this phase (their actions are emitted by their enforcing slices — §15). Records, never entities.
 */
@Component
public class CommitmentMapper {

  private final RcdoReadService rcdoReadService;

  public CommitmentMapper(RcdoReadService rcdoReadService) {
    this.rcdoReadService = rcdoReadService;
  }

  public WeeklyCommitmentDto toDto(WeeklyCommitment c) {
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
        List.of(),
        c.getVersion());
  }
}
