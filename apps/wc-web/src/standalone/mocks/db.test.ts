import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  resetDb,
  getPlanForPersona,
  getPlanById,
  getCommitment,
  findDispute,
  openDispute,
  respondDispute,
  resolveDispute,
  commandCenterPage,
  MockDbError,
} from './db';
import { waitForServiceWorkerControl } from './swControl';
import { IC_3, IC_4, MGR_1, ALL_PLANS } from './fixtures';
import type { WeeklyCommitmentDto, WeeklyPlanDto } from '../../shared/lib/dtos';

// 9.15 — the deterministic pins for the standalone MSW *mutable* db: the dispute
// lifecycle (open/respond/resolve) writes through a seeded in-memory store so the
// plan re-reads reflect the live dispute nest (B.6), the manager-review re-derives
// (REVIEWED↔REVIEWED_WITH_DISPUTES, §3) and `emitDisputeAffordances` tracks live
// state — the live disputes-loop demo/QA. Plus the cold-install boot ordering
// (await SW control) so a fresh browser loads cleanly (Finding #1). Demo-only,
// standalone, no contract change (REQ-I-008 stays green — guarded by boundary.test.ts).

/** Capture the MockDbError a mutation throws (fails the test if it doesn't throw). */
function captureThrow(fn: () => unknown): MockDbError {
  try {
    fn();
  } catch (e) {
    return e as MockDbError;
  }
  throw new Error('expected the mutation to throw a MockDbError');
}

/** The commitment carrying the seeded OPEN dispute on ic-3's plan (owner view). */
function disputedCommitment(): WeeklyCommitmentDto {
  const plan = getPlanForPersona(IC_3);
  const c = plan.commitments.find((x) => x.dispute);
  if (!c) {
    throw new Error('fixture invariant: ic-3 should seed one disputed commitment');
  }
  return c;
}

/** An UNDISPUTED commitment on ic-4's (RECONCILED + REVIEWED) plan. */
function undisputedReviewedCommitment(): {
  plan: WeeklyPlanDto;
  commitment: WeeklyCommitmentDto;
} {
  const plan = getPlanForPersona(IC_4);
  const commitment = plan.commitments.find((c) => !c.dispute);
  if (!commitment) {
    throw new Error('fixture invariant: ic-4 should have an undisputed commitment');
  }
  return { plan, commitment };
}

