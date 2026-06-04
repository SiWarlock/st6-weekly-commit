/**
 * 9.15 — the standalone MSW *mutable* in-memory db (dev/demo only; tree-shaken
 * from the MF remote, REQ-I-008 — only `handlers.ts`/`browser.ts` reach it, and
 * the `dtos` import is type-only/erased so it adds no runtime edge).
 *
 * Seeds a deep clone of the contract-typed fixtures at module init and PERSISTS
 * across persona switches (the shared demo world — a manager's open is visible
 * when you switch to the IC, which is the whole point of the live loop). The
 * dispute-lifecycle mutations (E17 open / E18 respond / E19 resolve, §3) write
 * THROUGH the store, so the plan re-reads reflect the live dispute nest (B.6), the
 * manager-review re-derivation (REVIEWED↔REVIEWED_WITH_DISPUTES) and the per-actor
 * affordances (`emitDisputeAffordances` reads the live state). The targeted
 * command-center overlay surfaces the live review state on the roll-up row.
 *
 * Demo-only: the real lifecycle invariants are backend-enforced + tested; this is
 * a faithful double that mirrors them so the demo/QA can traverse the loop. The
 * other (non-dispute) mutations stay static-coherent in `handlers.ts` (9.15b).
 */
import {
  ALL_PLANS,
  COMMAND_CENTER_ROWS,
  IC_1,
  breadcrumb,
} from './fixtures';
import type {
  AlignmentDisputeDto,
  AllowedAction,
  ManagerCommandCenterRowDto,
  ManagerReviewDto,
  OpenDisputeRequest,
  PageEnvelope,
  RespondDisputeRequest,
  WeeklyCommitmentDto,
  WeeklyPlanDto,
} from '../../shared/lib/dtos';

/** A fixed demo timestamp (no real clock — keeps the demo + tests deterministic). */
const RESOLVED_AT = '2026-06-06T20:00:00Z';

/**
 * A demo-side lifecycle/authorization rejection. The handler maps it to an
 * RFC-7807 problem body (`status`/`safeMessage`/`code`) — only safe fields surface
 * (rule #7). Mirrors the backend's typed rejections for the demo (e.g. the §3
 * single-unresolved-dispute invariant → `409 SECOND_OPEN_DISPUTE`).
 */
export class MockDbError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly safeMessage: string,
  ) {
    super(safeMessage);
    this.name = 'MockDbError';
  }
}

interface Store {
  plans: WeeklyPlanDto[];
  byId: Map<string, WeeklyPlanDto>;
  byPersona: Map<string, WeeklyPlanDto>;
}

function seed(): Store {
  // Deep clone so mutations never alias the exported fixture seed (structuredClone
  // — the DTOs are plain JSON-shaped data).
  const plans = structuredClone(ALL_PLANS) as WeeklyPlanDto[];
  return {
    plans,
    byId: new Map(plans.map((p) => [p.id, p])),
    byPersona: new Map(plans.map((p) => [p.employeeId, p])),
  };
}

let store: Store = seed();
let disputeSeq = 0;

/** Re-seed a fresh deep clone of the canonical fixture world (test isolation). */
export function resetDb(): void {
  store = seed();
  disputeSeq = 0;
}

// ── Read selectors (read the LIVE store; mutations show through) ───────────────

function rawPlanById(planId: string): WeeklyPlanDto | undefined {
  return store.byId.get(planId);
}

function rawPlanByPersona(personaId: string): WeeklyPlanDto {
  return store.byPersona.get(personaId) ?? store.byPersona.get(IC_1)!;
}

/** A live commitment by id (across all plans) — the mutation target. */
export function getCommitment(commitmentId: string): WeeklyCommitmentDto | undefined {
  for (const plan of store.plans) {
    const found = plan.commitments.find((c) => c.id === commitmentId);
    if (found) {
      return found;
    }
  }
  return undefined;
}

export interface LocatedDispute {
  dispute: AlignmentDisputeDto;
  commitment: WeeklyCommitmentDto;
  plan: WeeklyPlanDto;
}

