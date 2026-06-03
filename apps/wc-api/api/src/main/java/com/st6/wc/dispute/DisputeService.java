package com.st6.wc.dispute;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.st6.wc.audit.AuditService;
import com.st6.wc.auth.DomainAuthorizationService;
import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.dispute.dto.AlignmentDisputeDto;
import com.st6.wc.dispute.dto.OpenDisputeRequest;
import com.st6.wc.dispute.mapper.DisputeMapper;
import com.st6.wc.dispute.repo.AlignmentDisputeRepository;
import com.st6.wc.enums.DisputeStatus;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.review.ReviewStatusDeriver;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.web.IllegalStateTransitionException;
import com.st6.wc.web.SecondOpenDisputeException;
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

  public DisputeService(
      DomainAuthorizationService authz,
      AlignmentDisputeRepository disputes,
      WeeklyCommitmentRepository commitments,
      WeeklyPlanRepository plans,
      ManagerReviewRepository reviews,
      ReviewStatusDeriver deriver,
      DisputeMapper disputeMapper,
      AuditService auditService) {
    this.authz = authz;
    this.disputes = disputes;
    this.commitments = commitments;
    this.plans = plans;
    this.reviews = reviews;
    this.deriver = deriver;
    this.disputeMapper = disputeMapper;
    this.auditService = auditService;
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
   * Re-derive an <strong>already-reviewed</strong> review's status on dispute-open ({@code REVIEWED
   * → REVIEWED_WITH_DISPUTES}). A {@code NOT_REVIEWED} review is left untouched — opening a dispute
   * must never mark a review reviewed (the deriver returns {@code REVIEWED}/{@code
   * REVIEWED_WITH_DISPUTES} only).
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
}
