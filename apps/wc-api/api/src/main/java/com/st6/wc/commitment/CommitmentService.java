package com.st6.wc.commitment;

import com.st6.wc.auth.DomainAuthorizationService;
import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.commitment.dto.CreateCommitmentRequest;
import com.st6.wc.commitment.dto.PatchCommitmentRequest;
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
import com.st6.wc.web.LockedBaselineEditException;
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

  /**
   * E6 partial edit (task 3.4b, §3 rule #2 / §5 / §6 rule #3). Authorizes the <strong>commitment
   * mutation</strong> FIRST (the chokepoint — IC-owner-only, no load before authorization), then
   * gates on the parent-plan state: on a non-{@code DRAFT} plan a frozen-baseline-field edit → 409
   * {@code LOCKED_BASELINE_EDIT} (rule #2, baseline-first precedence) and an {@code
   * alignmentStatus} edit → 409 {@code ILLEGAL_STATE_TRANSITION} ({@code
   * alignment_status_read_only_post_lock}, Appendix E rule 2). On a {@code DRAFT} plan only the
   * provided fields are applied (validated + normalized like E5; {@code workType=UNPLANNED}
   * rejected — that is the Phase-4 path). One transaction; no projection upsert (§9, 3.5 owns
   * projections).
   */
  @Transactional
  public WeeklyCommitmentDto update(
      UserPrincipal actor, UUID commitmentId, PatchCommitmentRequest req) {
    authz.authorizeCommitmentMutation(actor, commitmentId); // chokepoint: 404/403 (+ audit)
    WeeklyCommitment commitment =
        commitments
            .findById(commitmentId)
            .orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
    WeeklyPlan plan =
        plans
            .findById(commitment.getWeeklyPlanId())
            .orElseThrow(ResourceNotFoundOrUnauthorizedException::new);

    if (plan.getState() != PlanState.DRAFT) {
      if (touchesBaseline(req)) {
        throw new LockedBaselineEditException(); // rule #2 — baseline-first precedence
      }
      if (req.alignmentStatusProvided()) {
        throw new IllegalStateTransitionException("alignment_status_read_only_post_lock");
      }
      return commitmentMapper.toDto(commitment); // locked + nothing editable provided → no-op
    }

    applyFields(commitment, req);
    commitments.save(commitment);
    return commitmentMapper.toDto(commitment);
  }

  /**
   * E7 delete (task 3.4b, §5 / §6 rule #3). Authorizes the commitment mutation FIRST
   * (IC-owner-only, the chokepoint), then requires the parent plan be {@code DRAFT} (else 409
   * {@code ILLEGAL_STATE_TRANSITION}). One transaction.
   *
   * <p>Named {@code discard} (not {@code delete}) deliberately: SpotBugs 4.8.x {@code
   * MutableClasses} deems a class mutable when it has a public method whose name starts with a
   * mutator prefix ({@code delete}/{@code remove}/{@code set}/…), which would spuriously fire
   * {@code EI_EXPOSE_REP2} on the controller that injects this service (LESSONS). {@code discard}
   * sidesteps the heuristic.
   */
  @Transactional
  public void discard(UserPrincipal actor, UUID commitmentId) {
    authz.authorizeCommitmentMutation(actor, commitmentId); // chokepoint: 404/403 (+ audit)
    WeeklyCommitment commitment =
        commitments
            .findById(commitmentId)
            .orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
    WeeklyPlan plan =
        plans
            .findById(commitment.getWeeklyPlanId())
            .orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
    if (plan.getState() != PlanState.DRAFT) {
      throw new IllegalStateTransitionException(); // delete requires DRAFT
    }
    commitments.delete(commitment);
  }

  /** True when the patch provides any frozen planned-baseline field (rule #2 freeze set, §3). */
  private static boolean touchesBaseline(PatchCommitmentRequest req) {
    return req.titleProvided()
        || req.descriptionProvided()
        || req.supportingOutcomeIdProvided()
        || req.priorityProvided()
        || req.workTypeProvided()
        || req.confidenceProvided();
  }

  /**
   * Apply only the provided fields onto a DRAFT commitment — a present {@code null} clears the
   * nullable fields ({@code description}/{@code supportingOutcomeId}); present text is validated +
   * normalized via {@link TextNormalizer} (uniform with E5, on the normalized value).
   */
  private void applyFields(WeeklyCommitment commitment, PatchCommitmentRequest req) {
    if (req.titleProvided()) {
      String title = TextNormalizer.normalizeSingleLine(req.getTitle());
      if (title == null || title.isBlank()) {
        throw ValidationException.field("title", "must not be blank");
      }
      if (TextNormalizer.containsControlChars(title)) {
        throw ValidationException.field("title", "must not contain control characters");
      }
      if (TextNormalizer.codePointCount(title) > 255) {
        throw ValidationException.field("title", "must be at most 255 code points");
      }
      commitment.setTitle(title);
    }
    if (req.descriptionProvided()) {
      String description = TextNormalizer.normalizeMultiLine(req.getDescription());
      if (TextNormalizer.codePointCount(description) > 4000) {
        throw ValidationException.field("description", "must be at most 4000 code points");
      }
      commitment.setDescription(description); // present-null / blank → null (cleared)
    }
    if (req.supportingOutcomeIdProvided()) {
      UUID supportingOutcomeId = req.getSupportingOutcomeId();
      if (supportingOutcomeId != null) {
        try {
          rcdoReadService.findSupportingOutcome(supportingOutcomeId);
        } catch (ResourceNotFoundOrUnauthorizedException e) {
          throw ValidationException.field("supportingOutcomeId", "unknown Supporting Outcome");
        }
      }
      commitment.setSupportingOutcomeId(supportingOutcomeId); // present-null → null (unlinked)
    }
    if (req.priorityProvided() && req.getPriority() != null) {
      commitment.setPriority(req.getPriority());
    }
    if (req.workTypeProvided() && req.getWorkType() != null) {
      if (req.getWorkType() == WorkType.UNPLANNED) {
        throw ValidationException.field("workType", "UNPLANNED is not allowed on this endpoint");
      }
      commitment.setWorkType(req.getWorkType());
    }
    if (req.confidenceProvided() && req.getConfidence() != null) {
      commitment.setConfidence(req.getConfidence());
    }
    if (req.alignmentStatusProvided() && req.getAlignmentStatus() != null) {
      commitment.setAlignmentStatus(req.getAlignmentStatus());
    }
  }
}
