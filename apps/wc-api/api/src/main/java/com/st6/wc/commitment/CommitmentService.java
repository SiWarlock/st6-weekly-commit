package com.st6.wc.commitment;

import com.st6.wc.auth.DomainAuthorizationService;
import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.commitment.dto.CreateCommitmentRequest;
import com.st6.wc.commitment.dto.WeeklyCommitmentDto;
import com.st6.wc.commitment.mapper.CommitmentMapper;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.WorkType;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.rcdo.RcdoReadService;
import com.st6.wc.web.IllegalStateTransitionException;
import com.st6.wc.web.ValidationException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Draft-commitment writes (task 3.4a, §5 E5 / §6 rule #3 / §3 chess layer). {@link #create}
 * authorizes the <strong>parent plan</strong> FIRST (the chokepoint — no write before
 * authorization), requires the plan be {@code DRAFT} (else 409 {@code ILLEGAL_STATE_TRANSITION}),
 * rejects {@code workType=UNPLANNED} (planned-only endpoint → 400), validates an optional
 * Supporting-Outcome link (unknown → 400), forces {@code commitmentKind=PLANNED}, persists, and
 * maps to {@link WeeklyCommitmentDto}. The request's text fields are already normalized +
 * length/control-validated at the DTO boundary (Appendix E Part 1); this service applies the
 * business + state rules. One transaction; no projection upsert (DRAFT commitments aren't projected
 * — §9, 3.5 owns projections).
 */
@Service
public class CommitmentService {

  private final DomainAuthorizationService authz;
  private final WeeklyPlanRepository plans;
  private final WeeklyCommitmentRepository commitments;
  private final RcdoReadService rcdoReadService;
  private final CommitmentMapper commitmentMapper;

  public CommitmentService(
      DomainAuthorizationService authz,
      WeeklyPlanRepository plans,
      WeeklyCommitmentRepository commitments,
      RcdoReadService rcdoReadService,
      CommitmentMapper commitmentMapper) {
    this.authz = authz;
    this.plans = plans;
    this.commitments = commitments;
    this.rcdoReadService = rcdoReadService;
    this.commitmentMapper = commitmentMapper;
  }

  @Transactional
  public WeeklyCommitmentDto create(UserPrincipal actor, UUID planId, CreateCommitmentRequest req) {
    authz.authorizePlanAccess(
        actor, planId); // chokepoint: codeless 404 (+ audit) on denial/missing
    WeeklyPlan plan =
        plans.findById(planId).orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
    if (plan.getState() != PlanState.DRAFT) {
      throw new IllegalStateTransitionException(); // 409 — create requires DRAFT
    }
    if (req.workType() == WorkType.UNPLANNED) {
      throw ValidationException.field("workType", "UNPLANNED is not allowed on this endpoint");
    }
    UUID supportingOutcomeId = req.supportingOutcomeId();
    if (supportingOutcomeId != null) {
      try {
        rcdoReadService.findSupportingOutcome(supportingOutcomeId);
      } catch (ResourceNotFoundOrUnauthorizedException e) {
        throw ValidationException.field("supportingOutcomeId", "unknown Supporting Outcome");
      }
    }

    WeeklyCommitment commitment = new WeeklyCommitment();
    commitment.setId(UUID.randomUUID());
    commitment.setWeeklyPlanId(planId);
    commitment.setCommitmentKind(CommitmentKind.PLANNED); // forced — E5 is planned-only (B.6)
    commitment.setTitle(req.title());
    commitment.setDescription(req.description());
    commitment.setSupportingOutcomeId(supportingOutcomeId);
    commitment.setPriority(req.priority());
    commitment.setWorkType(req.workType());
    commitment.setConfidence(req.confidence());
    commitment.setAlignmentStatus(req.alignmentStatus());
    commitments.save(commitment);
    return commitmentMapper.toDto(commitment);
  }
}
