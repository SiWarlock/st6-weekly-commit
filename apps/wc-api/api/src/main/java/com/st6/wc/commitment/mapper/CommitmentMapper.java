package com.st6.wc.commitment.mapper;

import com.st6.wc.action.AllowedAction;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.dto.RcdoBreadcrumbDto;
import com.st6.wc.commitment.dto.WeeklyCommitmentDto;
import com.st6.wc.dispute.dto.AlignmentDisputeDto;
import com.st6.wc.dispute.mapper.DisputeMapper;
import com.st6.wc.dispute.repo.AlignmentDisputeRepository;
import com.st6.wc.enums.DisputeStatus;
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
 * RcdoReadService} when linked, null when unlinked) + the nested unresolved {@code dispute} (task
 * 5.3b, B.6 Option-A): the commitment's {@code OPEN}/{@code IC_RESPONDED} dispute mapped via {@link
 * DisputeMapper} (≤1 by rule #6), else null — resolved once here so every path (write responses +
 * the plan read) carries it consistently. Records, never entities.
 *
 * <p>{@code allowedActions} (§15/§24): the context-free {@link #toDto(WeeklyCommitment)} emits an
 * empty list (single-commitment write responses — the UI re-reads the plan), while the
 * context-aware {@link #toDto(WeeklyCommitment, WeeklyPlan, UUID)} (task 4.4b) computes the
 * per-commitment affordances via {@link AllowedActionResolver#commitmentActions} — used by {@code
 * PlanMapper} on the plan read so the IC's reconciliation view surfaces {@code CARRY_FORWARD}.
 */
@Component
public class CommitmentMapper {

  // The unresolved-dispute bucket (rule #6) — consistent with ReviewStatusDeriver + DisputeService;
  // a shared constant is deferred (Carry-forward, 3-site DRY across the dispute/review domains).
  private static final List<DisputeStatus> UNRESOLVED =
      List.of(DisputeStatus.OPEN, DisputeStatus.IC_RESPONDED);

  private final RcdoReadService rcdoReadService;
  private final AllowedActionResolver allowedActionResolver;
  private final AlignmentDisputeRepository disputes;
  private final DisputeMapper disputeMapper;

  public CommitmentMapper(
      RcdoReadService rcdoReadService,
      AllowedActionResolver allowedActionResolver,
      AlignmentDisputeRepository disputes,
      DisputeMapper disputeMapper) {
    this.rcdoReadService = rcdoReadService;
    this.allowedActionResolver = allowedActionResolver;
    this.disputes = disputes;
    this.disputeMapper = disputeMapper;
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
    // The commitment's current unresolved dispute (B.6 Option-A, 5.3b), ≤1 by rule #6, else null.
    AlignmentDisputeDto dispute =
        disputes
            .findByCommitmentIdAndStatusIn(c.getId(), UNRESOLVED)
            .map(disputeMapper::toDto)
            .orElse(null);
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
        dispute,
        allowedActions,
        c.getVersion());
  }
}
