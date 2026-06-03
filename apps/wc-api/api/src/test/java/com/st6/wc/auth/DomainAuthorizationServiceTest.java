package com.st6.wc.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.st6.wc.comment.Comment;
import com.st6.wc.comment.repo.CommentRepository;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.dispute.AlignmentDispute;
import com.st6.wc.dispute.repo.AlignmentDisputeRepository;
import com.st6.wc.enums.CommentTargetType;
import com.st6.wc.enums.RoleType;
import com.st6.wc.identity.SystemPrincipal;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.ManagerHeatmapCell;
import com.st6.wc.projection.repo.ManagerHeatmapCellRepository;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.ManagerReview;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code DomainAuthorizationService} unit proof (task 2.5, SAFETY-CRITICAL — rule #3 IDOR;
 * §6/§5/§15 /§16). Resolution chains + the IC-self / manager-active-direct-report scope are
 * exercised with mocked repos + a mocked {@link AuthorizationDeniedAuditer} (the Testcontainers
 * IDOR matrix + REQUIRES_NEW proofs live in {@code AuthorizationIdorMatrixTest} / {@code
 * AuthorizationDeniedAuditerTest}). Pins the load-bearing safety rules:
 *
 * <ul>
 *   <li>cross-owner access → IDOR-safe {@code 404} ({@link
 *       ResourceNotFoundOrUnauthorizedException}) + <strong>exactly one</strong> denial audit
 *       (existence-hiding);
 *   <li>capability denial → {@code 403} ({@link AuthorizationDeniedException}) with a named code
 *       (IC can <em>see</em> a dispute but not resolve it; ICs have no team-heatmap surface);
 *   <li>manager scope is the <em>active</em> {@code manager_relationship} (inactive removes scope);
 *   <li>a manager who owns a plan is authorized <em>as IC</em> (relationship-driven, §4);
 *   <li>{@link SystemPrincipal} is exempt from self/direct-report checks;
 *   <li>a missing resource → {@code 404} with NO audit (no audit-spam on probes, 2.3 precedent).
 * </ul>
 */
class DomainAuthorizationServiceTest {

  private final WeeklyPlanRepository plans = mock(WeeklyPlanRepository.class);
  private final WeeklyCommitmentRepository commitments = mock(WeeklyCommitmentRepository.class);
  private final AlignmentDisputeRepository disputes = mock(AlignmentDisputeRepository.class);
  private final CommentRepository comments = mock(CommentRepository.class);
  private final ManagerReviewRepository reviews = mock(ManagerReviewRepository.class);
  private final ManagerHeatmapCellRepository heatmapCells =
      mock(ManagerHeatmapCellRepository.class);
  private final OutlookCalendarSyncRecordRepository syncRecords =
      mock(OutlookCalendarSyncRecordRepository.class);
  private final ManagerRelationshipRepository relationships =
      mock(ManagerRelationshipRepository.class);
  private final AuthorizationDeniedAuditer auditer = mock(AuthorizationDeniedAuditer.class);

  private final DomainAuthorizationService authz =
      new DomainAuthorizationService(
          plans,
          commitments,
          disputes,
          comments,
          reviews,
          heatmapCells,
          syncRecords,
          relationships,
          auditer);

  // ===== IC self-access (authorized, no audit) =====

  // --- 1a. IC authorized on its own plan ----
  @Test
  void icAuthorizedOnOwnPlan() {
    UUID ic = UUID.randomUUID();
    UUID planId = UUID.randomUUID();
    when(plans.findById(planId)).thenReturn(Optional.of(plan(planId, ic)));

    assertThatCode(() -> authz.authorizePlanAccess(user(ic, RoleType.IC, false), planId))
        .doesNotThrowAnyException();
    verifyNoInteractions(auditer);
  }

