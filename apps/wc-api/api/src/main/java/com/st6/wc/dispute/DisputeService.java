package com.st6.wc.dispute;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.st6.wc.audit.AuditService;
import com.st6.wc.auth.DomainAuthorizationService;
import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.dispute.dto.AlignmentDisputeDto;
import com.st6.wc.dispute.dto.OpenDisputeRequest;
import com.st6.wc.dispute.dto.RespondDisputeRequest;
import com.st6.wc.dispute.mapper.DisputeMapper;
import com.st6.wc.dispute.repo.AlignmentDisputeRepository;
import com.st6.wc.enums.DisputeStatus;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.rcdo.RcdoReadService;
import com.st6.wc.review.ReviewStatusDeriver;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.web.IllegalStateTransitionException;
import com.st6.wc.web.SecondOpenDisputeException;
import com.st6.wc.web.ValidationException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alignment-dispute writes (task 5.3, §5 E17 / §6 / §3 / rule #6). {@link #open} authorizes the
 * dispute <strong>manager-of-owner-only</strong> FIRST (the chokepoint — {@link
 * DomainAuthorizationService#authorizeDisputeCreation}; the IC owner cannot dispute their own
 * commitment), guards the plan state, enforces the single-unresolved invariant (service pre-check +
 * the partial-unique DB backstop → {@code 409 SECOND_OPEN_DISPUTE}), persists an {@code OPEN}
 * dispute, re-derives the parent review status (an already-reviewed review only), and emits a
 * note-body-free {@code DISPUTE_OPENED} audit (§15) — all in one {@code @Version}-guarded txn.
 */
@Service
public class DisputeService {

  private final DomainAuthorizationService authz;
  private final AlignmentDisputeRepository disputes;
  private final WeeklyCommitmentRepository commitments;
  private final WeeklyPlanRepository plans;
  private final ManagerReviewRepository reviews;
  private final ReviewStatusDeriver deriver;
  private final DisputeMapper disputeMapper;
  private final AuditService auditService;
  private final RcdoReadService rcdoReadService;
  private final Clock clock;

  public DisputeService(
      DomainAuthorizationService authz,
      AlignmentDisputeRepository disputes,
      WeeklyCommitmentRepository commitments,
      WeeklyPlanRepository plans,
      ManagerReviewRepository reviews,
      ReviewStatusDeriver deriver,
      DisputeMapper disputeMapper,
      AuditService auditService,
      RcdoReadService rcdoReadService,
      Clock clock) {
    this.authz = authz;
    this.disputes = disputes;
    this.commitments = commitments;
    this.plans = plans;
    this.reviews = reviews;
    this.deriver = deriver;
    this.disputeMapper = disputeMapper;
    this.auditService = auditService;
    this.rcdoReadService = rcdoReadService;
    this.clock = clock;
  }

  private static final List<DisputeStatus> UNRESOLVED =
      List.of(DisputeStatus.OPEN, DisputeStatus.IC_RESPONDED);
  private static final String UNRESOLVED_CONSTRAINT = "uq_one_unresolved_dispute_per_commitment";

  @Transactional
  public AlignmentDisputeDto open(UserPrincipal actor, UUID commitmentId, OpenDisputeRequest req) {
    authz.authorizeDisputeCreation(actor, commitmentId); // chokepoint: manager-of-owner only
    WeeklyCommitment commitment =
        commitments
            .findById(commitmentId)
            .orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
    WeeklyPlan plan =
        plans
            .findById(commitment.getWeeklyPlanId())
            .orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
    if (plan.getState() == PlanState.DRAFT) {
      throw new IllegalStateTransitionException(); // disputes are a post-lock manager action
    }
    // rule #6 single-unresolved — service pre-check (the partial-unique is the race backstop below)
    if (disputes.findByCommitmentIdAndStatusIn(commitmentId, UNRESOLVED).isPresent()) {
      throw new SecondOpenDisputeException();
    }

    AlignmentDispute dispute = new AlignmentDispute();
    dispute.setId(UUID.randomUUID());
    dispute.setCommitmentId(commitmentId);
    dispute.setManagerEmployeeId(actor.employeeId()); // opener = the direct manager
    dispute.setStatus(DisputeStatus.OPEN);
    dispute.setFlagType(req.flagType());
    dispute.setManagerNote(req.managerNote());
    try {
      disputes.saveAndFlush(dispute);
    } catch (DataIntegrityViolationException ex) {
      if (isUnresolvedDisputeConstraint(ex)) {
        throw new SecondOpenDisputeException(); // rule #6 DB backstop — a concurrent race lost
      }
      throw ex; // a different constraint — never mask it as SECOND_OPEN_DISPUTE
    }

    reDeriveReviewStatus(commitment.getWeeklyPlanId());
    auditService.record(
        "DISPUTE_OPENED",
        "AlignmentDispute",
        dispute.getId(),
        actor.employeeId(),
        "Dispute opened",
        safeMetadata(dispute)); // ids/flagType/status only — no managerNote body (§15/REQ-S-006)
    return disputeMapper.toDto(dispute);
  }

  /**
   * IC responds to an {@code OPEN} dispute (task 5.4, E18): authorize the owning IC FIRST (the
   * chokepoint — {@link DomainAuthorizationService#authorizeDisputeResponse}; the manager has no
   * respond capability), guard the {@code OPEN} state, require at-least-one-of {@code {icResponse,
   * supportingOutcomeId}}, optionally revise the disputed commitment's {@code supportingOutcomeId}
   * (the <strong>rule-#2 dispute-gated exception</strong> — that field ONLY, reachable only here),
   * set {@code icResponse}, transition {@code OPEN→IC_RESPONDED}, and emit a note-body-free {@code
   * DISPUTE_RESPONDED} audit (status + the safe SO-revision ids, §15). The dispute stays unresolved
   * ({@code IC_RESPONDED} ∈ the unresolved bucket) — NO review re-derivation, NO projection change.
   * One {@code @Version}-guarded txn (dispute + commitment).
   */
  @Transactional
  public AlignmentDisputeDto respond(
      UserPrincipal actor, UUID disputeId, RespondDisputeRequest req) {
    authz.authorizeDisputeResponse(actor, disputeId); // chokepoint: owning-IC only (manager→403)
    AlignmentDispute dispute =
        disputes.findById(disputeId).orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
    if (dispute.getStatus() != DisputeStatus.OPEN) {
      throw new IllegalStateTransitionException(); // respond is a one-shot OPEN→IC_RESPONDED
    }
    if (req.icResponse() == null && req.supportingOutcomeId() == null) {
      throw ValidationException.field(
          "icResponse", "provide a response or a revised Supporting Outcome");
    }

    UUID revisedSupportingOutcomeId = null;
    if (req.supportingOutcomeId() != null) {
      try {
        rcdoReadService.findSupportingOutcome(req.supportingOutcomeId());
      } catch (ResourceNotFoundOrUnauthorizedException e) {
        throw ValidationException.field("supportingOutcomeId", "unknown Supporting Outcome");
      }
      WeeklyCommitment commitment =
          commitments
              .findById(dispute.getCommitmentId())
              .orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
      commitment.setSupportingOutcomeId(req.supportingOutcomeId()); // rule-#2 exception — SO ONLY
      commitments.save(commitment); // @Version-guarded (concurrent edit → 409)
      revisedSupportingOutcomeId = req.supportingOutcomeId();
    }
    if (req.icResponse() != null) {
      dispute.setIcResponse(req.icResponse());
    }
    dispute.setStatus(DisputeStatus.IC_RESPONDED);
    disputes.save(dispute);

    auditService.record(
        "DISPUTE_RESPONDED",
        "AlignmentDispute",
        dispute.getId(),
        actor.employeeId(),
        "Dispute responded",
        respondMetadata(dispute, revisedSupportingOutcomeId)); // no icResponse body (§15)
    return disputeMapper.toDto(dispute);
  }

  /**
   * The active direct manager resolves an alignment dispute (task 5.5, E19): authorize
   * manager-of-owner FIRST (the chokepoint — {@link
   * DomainAuthorizationService#authorizeDisputeResolution}; the IC owner SEES the dispute via the
   * plan read but cannot resolve it → {@code 403 IC_CANNOT_RESOLVE_DISPUTE}), guard the
   * already-{@code RESOLVED} state ({@code 409 ILLEGAL_STATE_TRANSITION} — no re-resolve),
   * transition {@code OPEN|IC_RESPONDED→RESOLVED} and stamp {@code resolvedAt} from the injected
   * {@code Clock}, re-derive the parent review (an <strong>already-reviewed</strong> one only —
   * resolving the LAST unresolved dispute flips {@code REVIEWED_WITH_DISPUTES→REVIEWED}; a {@code
   * NOT_REVIEWED} review is left untouched, the shared {@link #reDeriveReviewStatus} guard), and
   * emit a note-body-free {@code DISPUTE_RESOLVED} audit (§15). One {@code @Version}-guarded txn
   * (dispute + the re-derived review; a concurrent double-resolve → {@code OptimisticLockingFailure
   * → 409}). Body-less (E19 carries no {@code resolutionNote} in MVP); no projection change (the §9
   * read-model is Phase-6 work).
   */
  @Transactional
  public AlignmentDisputeDto resolve(UserPrincipal actor, UUID disputeId) {
    authz.authorizeDisputeResolution(actor, disputeId); // chokepoint: manager-of-owner (IC→403)
    AlignmentDispute dispute =
        disputes.findById(disputeId).orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
    if (dispute.getStatus() == DisputeStatus.RESOLVED) {
      throw new IllegalStateTransitionException(); // one-shot OPEN|IC_RESPONDED→RESOLVED
    }
    dispute.setStatus(DisputeStatus.RESOLVED);
    dispute.setResolvedAt(clock.instant());
    disputes.saveAndFlush(dispute); // flush BEFORE the re-derive so its count query sees RESOLVED

    WeeklyCommitment commitment =
        commitments
            .findById(dispute.getCommitmentId())
            .orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
    reDeriveReviewStatus(
        commitment.getWeeklyPlanId()); // last unresolved cleared → WITH_DISPUTES→REVIEWED

    auditService.record(
        "DISPUTE_RESOLVED",
        "AlignmentDispute",
        dispute.getId(),
        actor.employeeId(),
        "Dispute resolved",
        resolveMetadata(dispute)); // resolved status only — no managerNote/icResponse body (§15)
    return disputeMapper.toDto(dispute);
  }

  /**
   * Re-derive an <strong>already-reviewed</strong> review's status (dispute open {@code REVIEWED →
   * REVIEWED_WITH_DISPUTES}, resolve {@code REVIEWED_WITH_DISPUTES → REVIEWED}). A {@code
   * NOT_REVIEWED} review is left untouched — a dispute open/resolve must never mark a review
   * reviewed (the deriver returns {@code REVIEWED}/{@code REVIEWED_WITH_DISPUTES} only).
   */
  private void reDeriveReviewStatus(UUID planId) {
    reviews
        .findByWeeklyPlanId(planId)
        .filter(review -> review.getStatus() != ReviewStatus.NOT_REVIEWED)
        .ifPresent(
            review -> {
              review.setStatus(deriver.derive(planId));
              reviews.save(review);
            });
  }

  private boolean isUnresolvedDisputeConstraint(DataIntegrityViolationException ex) {
    String message = ex.getMostSpecificCause().getMessage();
    return message != null && message.contains(UNRESOLVED_CONSTRAINT);
  }

  private static String safeMetadata(AlignmentDispute dispute) {
    // §18 — build via an escaped JSON node, never string-concat; carries only safe enum constants.
    return JsonNodeFactory.instance
        .objectNode()
        .put("flagType", dispute.getFlagType().name())
        .put("status", dispute.getStatus().name())
        .toString();
  }

  /**
   * The {@code DISPUTE_RESPONDED} audit metadata (§18 escaped node): the resulting status + — when
   * the rule-#2 exception fired — a safe trail of the SO revision (RCDO ids only, §15-safe; the
   * locked baseline field changed, so the audit records what + that it was via respond). The {@code
   * icResponse} body is NEVER included.
   */
  private static String respondMetadata(AlignmentDispute dispute, UUID revisedSupportingOutcomeId) {
    var node = JsonNodeFactory.instance.objectNode().put("status", dispute.getStatus().name());
    if (revisedSupportingOutcomeId != null) {
      node.put("supportingOutcomeRevised", true)
          .put("supportingOutcomeId", revisedSupportingOutcomeId.toString());
    }
    return node.toString();
  }

  /**
   * The {@code DISPUTE_RESOLVED} audit metadata (§18 escaped node): the resolved status only —
   * never the {@code managerNote}/{@code icResponse} bodies (§15/REQ-S-006).
   */
  private static String resolveMetadata(AlignmentDispute dispute) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("status", dispute.getStatus().name())
        .toString();
  }
}