describe('9.15 standalone MSW mutable db — dispute lifecycle write-through', () => {
  beforeEach(() => {
    // Persist + seed-once is the production semantic (Q3); tests re-seed a fresh
    // deep clone for isolation so each case starts from the canonical fixture world.
    resetDb();
  });

  it('open_dispute_writes_through: E17 creates an OPEN dispute that re-reads nest + bumps the review (+1, REVIEWED_WITH_DISPUTES when reviewed)', () => {
    const { plan, commitment } = undisputedReviewedCommitment();
    expect(plan.managerReview?.status).toBe('REVIEWED');
    const before = plan.managerReview?.unresolvedDisputeCount ?? 0;

    const created = openDispute(
      commitment.id,
      { flagType: 'MISALIGNED', managerNote: 'Re-link this to the reliability outcome.' },
      MGR_1,
    );
    expect(created.status).toBe('OPEN');
    expect(created.flagType).toBe('MISALIGNED');
    expect(created.commitmentId).toBe(commitment.id);
    expect(created.managerEmployeeId).toBe(MGR_1);
    expect(typeof created.id).toBe('string');

    // Subsequent read nests it (B.6) and the review re-derives (§3).
    const after = getPlanById(plan.id);
    const reread = after?.commitments.find((c) => c.id === commitment.id);
    expect(reread?.dispute?.status).toBe('OPEN');
    expect(reread?.dispute?.managerNote).toBe(
      'Re-link this to the reliability outcome.',
    );
    expect(after?.managerReview?.unresolvedDisputeCount).toBe(before + 1);
    expect(after?.managerReview?.status).toBe('REVIEWED_WITH_DISPUTES');
  });

  it('open_rejects_second_unresolved_dispute: a 2nd open on a commitment with an unresolved dispute → 409 SECOND_OPEN_DISPUTE, existing dispute + count untouched (§3 single-unresolved)', () => {
    const c = disputedCommitment();
    const planBefore = getPlanForPersona(IC_3);
    const countBefore = planBefore.managerReview?.unresolvedDisputeCount;

    const err = captureThrow(() =>
      openDispute(c.id, { flagType: 'NEEDS_REVISION', managerNote: 'again' }, MGR_1),
    );
    expect(err).toBeInstanceOf(MockDbError);
    expect(err.status).toBe(409);
    expect(err.code).toBe('SECOND_OPEN_DISPUTE');

    const planAfter = getPlanForPersona(IC_3);
    const reread = planAfter.commitments.find((x) => x.id === c.id);
    expect(reread?.dispute?.id).toBe(c.dispute?.id); // unchanged
    expect(planAfter.managerReview?.unresolvedDisputeCount).toBe(countBefore);
  });

  it('open_on_unknown_commitment_404: opening a dispute on a non-existent commitment id → 404 (not-found, never leaks existence)', () => {
    const err = captureThrow(() =>
      openDispute('commit-does-not-exist', { flagType: 'MISALIGNED', managerNote: 'x' }, MGR_1),
    );
    expect(err.status).toBe(404);
  });

  it('respond_transitions_open_to_ic_responded: E18 OPEN→IC_RESPONDED, sets icResponse + an optional newSupportingOutcomeId re-links the commitment SO; count unchanged (still unresolved)', () => {
    const c = disputedCommitment();
    const disputeId = c.dispute!.id;
    // A valid, different SO id read from another commitment (no hard-coded ids).
    const plan = getPlanForPersona(IC_3);
    const otherSo = plan.commitments
      .map((x) => x.supportingOutcomeId)
      .find((so) => so && so !== c.supportingOutcomeId)!;
    const countBefore = plan.managerReview?.unresolvedDisputeCount;

    const updated = respondDispute(disputeId, {
      icResponse: 'Re-scoped — this maps to reliability after all.',
      newSupportingOutcomeId: otherSo,
    });
    expect(updated.status).toBe('IC_RESPONDED');
    expect(updated.icResponse).toBe('Re-scoped — this maps to reliability after all.');

    const after = getPlanForPersona(IC_3);
    const reread = after.commitments.find((x) => x.id === c.id);
    expect(reread?.dispute?.status).toBe('IC_RESPONDED');
    expect(reread?.supportingOutcomeId).toBe(otherSo);
    expect(reread?.supportingOutcomeBreadcrumb?.supportingOutcomeId).toBe(otherSo);
    // Still unresolved → the review count does NOT change on respond.
    expect(after.managerReview?.unresolvedDisputeCount).toBe(countBefore);
  });

  it('respond_requires_at_least_one_field: E18 with neither icResponse nor newSupportingOutcomeId → 400 (≥1 required, B.8)', () => {
    const disputeId = disputedCommitment().dispute!.id;
    const err = captureThrow(() => respondDispute(disputeId, {}));
    expect(err.status).toBe(400);
  });

  it('resolve_clears_dispute_and_decrements_count: E19 → the commitment dispute is undefined on re-read (B.6), count −1; an unreviewed plan is NOT falsely promoted to REVIEWED', () => {
    const c = disputedCommitment();
    const disputeId = c.dispute!.id;
    const before = getPlanForPersona(IC_3).managerReview;
    expect(before?.status).toBe('NOT_REVIEWED');
    expect(before?.unresolvedDisputeCount).toBe(1);

    const resolved = resolveDispute(disputeId);
    expect(resolved.status).toBe('RESOLVED');
    expect(typeof resolved.resolvedAt).toBe('string');

    const after = getPlanForPersona(IC_3);
    const reread = after.commitments.find((x) => x.id === c.id);
    expect(reread?.dispute).toBeUndefined(); // null once RESOLVED (B.6)
    expect(after.managerReview?.unresolvedDisputeCount).toBe(0);
    expect(after.managerReview?.status).toBe('NOT_REVIEWED'); // not falsely promoted
  });

  it('resolve_restores_reviewed_status: an open→resolve round-trip on a REVIEWED plan re-derives REVIEWED→REVIEWED_WITH_DISPUTES→REVIEWED (§3)', () => {
    const { plan, commitment } = undisputedReviewedCommitment();
    const created = openDispute(
      commitment.id,
      { flagType: 'MISALIGNED', managerNote: 'check this' },
      MGR_1,
    );
    expect(getPlanById(plan.id)?.managerReview?.status).toBe('REVIEWED_WITH_DISPUTES');

    resolveDispute(created.id);
    const after = getPlanById(plan.id);
    expect(after?.managerReview?.status).toBe('REVIEWED');
    expect(after?.managerReview?.unresolvedDisputeCount).toBe(0);
    expect(
      after?.commitments.find((c) => c.id === commitment.id)?.dispute,
    ).toBeUndefined();
  });

  it('affordances_track_db_state: emitDisputeAffordances reads live db state — manager sees OPEN_DISPUTE on undisputed LOCKED+ + RESOLVE on disputed; IC owner sees RESPOND on disputed; resolve flips a commitment back to OPEN_DISPUTE', () => {
    const ownerPlan = getPlanForPersona(IC_3);
    const disputed = ownerPlan.commitments.find((c) => c.dispute)!;
    // IC owner view → RESPOND_DISPUTE on the disputed commitment's dispute.
    expect(disputed.dispute?.allowedActions).toContain('RESPOND_DISPUTE');

    // Manager (non-owner) view of the same report plan.
    const mgrView = getPlanById(ownerPlan.id, MGR_1);
    const mgrDisputed = mgrView?.commitments.find((c) => c.id === disputed.id);
    expect(mgrDisputed?.dispute?.allowedActions).toContain('RESOLVE_DISPUTE');
    expect(mgrView?.allowedActions).toEqual([]); // owner-only lifecycle cleared
    const mgrUndisputed = mgrView?.commitments.find((c) => !c.dispute);
    expect(mgrUndisputed?.allowedActions).toContain('OPEN_DISPUTE');

    // After resolve, the formerly-disputed commitment is undisputed → manager now
    // sees OPEN_DISPUTE on it (affordances track the transition).
    resolveDispute(disputed.dispute!.id);
    const mgrAfter = getPlanById(ownerPlan.id, MGR_1);
    const flipped = mgrAfter?.commitments.find((c) => c.id === disputed.id);
    expect(flipped?.dispute).toBeUndefined();
    expect(flipped?.allowedActions).toContain('OPEN_DISPUTE');
  });

  it('affordances_at_ic_responded: while the dispute is IC_RESPONDED (the loop middle) the IC owner still sees RESPOND_DISPUTE and the manager still sees RESOLVE_DISPUTE (emission spans OPEN/IC_RESPONDED)', () => {
    const c = disputedCommitment();
    const disputeId = c.dispute!.id;
    respondDispute(disputeId, { icResponse: 'Re-scoped — keeping the link.' });

    // IC owner view at IC_RESPONDED → can still re-respond while unresolved.
    const ownerView = getPlanForPersona(IC_3);
    const ownerDisputed = ownerView.commitments.find((x) => x.id === c.id);
    expect(ownerDisputed?.dispute?.status).toBe('IC_RESPONDED');
    expect(ownerDisputed?.dispute?.allowedActions).toContain('RESPOND_DISPUTE');

    // Manager (non-owner) view at IC_RESPONDED → can resolve.
    const mgrView = getPlanById(ownerView.id, MGR_1);
    const mgrDisputed = mgrView?.commitments.find((x) => x.id === c.id);
    expect(mgrDisputed?.dispute?.status).toBe('IC_RESPONDED');
    expect(mgrDisputed?.dispute?.allowedActions).toContain('RESOLVE_DISPUTE');
  });

  it('draft_plan_has_no_open_dispute_affordance: a DRAFT plan is not LOCKED+ so its commitments never get OPEN_DISPUTE for a manager viewer', () => {
    // ic-1 is the DRAFT plan; read it as the manager (non-owner).
    const draftPlan = ALL_PLANS.find((p) => p.state === 'DRAFT')!;
    const mgrView = getPlanById(draftPlan.id, MGR_1);
    for (const c of mgrView?.commitments ?? []) {
      expect(c.allowedActions).not.toContain('OPEN_DISPUTE');
    }
  });

  it('command_center_reflects_db_review_state: the command-center row for a plan reflects the db review (unresolvedDisputeCount + REVIEWED_WITH_DISPUTES) after an open, while the hand-tuned styling counts stay seeded', () => {
    const { plan, commitment } = undisputedReviewedCommitment();
    const rowBefore = commandCenterPage().content.find(
      (r) => r.weeklyPlanId === plan.id,
    );
    expect(rowBefore?.reviewStatus).toBe('REVIEWED');
    expect(rowBefore?.unresolvedDisputeCount).toBe(0);
    const plannedBefore = rowBefore?.plannedCount;

    openDispute(commitment.id, { flagType: 'MISALIGNED', managerNote: 'x' }, MGR_1);

    const rowAfter = commandCenterPage().content.find(
      (r) => r.weeklyPlanId === plan.id,
    );
    expect(rowAfter?.unresolvedDisputeCount).toBe(1);
    expect(rowAfter?.reviewStatus).toBe('REVIEWED_WITH_DISPUTES');
    expect(rowAfter?.plannedCount).toBe(plannedBefore); // hand-tuned count untouched
  });

  it('db_persists_within_a_session_and_reseeds_on_reset: a write is visible to a later read of the shared demo world; resetDb re-clones the canonical seed', () => {
    const c = disputedCommitment();
    resolveDispute(c.dispute!.id);
    // Same world, read again — the resolve persisted (no per-read reset).
    expect(getPlanForPersona(IC_3).managerReview?.unresolvedDisputeCount).toBe(0);
    // A fresh seed restores the canonical OPEN dispute (count back to 1).
    resetDb();
    expect(getPlanForPersona(IC_3).managerReview?.unresolvedDisputeCount).toBe(1);
  });

  it('seed_is_deep_cloned_not_aliased: mutating the db never mutates the exported fixture seed (structuredClone isolation)', () => {
    const c = disputedCommitment();
    resolveDispute(c.dispute!.id);
    // The canonical fixture seed still carries the OPEN dispute (db is a clone).
    const seedPlan = ALL_PLANS.find((p) => p.employeeId === IC_3)!;
    expect(seedPlan.commitments.some((x) => x.dispute?.status === 'OPEN')).toBe(true);
  });

  it('getCommitment_and_findDispute_locate_by_id: the selectors resolve a commitment + dispute from the live store', () => {
    const c = disputedCommitment();
    expect(getCommitment(c.id)?.id).toBe(c.id);
    const located = findDispute(c.dispute!.id);
    expect(located?.dispute.id).toBe(c.dispute!.id);
    expect(located?.commitment.id).toBe(c.id);
  });
});