  // --- 1b. IC authorized on its own commitment / dispute / comment-target / review ----
  @Test
  void icAuthorizedOnOwnCommitmentDisputeCommentReview() {
    UUID ic = UUID.randomUUID();
    UUID planId = UUID.randomUUID();
    UUID commitmentId = UUID.randomUUID();
    UUID disputeId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();
    UUID commentId = UUID.randomUUID();
    when(plans.findById(planId)).thenReturn(Optional.of(plan(planId, ic)));
    when(commitments.findById(commitmentId))
        .thenReturn(Optional.of(commitment(commitmentId, planId)));
    when(disputes.findById(disputeId))
        .thenReturn(Optional.of(dispute(disputeId, commitmentId, UUID.randomUUID())));
    when(reviews.findById(reviewId))
        .thenReturn(Optional.of(review(reviewId, planId, UUID.randomUUID())));
    when(comments.findById(commentId))
        .thenReturn(Optional.of(comment(commentId, CommentTargetType.PLAN, planId, ic)));

    UserPrincipal principal = user(ic, RoleType.IC, false);
    assertThatCode(
            () -> {
              authz.authorizeCommitmentAccess(principal, commitmentId);
              authz.authorizeDisputeAccess(principal, disputeId);
              authz.authorizeReviewAccess(principal, reviewId);
              authz.authorizeCommentAccess(principal, commentId);
              authz.authorizeCommentTargetAccess(principal, CommentTargetType.PLAN, planId);
              authz.authorizeCommentTargetAccess(
                  principal, CommentTargetType.COMMITMENT, commitmentId);
            })
        .doesNotThrowAnyException();
    verifyNoInteractions(auditer);
  }

  // ===== manager active-direct-report scope =====

  // --- 2. manager authorized on an active direct report's resource ----
  @Test
  void managerAuthorizedOnActiveDirectReportResource() {
    UUID mgr = UUID.randomUUID();
    UUID ic = UUID.randomUUID();
    UUID planId = UUID.randomUUID();
    when(plans.findById(planId)).thenReturn(Optional.of(plan(planId, ic)));
    when(relationships.findByDirectReportEmployeeIdAndActiveTrue(ic))
        .thenReturn(Optional.of(relationship(mgr, ic, true)));

    assertThatCode(() -> authz.authorizePlanAccess(user(mgr, RoleType.MANAGER, true), planId))
        .doesNotThrowAnyException();
    verifyNoInteractions(auditer);
  }

  // --- 3. a manager who OWNS a plan is authorized as IC (relationship-driven, §4) ----
  @Test
  void managerWhoOwnsPlanAuthorizedAsIc() {
    UUID mgr = UUID.randomUUID();
    UUID planId = UUID.randomUUID();
    when(plans.findById(planId)).thenReturn(Optional.of(plan(planId, mgr))); // owner == the manager

    assertThatCode(() -> authz.authorizePlanAccess(user(mgr, RoleType.MANAGER, true), planId))
        .doesNotThrowAnyException();
    // self short-circuit: the relationship finder is never consulted.
    verifyNoInteractions(relationships);
    verifyNoInteractions(auditer);
  }

  // ===== cross-owner denials -> 404 IDOR-safe + exactly one audit =====

  // --- 4. IC -> another IC's plan -> 404 + one audit ----
  @Test
  void icCrossIcPlan_throws404_oneAudit() {
    UUID ic1 = UUID.randomUUID();
    UUID ic2 = UUID.randomUUID();
    UUID planId = UUID.randomUUID();
    when(plans.findById(planId)).thenReturn(Optional.of(plan(planId, ic2)));

    UserPrincipal principal = user(ic1, RoleType.IC, false);
    assertThatThrownBy(() -> authz.authorizePlanAccess(principal, planId))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verify(auditer, times(1)).recordDenial(eq(principal), eq("Plan"), eq(planId), anyString());
    verifyNoMoreInteractions(auditer);
  }

  // --- 5. manager -> a non-direct-report (other team) plan -> 404 + one audit ----
  @Test
  void managerCrossTeamPlan_throws404_oneAudit() {
    UUID mgr = UUID.randomUUID();
    UUID otherMgr = UUID.randomUUID();
    UUID icOther = UUID.randomUUID();
    UUID planId = UUID.randomUUID();
    when(plans.findById(planId)).thenReturn(Optional.of(plan(planId, icOther)));
    // the report belongs to a DIFFERENT active manager
    when(relationships.findByDirectReportEmployeeIdAndActiveTrue(icOther))
        .thenReturn(Optional.of(relationship(otherMgr, icOther, true)));

    UserPrincipal principal = user(mgr, RoleType.MANAGER, true);
    assertThatThrownBy(() -> authz.authorizePlanAccess(principal, planId))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verify(auditer, times(1)).recordDenial(eq(principal), eq("Plan"), eq(planId), anyString());
    verifyNoMoreInteractions(auditer);
  }

