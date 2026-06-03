package com.st6.wc.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.st6.wc.action.AllowedAction;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.ReconciliationOutcome;
import com.st6.wc.enums.WorkType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pure-function proof of {@link AllowedActionResolver} (task 3.3a, §15 / Appendix B.1 / F.4). The
 * resolver computes the server-authoritative {@code allowedActions[]} for a plan given the current
 * actor + plan/commitment state — a <strong>UI affordance only, never the authorization
 * source</strong>. The load-bearing predicate is {@code LOCK} (DRAFT ∧ owning IC ∧ ≥1 PLANNED ∧
 * every PLANNED linked to a Supporting Outcome) — the <strong>same</strong> predicate 3.5's {@code
 * PlanLifecycleService} enforces, so 3.5 reuses this (affordance↔enforcement single-source, §15).
 * Other actions are emitted only by the slices that enforce them (no affordance without
 * enforcement).
 */
class AllowedActionResolverTest {

  private final AllowedActionResolver resolver = new AllowedActionResolver();

  private static final UUID OWNER = UUID.randomUUID();
  private static final UUID OTHER = UUID.randomUUID();

  private static WeeklyPlan plan(UUID owner, PlanState state) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(owner);
    p.setWeekStartDate(LocalDate.of(2026, 6, 1));
    p.setWeekEndDate(LocalDate.of(2026, 6, 7));
    p.setState(state);
    return p;
  }

  private static WeeklyCommitment planned(UUID soId) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("t");
    c.setSupportingOutcomeId(soId); // null = unlinked
    c.setPriority(Priority.P1);
    c.setWorkType(WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(AlignmentStatus.NEEDS_REVIEW);
    return c;
  }

  // --- RED #5: LOCK present only when DRAFT ∧ owner ∧ ≥1 planned ∧ all planned linked ----
  @Test
  void lock_present_whenDraftOwnerAllPlannedLinked() {
    List<AllowedAction> actions =
        resolver.planActions(
            OWNER, plan(OWNER, PlanState.DRAFT), List.of(planned(UUID.randomUUID())));
    assertThat(actions).contains(AllowedAction.LOCK);
  }

  @Test
  void lock_absent_whenAnyPlannedUnlinked() {
    List<AllowedAction> actions =
        resolver.planActions(
            OWNER,
            plan(OWNER, PlanState.DRAFT),
            List.of(planned(UUID.randomUUID()), planned(null))); // one unlinked
    assertThat(actions).doesNotContain(AllowedAction.LOCK);
  }

  @Test
  void lock_absent_whenEmptyDraftShell() {
    List<AllowedAction> actions =
        resolver.planActions(OWNER, plan(OWNER, PlanState.DRAFT), List.of()); // no planned
    assertThat(actions).doesNotContain(AllowedAction.LOCK);
  }

  @Test
  void lock_absent_whenActorNotOwner() {
    List<AllowedAction> actions =
        resolver.planActions(
            OTHER, plan(OWNER, PlanState.DRAFT), List.of(planned(UUID.randomUUID())));
    assertThat(actions).doesNotContain(AllowedAction.LOCK);
  }

  @Test
  void lock_absent_whenNotDraft() {
    List<AllowedAction> actions =
        resolver.planActions(
            OWNER, plan(OWNER, PlanState.LOCKED), List.of(planned(UUID.randomUUID())));
    assertThat(actions).doesNotContain(AllowedAction.LOCK);
  }

  // ===================== CLOSE_RECONCILIATION plan affordance (4.5) =====================

  // --- CLOSE_RECONCILIATION present for a RECONCILING owning-IC plan (the close attempt surfaces a
  // 422 if incomplete — not gated on completeness) ----
  @Test
  void close_present_whenReconcilingOwner() {
    List<AllowedAction> actions =
        resolver.planActions(OWNER, plan(OWNER, PlanState.RECONCILING), List.of());
    assertThat(actions).contains(AllowedAction.CLOSE_RECONCILIATION);
  }

  @Test
  void close_absent_whenNotOwner() {
    List<AllowedAction> actions =
        resolver.planActions(OTHER, plan(OWNER, PlanState.RECONCILING), List.of());
    assertThat(actions).doesNotContain(AllowedAction.CLOSE_RECONCILIATION);
  }

  @Test
  void close_absent_whenNotReconciling() {
    for (PlanState state : List.of(PlanState.DRAFT, PlanState.LOCKED, PlanState.RECONCILED)) {
      assertThat(resolver.planActions(OWNER, plan(OWNER, state), List.of()))
          .as("no CLOSE_RECONCILIATION affordance in %s", state)
          .doesNotContain(AllowedAction.CLOSE_RECONCILIATION);
    }
  }

  // --- ADD_UNPLANNED present for an owning IC in LOCKED AND RECONCILING (4.3 enforces both) ----
  @Test
  void addUnplanned_present_whenLockedOrReconcilingOwner() {
    for (PlanState state : List.of(PlanState.LOCKED, PlanState.RECONCILING)) {
      assertThat(resolver.planActions(OWNER, plan(OWNER, state), List.of()))
          .as("ADD_UNPLANNED affordance in %s", state)
          .contains(AllowedAction.ADD_UNPLANNED);
    }
  }

  @Test
  void addUnplanned_absent_whenDraftOrReconciled() {
    for (PlanState state : List.of(PlanState.DRAFT, PlanState.RECONCILED)) {
      assertThat(resolver.planActions(OWNER, plan(OWNER, state), List.of()))
          .as("no ADD_UNPLANNED affordance in %s", state)
          .doesNotContain(AllowedAction.ADD_UNPLANNED);
    }
  }

  @Test
  void addUnplanned_absent_whenNotOwner() {
    assertThat(resolver.planActions(OTHER, plan(OWNER, PlanState.LOCKED), List.of()))
        .doesNotContain(AllowedAction.ADD_UNPLANNED);
  }

  // ===================== commitmentActions — per-commitment CARRY_FORWARD (4.4b)
  // =====================

  private static WeeklyCommitment withOutcome(ReconciliationOutcome outcome) {
    WeeklyCommitment c = planned(UUID.randomUUID());
    c.setReconciliationOutcome(outcome);
    return c;
  }

  // --- CARRY_FORWARD present iff owner ∧ RECONCILING ∧ not-already-carried (the 4.4b predicate)
  // ----
  @Test
  void carryForward_present_whenOwnerReconcilingNotCarried() {
    List<AllowedAction> actions =
        resolver.commitmentActions(OWNER, plan(OWNER, PlanState.RECONCILING), withOutcome(null));
    assertThat(actions).contains(AllowedAction.CARRY_FORWARD);
  }

  // --- absent in any non-RECONCILING state (no affordance without enforcement) ----
  @Test
  void carryForward_absent_whenNotReconciling() {
    for (PlanState state : List.of(PlanState.DRAFT, PlanState.LOCKED, PlanState.RECONCILED)) {
      assertThat(resolver.commitmentActions(OWNER, plan(OWNER, state), withOutcome(null)))
          .as("no CARRY_FORWARD affordance in %s", state)
          .doesNotContain(AllowedAction.CARRY_FORWARD);
    }
  }

  // --- absent once already carried (re-carry is a pointless idempotent no-op — hide it) ----
  @Test
  void carryForward_absent_whenAlreadyCarried() {
    List<AllowedAction> actions =
        resolver.commitmentActions(
            OWNER,
            plan(OWNER, PlanState.RECONCILING),
            withOutcome(ReconciliationOutcome.CARRIED_FORWARD));
    assertThat(actions).doesNotContain(AllowedAction.CARRY_FORWARD);
  }

  // --- absent for a non-owner reader (a manager-direct-report can READ but it's an IC-self action)
  // -
  @Test
  void carryForward_absent_whenNotOwner() {
    List<AllowedAction> actions =
        resolver.commitmentActions(OTHER, plan(OWNER, PlanState.RECONCILING), withOutcome(null));
    assertThat(actions).doesNotContain(AllowedAction.CARRY_FORWARD);
  }

  // --- a completion outcome (PARTIALLY_COMPLETED/BLOCKED/COMPLETED) is still
  // carry-forward-eligible
  // (Q1 sub-q: do NOT restrict to incomplete — only already-carried is hidden) ----
  @Test
  void carryForward_present_whenPriorCompletionOutcome() {
    List<AllowedAction> actions =
        resolver.commitmentActions(
            OWNER,
            plan(OWNER, PlanState.RECONCILING),
            withOutcome(ReconciliationOutcome.PARTIALLY_COMPLETED));
    assertThat(actions).contains(AllowedAction.CARRY_FORWARD);
  }

  // --- an UNPLANNED commitment is equally carry-forward-eligible (the predicate has NO
  // commitmentKind branch — 4.4 accepts + handles an UNPLANNED source → STRATEGIC successor; a
  // future `&& kind==PLANNED` tidy would silently hide the affordance while E12 still accepts it)
  // --
  @Test
  void carryForward_present_whenUnplannedOwnedReconciling() {
    WeeklyCommitment unplanned = withOutcome(null);
    unplanned.setCommitmentKind(CommitmentKind.UNPLANNED);
    unplanned.setWorkType(WorkType.UNPLANNED);
    List<AllowedAction> actions =
        resolver.commitmentActions(OWNER, plan(OWNER, PlanState.RECONCILING), unplanned);
    assertThat(actions).contains(AllowedAction.CARRY_FORWARD);
  }

  // --- single-source pin (§24 "no affordance without enforcement"): every commitment marked
  // CARRY_FORWARD-eligible satisfies exactly the preconditions E12 enforces (owner ∧ RECONCILING) —
  // so the affordance never offers a button CarryForwardService would reject ----
  @Test
  void carryForward_eligibility_impliesEnforcementPreconditions() {
    UUID owner = UUID.randomUUID();
    for (PlanState state : PlanState.values()) {
      for (ReconciliationOutcome outcome :
          new ReconciliationOutcome[] {
            null, ReconciliationOutcome.BLOCKED, ReconciliationOutcome.CARRIED_FORWARD
          }) {
        for (UUID actor : List.of(owner, UUID.randomUUID())) {
          WeeklyPlan p = plan(owner, state);
          boolean eligible =
              resolver
                  .commitmentActions(actor, p, withOutcome(outcome))
                  .contains(AllowedAction.CARRY_FORWARD);
          if (eligible) {
            // exactly E12's gate: owner ∧ RECONCILING (the central authz owner-check + state guard)
            assertThat(actor).as("eligible ⇒ actor owns the plan").isEqualTo(owner);
            assertThat(state).as("eligible ⇒ plan RECONCILING").isEqualTo(PlanState.RECONCILING);
          }
        }
      }
    }
  }
}
