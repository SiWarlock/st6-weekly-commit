package com.st6.wc.plan;

import com.st6.wc.action.AllowedAction;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.ReconciliationOutcome;
import com.st6.wc.enums.ReviewStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Computes the server-authoritative {@code allowedActions[]} for a plan, given the current actor +
 * plan/commitment state (task 3.3a, §15 / Appendix B.1 / F.4). A <strong>UI affordance only — never
 * the authorization source</strong>: the mutation endpoints re-validate eligibility on every call.
 *
 * <p>The load-bearing predicate is {@code LOCK} — granted iff the actor owns the plan, it is {@code
 * DRAFT}, it has ≥1 PLANNED commitment, and <strong>every</strong> PLANNED commitment links a
 * Supporting Outcome (safety rule #1). This is the <strong>same</strong> predicate 3.5's {@code
 * PlanLifecycleService} enforces — 3.5 reuses {@link #canLock} (do NOT duplicate), so the
 * affordance and the enforcement never drift (§15).
 *
 * <p>Per the "no affordance without enforcement" discipline, 3.3a emits only {@code LOCK} (the only
 * affordance applicable to a DRAFT plan whose predicate is defined + soon-enforced). The other
 * actions (START_RECONCILIATION / CLOSE_RECONCILIATION / ADD_UNPLANNED / the commitment-level
 * CARRY_FORWARD / OPEN_DISPUTE / COMMENT) are added by the slices that enforce them.
 */
@Component
public class AllowedActionResolver {

  /**
   * Plan-level affordances for the actor — only the actions whose enforcement exists: {@code LOCK}
   * for a lockable DRAFT plan (3.3a), {@code START_RECONCILIATION} for a LOCKED owning-IC plan
   * (4.2). The RECONCILING actions ({@code ADD_UNPLANNED}/{@code CLOSE_RECONCILIATION}/{@code
   * CARRY_FORWARD}) are emitted by their enforcing slices — "no affordance without enforcement"
   * (§15).
   */
  public List<AllowedAction> planActions(
      UUID actorEmployeeId, WeeklyPlan plan, List<WeeklyCommitment> commitments) {
    List<AllowedAction> actions = new ArrayList<>();
    if (canLock(actorEmployeeId, plan, commitments)) {
      actions.add(AllowedAction.LOCK); // DRAFT-lockable
    }
    if (canStartReconciliation(actorEmployeeId, plan)) {
      actions.add(AllowedAction.START_RECONCILIATION); // LOCKED
    }
    if (canCloseReconciliation(actorEmployeeId, plan)) {
      actions.add(AllowedAction.CLOSE_RECONCILIATION); // RECONCILING (4.5)
    }
    if (canAddUnplanned(actorEmployeeId, plan)) {
      actions.add(AllowedAction.ADD_UNPLANNED); // LOCKED ∨ RECONCILING (4.3 enforces both)
    }
    return List.copyOf(actions);
  }

  /**
   * The {@code CLOSE_RECONCILIATION} precondition (task 4.5) — the owning IC may attempt close on a
   * {@code RECONCILING} plan; NOT completeness-gated (the close attempt surfaces the {@code 422} if
   * incomplete — same forward-only guard {@code PlanLifecycleService.closeReconciliation}
   * enforces).
   */
  public boolean canCloseReconciliation(UUID actorEmployeeId, WeeklyPlan plan) {
    return plan.getEmployeeId().equals(actorEmployeeId) && plan.getState() == PlanState.RECONCILING;
  }

  /**
   * The {@code ADD_UNPLANNED} precondition (task 4.3 enforcement, affordance task 4.5) — the owning
   * IC may add unplanned work while the plan is {@code LOCKED} or {@code RECONCILING} (the same
   * states {@code CommitmentService.createUnplanned} accepts — "no affordance without
   * enforcement").
   */
  public boolean canAddUnplanned(UUID actorEmployeeId, WeeklyPlan plan) {
    return plan.getEmployeeId().equals(actorEmployeeId)
        && (plan.getState() == PlanState.LOCKED || plan.getState() == PlanState.RECONCILING);
  }

  /**
   * Per-commitment affordances for the viewing actor: {@code CARRY_FORWARD} for a carry-forward-
   * eligible commitment (task 4.4b, the owning IC) + {@code OPEN_DISPUTE} for the active direct
   * manager (task 5.5b). {@code viewerIsDirectManager} + {@code hasUnresolvedDispute} are computed
   * by the caller ({@code CommitmentMapper}, reusing the 5.3b nested-dispute resolution) so the
   * resolver stays repo-free ("no affordance without enforcement", §15/§24). {@code COMMENT} joins
   * as its enforcing slice lands.
   */
  public List<AllowedAction> commitmentActions(
      UUID actorEmployeeId,
      WeeklyPlan plan,
      WeeklyCommitment commitment,
      boolean viewerIsDirectManager,
      boolean hasUnresolvedDispute) {
    List<AllowedAction> actions = new ArrayList<>();
    if (canCarryForward(actorEmployeeId, plan, commitment)) {
      actions.add(AllowedAction.CARRY_FORWARD); // owning IC ∧ RECONCILING
    }
    if (canOpenDispute(viewerIsDirectManager, plan, hasUnresolvedDispute)) {
      actions.add(AllowedAction.OPEN_DISPUTE); // direct manager ∧ LOCKED+ ∧ no unresolved dispute
    }
    return List.copyOf(actions);
  }

  /**
   * The {@code OPEN_DISPUTE} affordance predicate (task 5.5b) — the active direct manager viewing a
   * report's <strong>post-lock</strong> plan may open a dispute on a commitment that has no
   * unresolved dispute yet (rule #6). MIRRORS — not shares — E17's enforcement (the authorizer is
   * void-and-throw, §31): the boolean parallel of {@code authorizeDisputeCreation}
   * (manager-of-owner, here {@code viewerIsDirectManager}) + {@code DisputeService.open}'s
   * non-DRAFT state guard + the single-unresolved pre-check. So {@code canOpenDispute}-true ⟹ E17
   * accepts (§24).
   */
  public boolean canOpenDispute(
      boolean viewerIsDirectManager, WeeklyPlan plan, boolean hasUnresolvedDispute) {
    return viewerIsDirectManager && plan.getState() != PlanState.DRAFT && !hasUnresolvedDispute;
  }

  /**
   * The {@code MARK_REVIEWED} affordance predicate (task 6.8) — the active direct manager viewing a
   * report's <strong>post-lock</strong> plan may mark a still-pending ({@code NOT_REVIEWED})
   * review. A §31 <strong>UX-narrowed subset</strong> of E16's enforcement: {@code
   * ReviewService.markReviewed} authorizes manager-of-owner + guards only {@code state != DRAFT}
   * (it re-derives the status, so it would re-accept an already-reviewed review) — the affordance
   * hides the button once {@code REVIEWED}/{@code REVIEWED_WITH_DISPUTES}. So {@code
   * canMarkReviewed}-true ⟹ E16 accepts (§24/§31). MIRRORS — never calls — the void-throw {@code
   * authorizeReviewMutation} ({@code viewerIsDirectManager} is the boolean parallel, resolved once
   * in {@code PlanMapper}).
   */
  public boolean canMarkReviewed(
      boolean viewerIsDirectManager, PlanState planState, ReviewStatus reviewStatus) {
    return viewerIsDirectManager
        && planState != PlanState.DRAFT
        && reviewStatus == ReviewStatus.NOT_REVIEWED;
  }

  /**
   * The {@code CARRY_FORWARD} affordance predicate (task 4.4b) — the owning IC may carry a
   * commitment forward while the plan is {@code RECONCILING}, EXCEPT one already carried (re-carry
   * is a pointless idempotent no-op, so the button is hidden). There is deliberately <strong>no
   * {@code commitmentKind} branch</strong> (E12 accepts + handles an UNPLANNED source) and
   * <strong>no completion-outcome restriction</strong> (a {@code PARTIALLY_COMPLETED}/{@code
   * BLOCKED} item is the prime carry-forward case).
   *
   * <p>This is a UX-narrowed <strong>subset</strong> of what {@code CarryForwardService} enforces
   * (E12 idempotent-accepts an already-carried re-invoke; the affordance hides it) — NOT a literal
   * shared gate like {@link #canLock}. The §24 invariant that holds is "no affordance without
   * enforcement": {@code canCarryForward}-true ⟹ E12 accepts (owner ∧ {@code RECONCILING}). {@code
   * CarryForwardService} is NOT changed to reuse this — doing so would break E12's idempotent
   * re-carry (REQ-D-006).
   */
  public boolean canCarryForward(
      UUID actorEmployeeId, WeeklyPlan plan, WeeklyCommitment commitment) {
    return plan.getEmployeeId().equals(actorEmployeeId)
        && plan.getState() == PlanState.RECONCILING
        && commitment.getReconciliationOutcome() != ReconciliationOutcome.CARRIED_FORWARD;
  }

  /**
   * The {@code START_RECONCILIATION} precondition (task 4.2) — the owning IC may start
   * reconciliation on a {@code LOCKED} plan; the same forward-only guard {@code
   * PlanLifecycleService} enforces (§15).
   */
  public boolean canStartReconciliation(UUID actorEmployeeId, WeeklyPlan plan) {
    return plan.getEmployeeId().equals(actorEmployeeId) && plan.getState() == PlanState.LOCKED;
  }

  /**
   * The {@code LOCK} precondition (safety rule #1) — shared with 3.5's enforcement so the UI
   * affordance and the server gate are a single source of truth (§15).
   *
   * @return true iff actor owns the plan, plan is {@code DRAFT}, ≥1 PLANNED commitment exists, and
   *     every PLANNED commitment links a Supporting Outcome.
   */
  public boolean canLock(
      UUID actorEmployeeId, WeeklyPlan plan, List<WeeklyCommitment> commitments) {
    if (!plan.getEmployeeId().equals(actorEmployeeId) || plan.getState() != PlanState.DRAFT) {
      return false;
    }
    List<WeeklyCommitment> planned =
        commitments.stream().filter(c -> c.getCommitmentKind() == CommitmentKind.PLANNED).toList();
    return !planned.isEmpty() && planned.stream().allMatch(c -> c.getSupportingOutcomeId() != null);
  }
}