  // --- 6. inactive relationship removes scope -> 404 + audit ----
  @Test
  void inactiveRelationshipRemovesScope_throws404() {
    UUID mgr = UUID.randomUUID();
    UUID ic = UUID.randomUUID();
    UUID planId = UUID.randomUUID();
    when(plans.findById(planId)).thenReturn(Optional.of(plan(planId, ic)));
    // the active-manager finder is empty because the only relationship is active=false
    when(relationships.findByDirectReportEmployeeIdAndActiveTrue(ic)).thenReturn(Optional.empty());

    UserPrincipal principal = user(mgr, RoleType.MANAGER, true);
    assertThatThrownBy(() -> authz.authorizePlanAccess(principal, planId))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verify(auditer, times(1)).recordDenial(eq(principal), eq("Plan"), eq(planId), anyString());
  }

  // ===== capability denials -> 403 with named code =====

  // --- 7. IC attempts to resolve a dispute on its OWN commitment -> 403 IC_CANNOT_RESOLVE_DISPUTE
  // -
  @Test
  void icResolveDispute_throws403_icCannotResolve() {
    UUID ic = UUID.randomUUID();
    UUID planId = UUID.randomUUID();
    UUID commitmentId = UUID.randomUUID();
    UUID disputeId = UUID.randomUUID();
    when(disputes.findById(disputeId))
        .thenReturn(Optional.of(dispute(disputeId, commitmentId, UUID.randomUUID())));
    when(commitments.findById(commitmentId))
        .thenReturn(Optional.of(commitment(commitmentId, planId)));
    when(plans.findById(planId)).thenReturn(Optional.of(plan(planId, ic)));

    UserPrincipal principal = user(ic, RoleType.IC, false);
    assertThatThrownBy(() -> authz.authorizeDisputeResolution(principal, disputeId))
        .isInstanceOf(AuthorizationDeniedException.class)
        .hasFieldOrPropertyWithValue("code", "IC_CANNOT_RESOLVE_DISPUTE");
    verify(auditer, times(1))
        .recordDenial(eq(principal), eq("Dispute"), eq(disputeId), anyString());
  }

  // --- 8. IC accesses the team heatmap surface -> 403 (coarse role denial) ----
  @Test
  void icTeamHeatmap_throws403() {
    UserPrincipal principal = user(UUID.randomUUID(), RoleType.IC, false);
    assertThatThrownBy(() -> authz.authorizeTeamHeatmapAccess(principal))
        .isInstanceOf(AuthorizationDeniedException.class)
        .hasFieldOrPropertyWithValue("code", "MANAGER_ROLE_REQUIRED");
    verify(auditer, times(1)).recordDenial(eq(principal), eq("Heatmap"), eq(null), anyString());
  }

  // --- 9. comment on an unauthorized target -> 404 (§11 target IDOR) ----
  @Test
  void commentUnauthorizedTarget_throws404() {
    UUID ic1 = UUID.randomUUID();
    UUID ic2 = UUID.randomUUID();
    UUID targetPlanId = UUID.randomUUID();
    when(plans.findById(targetPlanId)).thenReturn(Optional.of(plan(targetPlanId, ic2)));

    UserPrincipal principal = user(ic1, RoleType.IC, false);
    assertThatThrownBy(
            () ->
                authz.authorizeCommentTargetAccess(principal, CommentTargetType.PLAN, targetPlanId))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verify(auditer, times(1))
        .recordDenial(eq(principal), eq("Comment"), eq(targetPlanId), anyString());
  }

  // --- 10. sync-retry on an unowned record -> 404 + audit ----
  @Test
  void syncRetryUnownedRecord_throws404() {
    UUID ic1 = UUID.randomUUID();
    UUID ownerOther = UUID.randomUUID();
    UUID syncId = UUID.randomUUID();
    when(syncRecords.findById(syncId)).thenReturn(Optional.of(syncRecord(syncId, ownerOther)));

    UserPrincipal principal = user(ic1, RoleType.IC, false);
    assertThatThrownBy(() -> authz.authorizeSyncRecordAccess(principal, syncId))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verify(auditer, times(1))
        .recordDenial(eq(principal), eq("SyncRecord"), eq(syncId), anyString());
  }

