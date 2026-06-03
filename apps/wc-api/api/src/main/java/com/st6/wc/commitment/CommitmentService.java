package com.st6.wc.commitment;

import com.st6.wc.audit.AuditService;
import com.st6.wc.auth.DomainAuthorizationService;
import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.commitment.dto.CreateCommitmentRequest;
import com.st6.wc.commitment.dto.CreateUnplannedCommitmentRequest;
import com.st6.wc.commitment.dto.PatchCommitmentRequest;
import com.st6.wc.commitment.dto.WeeklyCommitmentDto;
import com.st6.wc.commitment.mapper.CommitmentMapper;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.ReconciliationOutcome;
import com.st6.wc.enums.WorkType;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.ProjectionService;
import com.st6.wc.rcdo.RcdoReadService;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.web.IllegalStateTransitionException;
import com.st6.wc.web.LockedBaselineEditException;
import com.st6.wc.web.ValidationException;
import java.util.List;
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
  private final ManagerRelationshipRepository relationships;
  private final ManagerReviewRepository reviews;
  private final ProjectionService projectionService;
  private final AuditService auditService;

  public CommitmentService(
      DomainAuthorizationService authz,
      WeeklyPlanRepository plans,
      WeeklyCommitmentRepository commitments,
      RcdoReadService rcdoReadService,
      CommitmentMapper commitmentMapper,
      ManagerRelationshipRepository relationships,
      ManagerReviewRepository reviews,
      ProjectionService projectionService,
      AuditService auditService) {
    this.authz = authz;
    this.plans = plans;
    this.commitments = commitments;
    this.rcdoReadService = rcdoReadService;
    this.commitmentMapper = commitmentMapper;
    this.relationships = relationships;
    this.reviews = reviews;
    this.projectionService = projectionService;
    this.auditService = auditService;
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
   * E11 unplanned-commitment create (task 4.3, §3 / §5 / §6 rule #3 / §9 — REQ-F-025/026).
   * Authorizes the parent plan <strong>owner-only</strong> via {@link
   * DomainAuthorizationService#authorizePlanMutation} (the chokepoint — a manager-direct-report can
   * READ a locked report's plan but must NOT author on it, §6) — deliberately NOT {@code
   * authorizePlanAccess} (which also admits managers). Requires the plan be {@code LOCKED} or
   * {@code RECONCILING} (else 409 — unplanned work surfaces post-lock, never on a {@code
   * DRAFT}/{@code RECONCILED} plan), validates an optional Supporting-Outcome link (the link is
   * enforced only at close, §4.5; unknown → 400), and <strong>server-forces</strong> {@code
   * commitment_kind=UNPLANNED} + {@code work_type=UNPLANNED} (the inverse of the E5 planned-only
   * create — {@code workType} is absent from the request, so a client value is ignored). An
   * insert-only create: it never touches a planned-baseline row (REQ-F-025). One
   * {@code @Transactional} unit — persist, recompute the manager projection (§9 {@code
   * unplanned_count}, skipped if no manager), emit an IC audit.
   */
  @Transactional
  public WeeklyCommitmentDto createUnplanned(
      UserPrincipal actor, UUID planId, CreateUnplannedCommitmentRequest req) {
    authz.authorizePlanMutation(
        actor, planId); // chokepoint: owner-only (manager→403, cross/miss→404)
    WeeklyPlan plan =
        plans.findById(planId).orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
    PlanState state = plan.getState();
    if (state != PlanState.LOCKED && state != PlanState.RECONCILING) {
      throw new IllegalStateTransitionException(); // unplanned create only post-lock,
      // pre-reconciled
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
    commitment.setCommitmentKind(CommitmentKind.UNPLANNED); // forced — E11 is unplanned-only (B.6)
    commitment.setWorkType(WorkType.UNPLANNED); // forced — server-owned (the inverse of E5)
    commitment.setTitle(req.title());
    commitment.setDescription(req.description());
    commitment.setSupportingOutcomeId(supportingOutcomeId);
    commitment.setPriority(req.priority());
    commitment.setConfidence(req.confidence());
    commitment.setAlignmentStatus(req.alignmentStatus());
    commitments.save(commitment);

    recomputeProjection(plan); // §9 synchronous unplanned_count upsert (skipped if no manager)
    auditService.record(
        "UNPLANNED_COMMITMENT_CREATED",
        "WeeklyCommitment",
        commitment.getId(),
        actor.employeeId(),
        "Unplanned commitment created",
        "{}");
    return commitmentMapper.toDto(commitment);
  }

  /**
   * E6 partial edit (task 3.4b draft edit + 4.1 reconciliation outcome, §3 rule #2 + single-outcome
   * rule / §5 / §6 rule #3 / §9). Authorizes the <strong>commitment mutation</strong> FIRST (the
   * chokepoint — IC-owner-only, no load before authorization), then applies a
   * <strong>per-plan-state editable-field allow-list</strong> (the rule-#2-adjacent forward-guard —
   * each state names its editable set; never a widened {@code != DRAFT} condition):
   *
   * <ul>
   *   <li><b>baseline-first precedence (every non-DRAFT state):</b> a frozen planned-baseline field
   *       edit → 409 {@code LOCKED_BASELINE_EDIT} (rule #2) — checked before any other field so a
   *       patch mixing a baseline field with an outcome can never apply the outcome.
   *   <li><b>{@code alignmentStatus} (every non-DRAFT state):</b> read-only post-lock → 409 {@code
   *       ILLEGAL_STATE_TRANSITION} ({@code alignment_status_read_only_post_lock}).
   *   <li><b>outcome fields ({@code reconciliationOutcome}/{@code outcomeNote}):</b> editable ONLY
   *       in {@code RECONCILING}; provided in any other state → 409 {@code
   *       ILLEGAL_STATE_TRANSITION} (not-yet / no-longer recordable).
   *   <li><b>DRAFT:</b> only the provided baseline/alignment fields are applied (validated +
   *       normalized like E5; {@code workType=UNPLANNED} rejected — that is the E11 path).
   *   <li><b>RECONCILING + an outcome:</b> records the outcome under the single-outcome rule (a
   *       direct {@code CARRIED_FORWARD} is rejected — set only via E12/carry-forward), recomputes
   *       the manager projection synchronously (§9), and writes an IC audit — all in this one
   *       {@code @Version}-guarded transaction.
   * </ul>
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
    PlanState state = plan.getState();
    // (state, kind) allow-list: an UNPLANNED commitment's Supporting-Outcome link is editable in
    // RECONCILING (the pre-close link, 4.5) — PLANNED stays frozen; never widened (LESSONS
    // §27-ext).
    boolean unplannedSoLinkAllowed =
        state == PlanState.RECONCILING
            && commitment.getCommitmentKind() == CommitmentKind.UNPLANNED;

    if (state != PlanState.DRAFT && touchesBaseline(req)) {
      throw new LockedBaselineEditException(); // rule #2 — baseline-first precedence (every
      // non-DRAFT)
    }
    if (state != PlanState.DRAFT && req.supportingOutcomeIdProvided() && !unplannedSoLinkAllowed) {
      throw new LockedBaselineEditException(); // SO frozen post-lock EXCEPT the unplanned pre-close
      // link
    }
    if (state != PlanState.DRAFT && req.alignmentStatusProvided()) {
      throw new IllegalStateTransitionException("alignment_status_read_only_post_lock");
    }
    if ((req.reconciliationOutcomeProvided() || req.outcomeNoteProvided())
        && state != PlanState.RECONCILING) {
      throw new IllegalStateTransitionException(); // outcomes recordable only in RECONCILING
    }

    if (state == PlanState.DRAFT) {
      applyFields(commitment, req);
      commitments.save(commitment);
      return commitmentMapper.toDto(commitment);
    }

    boolean recordsOutcome = req.reconciliationOutcomeProvided() || req.outcomeNoteProvided();
    boolean linksUnplannedSo = unplannedSoLinkAllowed && req.supportingOutcomeIdProvided();
    if (state == PlanState.RECONCILING && (recordsOutcome || linksUnplannedSo)) {
      if (recordsOutcome) {
        applyOutcome(commitment, req);
      }
      if (linksUnplannedSo) {
        applyUnplannedSoLink(commitment, req);
      }
      commitments.save(commitment); // @Version-guarded: stale conflict → 409
      recomputeProjection(
          plan); // §9 synchronous manager-projection refresh (skipped if no manager)
      auditService.record(
          "OUTCOME_RECORDED",
          "WeeklyCommitment",
          commitmentId,
          actor.employeeId(),
          "Reconciliation outcome recorded",
          reconciliationAuditMetadata(
              req)); // safe field NAMES only (§15) — distinguishes link-only
      return commitmentMapper.toDto(commitment);
    }

    return commitmentMapper.toDto(commitment); // non-DRAFT, nothing editable provided → no-op
  }

  /**
   * Link an UNPLANNED commitment's Supporting Outcome during {@code RECONCILING} (the pre-close
   * link, 4.5) — validated via {@link RcdoReadService} (unknown → 400); a present {@code null}
   * unlinks.
   */
  private void applyUnplannedSoLink(WeeklyCommitment commitment, PatchCommitmentRequest req) {
    UUID supportingOutcomeId = req.getSupportingOutcomeId();
    if (supportingOutcomeId != null) {
      try {
        rcdoReadService.findSupportingOutcome(supportingOutcomeId);
      } catch (ResourceNotFoundOrUnauthorizedException e) {
        throw ValidationException.field("supportingOutcomeId", "unknown Supporting Outcome");
      }
    }
    commitment.setSupportingOutcomeId(supportingOutcomeId);
  }

  /**
   * Safe audit metadata for a RECONCILING E6 mutation — the touched field NAMES only (never values,
   * §15), built via an escaped JSON node (LESSONS §18) so a link-only edit is distinguishable in
   * the log from an outcome write under the shared {@code OUTCOME_RECORDED} umbrella.
   */
  private static String reconciliationAuditMetadata(PatchCommitmentRequest req) {
    com.fasterxml.jackson.databind.node.ObjectNode node =
        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    com.fasterxml.jackson.databind.node.ArrayNode fields = node.putArray("fields");
    if (req.reconciliationOutcomeProvided()) {
      fields.add("reconciliationOutcome");
    }
    if (req.outcomeNoteProvided()) {
      fields.add("outcomeNote");
    }
    if (req.supportingOutcomeIdProvided()) {
      fields.add("supportingOutcomeId");
    }
    return node.toString();
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

  /**
   * True when the patch provides an <strong>always-frozen</strong> planned-baseline field (rule #2,
   * §3). {@code supportingOutcomeId} is NOT here — it is gated separately because it is frozen for
   * PLANNED but linkable for an UNPLANNED commitment in {@code RECONCILING} (the pre-close link,
   * 4.5); the per-(state, kind) allow-list, never a widened condition (LESSONS §27-ext).
   */
  private static boolean touchesBaseline(PatchCommitmentRequest req) {
    return req.titleProvided()
        || req.descriptionProvided()
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

  /**
   * Apply the reconciliation-outcome fields on a {@code RECONCILING} commitment (E6 outcome
   * contract, §3 single-outcome rule). A direct {@code reconciliationOutcome=CARRIED_FORWARD} is
   * rejected — it is mutually exclusive with completion outcomes and reached only via E12
   * (carry-forward), so the valid direct values are {@code COMPLETED|PARTIALLY_COMPLETED|BLOCKED|
   * CANCELED}. {@code outcomeNote} is normalized + length-capped like a description (Appendix E
   * Part 1, reusing {@link TextNormalizer}); blank/present-null → {@code null}.
   */
  private void applyOutcome(WeeklyCommitment commitment, PatchCommitmentRequest req) {
    if (req.reconciliationOutcomeProvided()) {
      ReconciliationOutcome outcome = req.getReconciliationOutcome();
      if (outcome == ReconciliationOutcome.CARRIED_FORWARD) {
        throw ValidationException.field(
            "reconciliationOutcome", "CARRIED_FORWARD is set only via carry-forward");
      }
      commitment.setReconciliationOutcome(outcome);
    }
    if (req.outcomeNoteProvided()) {
      String note = TextNormalizer.normalizeMultiLine(req.getOutcomeNote());
      if (TextNormalizer.codePointCount(note) > 4000) {
        throw ValidationException.field("outcomeNote", "must be at most 4000 code points");
      }
      commitment.setOutcomeNote(note); // present-null / blank → null (cleared)
    }
  }

  /**
   * Synchronously recompute the manager projection for the plan's owner after an outcome write (§9
   * — reconciliation mutations keep the read models in lockstep in the same transaction). Skipped
   * when the IC has no active manager or no review row yet (nothing to project). Reuses the 3.5
   * {@link ProjectionService#recompute} from-source path unchanged — recording an outcome refreshes
   * {@code plan_state}/{@code updated_at} without changing the 3.5 count derivations (the §9
   * outcome-count source is an open doc-clarification, not introduced here).
   */
  private void recomputeProjection(WeeklyPlan plan) {
    UUID managerId =
        relationships
            .findByDirectReportEmployeeIdAndActiveTrue(plan.getEmployeeId())
            .map(ManagerRelationship::getManagerEmployeeId)
            .orElse(null);
    if (managerId == null) {
      return;
    }
    final UUID resolvedManagerId = managerId;
    reviews
        .findByWeeklyPlanId(plan.getId())
        .ifPresent(
            review -> {
              List<WeeklyCommitment> all = commitments.findByWeeklyPlanIdOrderByIdAsc(plan.getId());
              projectionService.recompute(plan, resolvedManagerId, all, review);
            });
  }
}