/** Locate the live unresolved dispute (nested in its commitment) by id (B.6). */
export function findDispute(disputeId: string): LocatedDispute | undefined {
  for (const plan of store.plans) {
    for (const commitment of plan.commitments) {
      if (commitment.dispute && commitment.dispute.id === disputeId) {
        return { dispute: commitment.dispute, commitment, plan };
      }
    }
  }
  return undefined;
}

/**
 * Inject the per-viewer dispute affordances backend 5.5b emits on E3/E4 (9.14),
 * reading the LIVE db state so the controls track the open→respond→resolve loop:
 *  - manager viewer (a report's plan): each UNDISPUTED LOCKED+ commitment gets
 *    `OPEN_DISPUTE`; each DISPUTED commitment's nested dispute gets `RESOLVE_DISPUTE`.
 *  - IC owner viewer (own plan): each DISPUTED commitment's dispute gets
 *    `RESPOND_DISPUTE`.
 * Emission spans the unresolved window (OPEN/IC_RESPONDED — the nested dispute is
 * removed once RESOLVED, B.6). Returns a snapshot copy; never mutates the store.
 */
function emitDisputeAffordances(
  plan: WeeklyPlanDto,
  viewerIsManager: boolean,
): WeeklyPlanDto {
  const lockedPlus = plan.state !== 'DRAFT';
  return {
    ...plan,
    commitments: plan.commitments.map((c) => {
      if (c.dispute) {
        const da: AllowedAction[] = viewerIsManager
          ? ['RESOLVE_DISPUTE']
          : ['RESPOND_DISPUTE'];
        return { ...c, dispute: { ...c.dispute, allowedActions: da } };
      }
      if (viewerIsManager && lockedPlus) {
        return {
          ...c,
          allowedActions: [...c.allowedActions, 'OPEN_DISPUTE' as AllowedAction],
        };
      }
      return c;
    }),
  };
}

/** The IC persona's own current plan (E3) — owner view (IC dispute respond). */
export function getPlanForPersona(personaId: string): WeeklyPlanDto {
  return emitDisputeAffordances(rawPlanByPersona(personaId), false);
}

/**
 * A plan by id (E4). The owner (IC) sees their own dispute-respond affordance; a
 * manager reading a report's plan (non-owner) sees the OPEN/RESOLVE dispute
 * affordances (9.14, backend 5.5b) with the plan-level `allowedActions` cleared
 * (owner-only lifecycle contract); the nested `managerReview` is preserved.
 */
export function getPlanById(
  planId: string,
  readerPersonaId?: string,
): WeeklyPlanDto | undefined {
  const plan = rawPlanById(planId);
  if (!plan) {
    return undefined;
  }
  if (readerPersonaId && readerPersonaId !== plan.employeeId) {
    return { ...emitDisputeAffordances(plan, true), allowedActions: [] };
  }
  return emitDisputeAffordances(plan, false);
}

// ── Manager-review re-derivation (§3) ──────────────────────────────────────────

/**
 * Re-derive a review's status from its live unresolved-dispute count. A review
 * that has been reviewed flips REVIEWED↔REVIEWED_WITH_DISPUTES as disputes
 * open/resolve; a NOT_REVIEWED review is never falsely promoted (the manager must
 * still mark it). The status is server-derived, never client-supplied (§3).
 */
function rederiveReviewStatus(review: ManagerReviewDto): void {
  const hasBeenReviewed =
    review.reviewedAt !== undefined ||
    review.status === 'REVIEWED' ||
    review.status === 'REVIEWED_WITH_DISPUTES';
  if (hasBeenReviewed) {
    review.status =
      review.unresolvedDisputeCount > 0 ? 'REVIEWED_WITH_DISPUTES' : 'REVIEWED';
  }
}

function adjustReview(planId: string, delta: number): void {
  const review = store.byId.get(planId)?.managerReview;
  if (!review) {
    return;
  }
  review.unresolvedDisputeCount = Math.max(
    0,
    review.unresolvedDisputeCount + delta,
  );
  rederiveReviewStatus(review);
}

// ── Dispute lifecycle mutations (E17/E18/E19) — write through ─────────────────