// ── Finding #1: cold-install boot ordering (await SW control before first query) ──
describe('9.15 cold-install boot — waitForServiceWorkerControl', () => {
  it('resolves_immediately_when_already_controlling: a warm SW (controller present) resolves without waiting for an event', async () => {
    const container = {
      controller: {},
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    };
    await expect(
      waitForServiceWorkerControl(container, 1000),
    ).resolves.toBe('already');
    expect(container.addEventListener).not.toHaveBeenCalled();
  });

  it('resolves_on_controllerchange: a cold SW (no controller) resolves only once it takes control of the page', async () => {
    let handler: (() => void) | undefined;
    const container = {
      controller: null as unknown,
      addEventListener: (_type: string, cb: () => void) => {
        handler = cb;
      },
      removeEventListener: vi.fn(),
    };
    const pending = waitForServiceWorkerControl(container, 1000);
    // Simulate the SW activating + claiming the page.
    container.controller = {};
    handler?.();
    await expect(pending).resolves.toBe('controlled');
  });

  it('resolves_on_timeout: if control never arrives the boot still resolves (never hangs the app forever)', async () => {
    vi.useFakeTimers();
    try {
      const container = {
        controller: null as unknown,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      };
      const pending = waitForServiceWorkerControl(container, 50);
      vi.advanceTimersByTime(51);
      await expect(pending).resolves.toBe('timeout');
    } finally {
      vi.useRealTimers();
    }
  });
});
