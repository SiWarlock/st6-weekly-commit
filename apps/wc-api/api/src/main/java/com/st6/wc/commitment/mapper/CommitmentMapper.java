package com.st6.wc.commitment.mapper;

import com.st6.wc.action.AllowedAction;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.dto.RcdoBreadcrumbDto;
import com.st6.wc.commitment.dto.WeeklyCommitmentDto;
import com.st6.wc.plan.AllowedActionResolver;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.rcdo.RcdoReadService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The single commitment→DTO mapper (task 3.4a, Appendix B.6) — extracted so both {@code PlanMapper}
 * (commitments nested in a plan) and {@code CommitmentController} (a single created/edited
 * commitment) share one place for the B.6 shape + the RC→DO→SO breadcrumb (resolved via {@link
 * RcdoReadService} when linked, null when unlinked). The DTO intentionally omits the dispute field
 * (B.6-minus, Option-A at the disputes slice). Records, never entities.
 *
 * <p>{@code allowedActions} (§15/§24): the context-free {@link #toDto(WeeklyCommitment)} emits an
 * empty list (single-commitment write responses — the UI re-reads the plan), while the
 * context-aware {@link #toDto(WeeklyCommitment, WeeklyPlan, UUID)} (task 4.4b) computes the
 * per-commitment affordances via {@link AllowedActionResolver#commitmentActions} — used by {@code
 * PlanMapper} on the plan read so the IC's reconciliation view surfaces {@code CARRY_FORWARD}.
 */
@Component
public class CommitmentMapper {

  private final RcdoReadService rcdoReadService;
  private final AllowedActionResolver allowedActionResolver;

  public CommitmentMapper(
      RcdoReadService rcdoReadService, AllowedActionResolver allowedActionResolver) {
    this.rcdoReadService = rcdoReadService;
    this.allowedActionResolver = allowedActionResolver;
  }

  /** Context-free map — empty {@code allowedActions} (single-commitment responses; UI re-reads). */
  public WeeklyCommitmentDto toDto(WeeklyCommitment c) {
    return toDto(c, List.of());
  }

  /**
   * Context-aware map (task 4.4b) — fills {@code allowedActions} via the resolver for the viewing
   * actor (the plan read path; {@code PlanMapper} passes the parent plan + actor).
   */
  public WeeklyCommitmentDto toDto(WeeklyCommitment c, WeeklyPlan plan, UUID actorEmployeeId) {
    return toDto(c, allowedActionResolver.commitmentActions(actorEmployeeId, plan, c));
  }

  private WeeklyCommitmentDto toDto(WeeklyCommitment c, List<AllowedAction> allowedActions) {
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
        allowedActions,
        c.getVersion());
  }
}