/** E17 — open a dispute on a commitment (§3 single-unresolved invariant). */
export function openDispute(
  commitmentId: string,
  body: OpenDisputeRequest,
  managerEmployeeId: string,
): AlignmentDisputeDto {
  const commitment = getCommitment(commitmentId);
  if (!commitment) {
    throw new MockDbError(404, 'NOT_FOUND', 'This resource is not available.');
  }
  if (commitment.dispute) {
    throw new MockDbError(
      409,
      'SECOND_OPEN_DISPUTE',
      'A dispute is already open on this commitment.',
    );
  }
  disputeSeq += 1;
  const dispute: AlignmentDisputeDto = {
    id: `dispute-new-${disputeSeq}`,
    commitmentId,
    managerEmployeeId,
    status: 'OPEN',
    flagType: body.flagType,
    managerNote: body.managerNote,
    allowedActions: [],
    version: 1,
  };
  commitment.dispute = dispute;
  adjustReview(commitment.weeklyPlanId, +1);
  return dispute;
}

/** E18 — IC responds (OPEN→IC_RESPONDED); ≥1 of icResponse/newSO required (B.8). */
export function respondDispute(
  disputeId: string,
  body: RespondDisputeRequest,
): AlignmentDisputeDto {
  const located = findDispute(disputeId);
  if (!located) {
    throw new MockDbError(404, 'NOT_FOUND', 'This resource is not available.');
  }
  if (body.icResponse === undefined && body.newSupportingOutcomeId === undefined) {
    throw new MockDbError(
      400,
      'INVALID_DISPUTE_RESPONSE',
      'Provide a response or a new supporting outcome.',
    );
  }
  const { dispute, commitment } = located;
  dispute.status = 'IC_RESPONDED';
  if (body.icResponse !== undefined) {
    dispute.icResponse = body.icResponse;
  }
  if (body.newSupportingOutcomeId !== undefined) {
    commitment.supportingOutcomeId = body.newSupportingOutcomeId;
    commitment.supportingOutcomeBreadcrumb = breadcrumb(body.newSupportingOutcomeId);
  }
  dispute.version += 1;
  // Still unresolved → the review's unresolved count does not change on respond.
  return dispute;
}

/**
 * E19 — manager resolves (→RESOLVED); the commitment's `dispute` clears (B.6).
 * The request's optional `resolutionNote` is discarded (an audit note, not on the
 * dispute DTO) — so this internal helper takes no body, mirroring the backend.
 */
export function resolveDispute(disputeId: string): AlignmentDisputeDto {
  const located = findDispute(disputeId);
  if (!located) {
    throw new MockDbError(404, 'NOT_FOUND', 'This resource is not available.');
  }
  const { dispute, commitment, plan } = located;
  dispute.status = 'RESOLVED';
  dispute.resolvedAt = RESOLVED_AT;
  dispute.version += 1;
  // B.6 — the commitment nests only the CURRENT unresolved dispute; null once
  // RESOLVED. `delete` (not `= undefined`) clears the optional under
  // exactOptionalPropertyTypes (§12).
  delete commitment.dispute;
  adjustReview(plan.id, -1);
  return dispute;
}

// ── Manager command center (targeted overlay) ─────────────────────────────────

/**
 * The E13 command-center page. Targeted overlay (9.15): the affected row's
 * `unresolvedDisputeCount` + `reviewStatus` derive from the LIVE db review so the
 * roll-up + the "At a glance" strip reflect the dispute loop; the other hand-tuned
 * styling-coverage counts (plannedCount, misalignedCount, …) stay as seeded (a
 * full count roll-up is 9.15b). A row whose plan has no review (DRAFT) is untouched.
 */
export function commandCenterPage(): PageEnvelope<ManagerCommandCenterRowDto> {
  const rows = COMMAND_CENTER_ROWS.map((seedRow) => {
    const review = seedRow.weeklyPlanId
      ? store.byId.get(seedRow.weeklyPlanId)?.managerReview
      : undefined;
    if (!review) {
      return seedRow;
    }
    return {
      ...seedRow,
      reviewStatus: review.status,
      unresolvedDisputeCount: review.unresolvedDisputeCount,
    };
  });
  return {
    content: rows,
    page: {
      number: 0,
      size: 25,
      totalElements: rows.length,
      totalPages: 1,
    },
    sort: [{ property: 'weekStartDate', direction: 'DESC' }],
  };
}
