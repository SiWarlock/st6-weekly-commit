package com.st6.wc.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.st6.wc.action.AllowedAction;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
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
}