  // ===== heatmap cell drill-down (manager-owned resource) =====

  // --- 11. manager authorized on its OWN heatmap cell + the team heatmap surface ----
  @Test
  void managerAuthorizedOnOwnHeatmapCellAndTeamSurface() {
    UUID mgr = UUID.randomUUID();
    UUID cellId = UUID.randomUUID();
    when(heatmapCells.findById(cellId))
        .thenReturn(Optional.of(heatmapCell(cellId, mgr, UUID.randomUUID())));

    UserPrincipal principal = user(mgr, RoleType.MANAGER, true);
    assertThatCode(
            () -> {
              authz.authorizeTeamHeatmapAccess(principal);
              authz.authorizeHeatmapCellAccess(principal, cellId);
            })
        .doesNotThrowAnyException();
    verifyNoInteractions(auditer);
  }

  // --- 12. manager opens a heatmap cell that is NOT their own -> 404 ----
  @Test
  void managerForeignHeatmapCell_throws404() {
    UUID mgr = UUID.randomUUID();
    UUID otherMgr = UUID.randomUUID();
    UUID cellId = UUID.randomUUID();
    when(heatmapCells.findById(cellId))
        .thenReturn(Optional.of(heatmapCell(cellId, otherMgr, UUID.randomUUID())));

    UserPrincipal principal = user(mgr, RoleType.MANAGER, true);
    assertThatThrownBy(() -> authz.authorizeHeatmapCellAccess(principal, cellId))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verify(auditer, times(1))
        .recordDenial(eq(principal), eq("HeatmapCell"), eq(cellId), anyString());
  }

  // ===== SYSTEM exemption (authorized across every surface, no scope lookup, no audit) =====

  // --- 13. SystemPrincipal is exempt from self/direct-report checks on ALL surfaces ----
  @Test
  void systemPrincipalExemptFromSelfAndDirectReportChecks() {
    UUID planId = UUID.randomUUID();
    UUID commitmentId = UUID.randomUUID();
    UUID disputeId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();
    UUID commentId = UUID.randomUUID();
    UUID syncId = UUID.randomUUID();
    UUID cellId = UUID.randomUUID();
    when(plans.findById(planId)).thenReturn(Optional.of(plan(planId, UUID.randomUUID())));
    when(commitments.findById(commitmentId))
        .thenReturn(Optional.of(commitment(commitmentId, planId)));
    when(disputes.findById(disputeId))
        .thenReturn(Optional.of(dispute(disputeId, commitmentId, UUID.randomUUID())));
    when(reviews.findById(reviewId))
        .thenReturn(Optional.of(review(reviewId, planId, UUID.randomUUID())));
    when(comments.findById(commentId))
        .thenReturn(
            Optional.of(comment(commentId, CommentTargetType.PLAN, planId, UUID.randomUUID())));
    when(syncRecords.findById(syncId))
        .thenReturn(Optional.of(syncRecord(syncId, UUID.randomUUID())));
    when(heatmapCells.findById(cellId))
        .thenReturn(Optional.of(heatmapCell(cellId, UUID.randomUUID(), UUID.randomUUID())));

    SystemPrincipal system = SystemPrincipal.INSTANCE;
    assertThatCode(
            () -> {
              authz.authorizePlanAccess(system, planId);
              authz.authorizeCommitmentAccess(system, commitmentId);
              authz.authorizeDisputeAccess(system, disputeId);
              authz.authorizeReviewAccess(system, reviewId);
              authz.authorizeCommentAccess(system, commentId);
              authz.authorizeCommentTargetAccess(system, CommentTargetType.PLAN, planId);
              authz.authorizeSyncRecordAccess(system, syncId);
              authz.authorizeHeatmapCellAccess(system, cellId);
              authz.authorizeTeamHeatmapAccess(system);
              authz.authorizeDisputeResolution(system, disputeId); // SYSTEM may resolve
            })
        .doesNotThrowAnyException();
    verifyNoInteractions(relationships); // exempt — no scope lookup
    verifyNoInteractions(auditer);
  }

