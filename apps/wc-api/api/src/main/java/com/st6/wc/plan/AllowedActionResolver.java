package com.st6.wc.plan;

import com.st6.wc.action.AllowedAction;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.PlanState;
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

  /** Plan-level affordances for the actor (3.3a: {@code LOCK} only). */
  public List<AllowedAction> planActions(
      UUID actorEmployeeId, WeeklyPlan plan, List<WeeklyCommitment> commitments) {
    if (canLock(actorEmployeeId, plan, commitments)) {
      return List.of(AllowedAction.LOCK);
    }
    return List.of();
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
