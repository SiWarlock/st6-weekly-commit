package com.st6.wc.auth;

import com.st6.wc.comment.Comment;
import com.st6.wc.comment.repo.CommentRepository;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.dispute.AlignmentDispute;
import com.st6.wc.dispute.repo.AlignmentDisputeRepository;
import com.st6.wc.enums.CommentTargetType;
import com.st6.wc.identity.DomainPrincipal;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.ManagerHeatmapCell;
import com.st6.wc.projection.repo.ManagerHeatmapCellRepository;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.ManagerReview;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The single central authorizer (task 2.5, SAFETY-CRITICAL — rule #3 IDOR; §6/§5/§11/§15/§16).
 * Every later-phase resource check calls one of these methods <strong>before</strong> any
 * repository read/mutation; controller annotations stay coarse authn/role gates only. Resolves each
 * resource to its owning employee via flat-FK {@code findById} chains, then applies:
 *
 * <ul>
 *   <li><b>IC self-access</b> — owner == principal (a manager who owns a resource is authorized
 *       <em>as IC</em>, §4 relationship-driven);
 *   <li><b>manager scope</b> — the owner is an <em>active</em> direct report of the manager ({@code
 *       manager_relationship}, {@code active=true}); an inactive row removes scope;
 *   <li><b>SYSTEM exemption</b> — {@code SystemPrincipal} is exempt from self/direct-report checks.
 * </ul>
 *
 * <p>Denials are IDOR-safe: cross-owner / cross-team / missing all render {@code 404} ({@link
 * ResourceNotFoundOrUnauthorizedException}) — existence is never revealed; capability denials
 * render {@code 403} ({@link AuthorizationDeniedException}) with a named code. Each
 * <em>genuine</em> denial (resource exists but unauthorized, or a capability denial) writes exactly
 * one safe-metadata {@code audit_event} via {@link AuthorizationDeniedAuditer} (in a new
 * transaction); a genuinely missing resource renders {@code 404} with <em>no</em> audit (no
 * audit-spam on id-probing).
 */
@Service
public class DomainAuthorizationService {

  private static final String PLAN = "Plan";
  private static final String COMMITMENT = "Commitment";
  private static final String DISPUTE = "Dispute";
  private static final String COMMENT = "Comment";
  private static final String REVIEW = "Review";
  private static final String HEATMAP_CELL = "HeatmapCell";
  private static final String HEATMAP = "Heatmap";
  private static final String SYNC_RECORD = "SyncRecord";

  private static final String REASON_CROSS_OWNER = "cross_owner_or_unauthorized";
  private static final String REASON_IC_NO_RESOLVE = "ic_cannot_resolve_dispute";
  private static final String REASON_MANAGER_ROLE = "manager_role_required";

  private static final String CODE_IC_CANNOT_RESOLVE = "IC_CANNOT_RESOLVE_DISPUTE";
  private static final String CODE_MANAGER_ROLE_REQUIRED = "MANAGER_ROLE_REQUIRED";

  private final WeeklyPlanRepository plans;
  private final WeeklyCommitmentRepository commitments;
  private final AlignmentDisputeRepository disputes;
  private final CommentRepository comments;
  private final ManagerReviewRepository reviews;
  private final ManagerHeatmapCellRepository heatmapCells;
  private final OutlookCalendarSyncRecordRepository syncRecords;
  private final ManagerRelationshipRepository relationships;
  private final AuthorizationDeniedAuditer auditer;

  public DomainAuthorizationService(
      WeeklyPlanRepository plans,
      WeeklyCommitmentRepository commitments,
      AlignmentDisputeRepository disputes,
      CommentRepository comments,
      ManagerReviewRepository reviews,
      ManagerHeatmapCellRepository heatmapCells,
      OutlookCalendarSyncRecordRepository syncRecords,
      ManagerRelationshipRepository relationships,
      AuthorizationDeniedAuditer auditer) {
    this.plans = plans;
    this.commitments = commitments;
    this.disputes = disputes;
    this.comments = comments;
    this.reviews = reviews;
    this.heatmapCells = heatmapCells;
    this.syncRecords = syncRecords;
    this.relationships = relationships;
    this.auditer = auditer;
  }

  // ===== resource access (IDOR-safe 404 on denial) =====

  public void authorizePlanAccess(DomainPrincipal principal, UUID planId) {
    authorizeOwnership(principal, planOwner(planId), PLAN, planId);
  }

  public void authorizeCommitmentAccess(DomainPrincipal principal, UUID commitmentId) {
    authorizeOwnership(principal, commitmentOwner(commitmentId), COMMITMENT, commitmentId);
  }

  public void authorizeDisputeAccess(DomainPrincipal principal, UUID disputeId) {
    authorizeOwnership(principal, disputeOwner(disputeId), DISPUTE, disputeId);
  }

  public void authorizeReviewAccess(DomainPrincipal principal, UUID reviewId) {
    authorizeOwnership(principal, reviewOwner(reviewId), REVIEW, reviewId);
  }

  public void authorizeCommentAccess(DomainPrincipal principal, UUID commentId) {
    Comment comment = comments.findById(commentId).orElseThrow(this::notFound);
    authorizeOwnership(
        principal,
        commentTargetOwner(comment.getTargetType(), comment.getTargetId()),
        COMMENT,
        commentId);
  }

  /** Authorize the right to comment on a target (§11 target IDOR — create-comment path). */
  public void authorizeCommentTargetAccess(
      DomainPrincipal principal, CommentTargetType targetType, UUID targetId) {
    authorizeOwnership(principal, commentTargetOwner(targetType, targetId), COMMENT, targetId);
  }

  public void authorizeSyncRecordAccess(DomainPrincipal principal, UUID syncRecordId) {
    OutlookCalendarSyncRecord sync = syncRecords.findById(syncRecordId).orElseThrow(this::notFound);
    authorizeOwnership(principal, sync.getOwnerEmployeeId(), SYNC_RECORD, syncRecordId);
  }

  /** A heatmap cell is owned by its manager; only that manager (or SYSTEM) may drill into it. */
  public void authorizeHeatmapCellAccess(DomainPrincipal principal, UUID cellId) {
    ManagerHeatmapCell cell = heatmapCells.findById(cellId).orElseThrow(this::notFound);
    if (principal instanceof UserPrincipal up
        && !(up.isManager() && cell.getManagerEmployeeId().equals(up.employeeId()))) {
      throw deny404(principal, HEATMAP_CELL, cellId);
    }
    // SYSTEM or the owning manager -> authorized
  }

  // ===== capability checks (403 with a named code) =====

  /** The team-heatmap surface is categorically manager-only — ICs have no team surface (§6). */
  public void authorizeTeamHeatmapAccess(DomainPrincipal principal) {
    if (principal instanceof UserPrincipal up && !up.isManager()) {
      throw deny403(principal, HEATMAP, null, REASON_MANAGER_ROLE, CODE_MANAGER_ROLE_REQUIRED);
    }
  }

  /** Resolving a dispute is a manager capability: the IC owner can see it but cannot resolve it. */
  public void authorizeDisputeResolution(DomainPrincipal principal, UUID disputeId) {
    UUID owner = disputeOwner(disputeId);
    authorizeOwnership(principal, owner, DISPUTE, disputeId); // must at least be visible (else 404)
    if (principal instanceof UserPrincipal up && up.employeeId().equals(owner)) {
      throw deny403(principal, DISPUTE, disputeId, REASON_IC_NO_RESOLVE, CODE_IC_CANNOT_RESOLVE);
    }
    // an authorized manager (or SYSTEM) may resolve
  }

  // ===== core ownership check =====

  private void authorizeOwnership(
      DomainPrincipal principal, UUID ownerEmployeeId, String entityType, UUID entityId) {
    if (principal instanceof UserPrincipal up) {
      boolean authorized =
          up.employeeId().equals(ownerEmployeeId)
              || (up.isManager() && isActiveDirectReport(ownerEmployeeId, up.employeeId()));
      if (!authorized) {
        throw deny404(principal, entityType, entityId);
      }
    }
    // SystemPrincipal -> exempt from self/direct-report checks (authorized)
  }

  private boolean isActiveDirectReport(UUID ownerEmployeeId, UUID managerEmployeeId) {
    return relationships
        .findByDirectReportEmployeeIdAndActiveTrue(ownerEmployeeId)
        .map(r -> r.getManagerEmployeeId().equals(managerEmployeeId))
        .orElse(false);
  }

  // ===== owner resolution (flat-FK findById chains; a missing resource -> 404 WITHOUT audit) =====

  private UUID planOwner(UUID planId) {
    return plans.findById(planId).map(WeeklyPlan::getEmployeeId).orElseThrow(this::notFound);
  }

  private UUID commitmentOwner(UUID commitmentId) {
    WeeklyCommitment c = commitments.findById(commitmentId).orElseThrow(this::notFound);
    return planOwner(c.getWeeklyPlanId());
  }

  private UUID disputeOwner(UUID disputeId) {
    AlignmentDispute d = disputes.findById(disputeId).orElseThrow(this::notFound);
    return commitmentOwner(d.getCommitmentId());
  }

  private UUID reviewOwner(UUID reviewId) {
    ManagerReview r = reviews.findById(reviewId).orElseThrow(this::notFound);
    return planOwner(r.getWeeklyPlanId());
  }

  private UUID commentTargetOwner(CommentTargetType targetType, UUID targetId) {
    return switch (targetType) {
      case PLAN -> planOwner(targetId);
      case COMMITMENT -> commitmentOwner(targetId);
    };
  }

  // ===== denial helpers =====

  private ResourceNotFoundOrUnauthorizedException deny404(
      DomainPrincipal principal, String entityType, UUID entityId) {
    auditer.recordDenial(principal, entityType, entityId, REASON_CROSS_OWNER);
    return new ResourceNotFoundOrUnauthorizedException();
  }

  private AuthorizationDeniedException deny403(
      DomainPrincipal principal, String entityType, UUID entityId, String reason, String code) {
    auditer.recordDenial(principal, entityType, entityId, reason);
    return new AuthorizationDeniedException(code);
  }

  /** A genuinely missing resource: IDOR-safe 404, but NOT audited (no audit-spam on id-probing). */
  private ResourceNotFoundOrUnauthorizedException notFound() {
    return new ResourceNotFoundOrUnauthorizedException();
  }
}