  // --- 13b. a manager resolves a dispute on an ACTIVE direct report's commitment -> authorized
  // ----
  @Test
  void managerResolvesDirectReportDispute_authorized() {
    UUID mgr = UUID.randomUUID();
    UUID ic = UUID.randomUUID();
    UUID planId = UUID.randomUUID();
    UUID commitmentId = UUID.randomUUID();
    UUID disputeId = UUID.randomUUID();
    when(disputes.findById(disputeId))
        .thenReturn(Optional.of(dispute(disputeId, commitmentId, mgr)));
    when(commitments.findById(commitmentId))
        .thenReturn(Optional.of(commitment(commitmentId, planId)));
    when(plans.findById(planId)).thenReturn(Optional.of(plan(planId, ic)));
    when(relationships.findByDirectReportEmployeeIdAndActiveTrue(ic))
        .thenReturn(Optional.of(relationship(mgr, ic, true)));

    // the manager (not the IC owner) may resolve -> no 403, no audit
    assertThatCode(
            () -> authz.authorizeDisputeResolution(user(mgr, RoleType.MANAGER, true), disputeId))
        .doesNotThrowAnyException();
    verifyNoInteractions(auditer);
  }

  // --- 13c. an IC reaching a specific heatmap cell -> 404 (manager-owned resource) ----
  @Test
  void icHeatmapCell_throws404() {
    UUID ic = UUID.randomUUID();
    UUID cellId = UUID.randomUUID();
    when(heatmapCells.findById(cellId))
        .thenReturn(Optional.of(heatmapCell(cellId, UUID.randomUUID(), ic)));

    UserPrincipal principal = user(ic, RoleType.IC, false);
    assertThatThrownBy(() -> authz.authorizeHeatmapCellAccess(principal, cellId))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verify(auditer, times(1))
        .recordDenial(eq(principal), eq("HeatmapCell"), eq(cellId), anyString());
  }

  // ===== no audit-spam on probes =====

  // --- 14. a genuinely missing resource -> 404 with NO audit (probe, not a denial) ----
  @Test
  void missingResource_throws404_withoutAudit() {
    UUID planId = UUID.randomUUID();
    when(plans.findById(planId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> authz.authorizePlanAccess(user(UUID.randomUUID(), RoleType.IC, false), planId))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verifyNoInteractions(auditer); // no existence leak AND no audit-spam on a nonexistent id
  }

  // ===== commitment mutation: access + IC-owner-only capability (task 3.4b) =====

  // --- M1. the owning IC may mutate its own commitment -> authorized, no scope lookup, no audit --
  @Test
  void commitmentMutation_ownerIc_authorized() {
    UUID ic = UUID.randomUUID();
    UUID planId = UUID.randomUUID();
    UUID commitmentId = UUID.randomUUID();
    when(commitments.findById(commitmentId))
        .thenReturn(Optional.of(commitment(commitmentId, planId)));
    when(plans.findById(planId)).thenReturn(Optional.of(plan(planId, ic)));

    assertThatCode(
            () -> authz.authorizeCommitmentMutation(user(ic, RoleType.IC, false), commitmentId))
        .doesNotThrowAnyException();
    verifyNoInteractions(relationships); // self short-circuit
    verifyNoInteractions(auditer);
  }

  // --- M2. a manager-direct-report can SEE the commitment but NOT mutate it -> 403 + one audit
  // ----
  @Test
  void commitmentMutation_managerDirectReport_throws403_oneAudit() {
    UUID mgr = UUID.randomUUID();
    UUID ic = UUID.randomUUID();
    UUID planId = UUID.randomUUID();
    UUID commitmentId = UUID.randomUUID();
    when(commitments.findById(commitmentId))
        .thenReturn(Optional.of(commitment(commitmentId, planId)));
    when(plans.findById(planId)).thenReturn(Optional.of(plan(planId, ic)));
    when(relationships.findByDirectReportEmployeeIdAndActiveTrue(ic))
        .thenReturn(Optional.of(relationship(mgr, ic, true)));

    UserPrincipal principal = user(mgr, RoleType.MANAGER, true);
    assertThatThrownBy(() -> authz.authorizeCommitmentMutation(principal, commitmentId))
        .isInstanceOf(AuthorizationDeniedException.class)
        .hasFieldOrPropertyWithValue("code", "COMMITMENT_OWNER_REQUIRED");
    verify(auditer, times(1))
        .recordDenial(eq(principal), eq("Commitment"), eq(commitmentId), anyString());
  }

  // --- M3. a cross-IC mutation attempt -> IDOR-safe 404 + one audit (existence-hidden) ----
  @Test
  void commitmentMutation_crossIc_throws404_oneAudit() {
    UUID ic1 = UUID.randomUUID();
    UUID ic2 = UUID.randomUUID();
    UUID planId = UUID.randomUUID();
    UUID commitmentId = UUID.randomUUID();
    when(commitments.findById(commitmentId))
        .thenReturn(Optional.of(commitment(commitmentId, planId)));
    when(plans.findById(planId)).thenReturn(Optional.of(plan(planId, ic2)));

    UserPrincipal principal = user(ic1, RoleType.IC, false);
    assertThatThrownBy(() -> authz.authorizeCommitmentMutation(principal, commitmentId))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verify(auditer, times(1))
        .recordDenial(eq(principal), eq("Commitment"), eq(commitmentId), anyString());
  }

  // --- M4. SYSTEM is exempt from the owner check -> authorized, no audit ----
  @Test
  void commitmentMutation_systemExempt_authorized() {
    UUID planId = UUID.randomUUID();
    UUID commitmentId = UUID.randomUUID();
    when(commitments.findById(commitmentId))
        .thenReturn(Optional.of(commitment(commitmentId, planId)));
    when(plans.findById(planId)).thenReturn(Optional.of(plan(planId, UUID.randomUUID())));

    assertThatCode(() -> authz.authorizeCommitmentMutation(SystemPrincipal.INSTANCE, commitmentId))
        .doesNotThrowAnyException();
    verifyNoInteractions(auditer);
  }

  // --- M5. a genuinely missing commitment -> 404 with NO audit (probe, not a denial) ----
  @Test
  void commitmentMutation_missingCommitment_throws404_withoutAudit() {
    UUID commitmentId = UUID.randomUUID();
    when(commitments.findById(commitmentId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                authz.authorizeCommitmentMutation(
                    user(UUID.randomUUID(), RoleType.IC, false), commitmentId))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
    verifyNoInteractions(auditer);
  }

  // ===== fixtures =====

  private static UserPrincipal user(UUID id, RoleType role, boolean isManager) {
    return new UserPrincipal(id, role, isManager);
  }

  private static WeeklyPlan plan(UUID id, UUID ownerIc) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(id);
    p.setEmployeeId(ownerIc);
    return p;
  }

  private static WeeklyCommitment commitment(UUID id, UUID planId) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(id);
    c.setWeeklyPlanId(planId);
    return c;
  }

  private static AlignmentDispute dispute(UUID id, UUID commitmentId, UUID managerId) {
    AlignmentDispute d = new AlignmentDispute();
    d.setId(id);
    d.setCommitmentId(commitmentId);
    d.setManagerEmployeeId(managerId);
    return d;
  }

  private static Comment comment(UUID id, CommentTargetType type, UUID targetId, UUID author) {
    Comment c = new Comment();
    c.setId(id);
    c.setTargetType(type);
    c.setTargetId(targetId);
    c.setAuthorEmployeeId(author);
    return c;
  }

  private static ManagerReview review(UUID id, UUID planId, UUID managerId) {
    ManagerReview r = new ManagerReview();
    r.setId(id);
    r.setWeeklyPlanId(planId);
    r.setManagerEmployeeId(managerId);
    return r;
  }

  private static ManagerHeatmapCell heatmapCell(UUID id, UUID managerId, UUID employeeId) {
    ManagerHeatmapCell cell = new ManagerHeatmapCell();
    cell.setId(id);
    cell.setManagerEmployeeId(managerId);
    cell.setEmployeeId(employeeId);
    return cell;
  }

  private static OutlookCalendarSyncRecord syncRecord(UUID id, UUID ownerId) {
    OutlookCalendarSyncRecord s = new OutlookCalendarSyncRecord();
    s.setId(id);
    s.setOwnerEmployeeId(ownerId);
    return s;
  }

  private static ManagerRelationship relationship(UUID managerId, UUID reportId, boolean active) {
    ManagerRelationship r = new ManagerRelationship();
    r.setId(UUID.randomUUID());
    r.setManagerEmployeeId(managerId);
    r.setDirectReportEmployeeId(reportId);
    r.setActive(active);
    return r;
  }
}
