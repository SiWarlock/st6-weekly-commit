/**
 * ST.7a — MSW v2 request handlers (standalone/dev only; tree-shaken from the MF
 * remote, REQ-I-008). They intercept the RTK Query endpoints and respond from
 * the contract-typed `fixtures`, routing by the active demo persona (the
 * `X-Demo-Employee-Id` header the standalone applier seam attaches — §6/§8;
 * referencing it here is fine: this module is never in the remote closure).
 *
 * Reads come from the mutable `db` (9.15) so mutations show through on re-read.
 * The dispute lifecycle (E17/E18/E19) WRITES THROUGH the db — enabling the live
 * open→respond→resolve loop. The other (non-dispute) mutations still return a
 * STATIC coherent DTO (so a stray click never errors) with empty `allowedActions`
 * per the write-response contract (the client re-reads the plan tag); persisting
 * them is the 9.15b fast-follow.
 */
import { http, HttpResponse } from 'msw';
import {
  ALL_PLANS,
  IC_1,
  RCDO_TREE,
  commentsForTarget,
  drilldownForCell,
  heatmapResponse,
  isManagerPersona,
  meForPersona,
  syncRecordsForPlan,
} from './fixtures';
import {
  MockDbError,
  commandCenterPage,
  getPlanById,
  getPlanForPersona,
  openDispute,
  resolveDispute,
  respondDispute,
} from './db';
import type {
  CommentDto,
  CreateCommentRequest,
  CreateCommitmentRequest,
  ManagerReviewDto,
  OpenDisputeRequest,
  OutlookSyncRecordDto,
  PlanState,
  RespondDisputeRequest,
  WeeklyCommitmentDto,
  WeeklyPlanDto,
} from '../../shared/lib/dtos';

const DEMO_HEADER = 'X-Demo-Employee-Id';

function persona(request: Request): string {
  return request.headers.get(DEMO_HEADER) ?? IC_1;
}

/** RFC-7807 (B.21) problem body — only `safeMessage`/`code` surface (rule #7). */
function problem(
  status: number,
  safeMessage: string,
  extra: Record<string, unknown> = {},
) {
  return HttpResponse.json(
    { status, safeMessage, ...extra },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
}

const NOT_FOUND = () => problem(404, 'This resource is not available.');

/** Map a db mutation rejection to its RFC-7807 problem body (only safe fields). */
function mapDbError(e: unknown) {
  if (e instanceof MockDbError) {
    return problem(e.status, e.safeMessage, { code: e.code });
  }
  return problem(500, 'Something went wrong. Please try again.');
}

/** A plan in a target state with empty write-response allowedActions. */
function planInState(plan: WeeklyPlanDto, state: PlanState): WeeklyPlanDto {
  return { ...plan, state, allowedActions: [] };
}

export const handlers = [
  // ── Identity / reference reads ─────────────────────────────────────────────
  http.get('*/api/me', ({ request }) =>
    HttpResponse.json(meForPersona(persona(request))),
  ),
  http.get('*/api/rcdo', () => HttpResponse.json(RCDO_TREE)),

  // ── Plan reads (E3/E4) ─────────────────────────────────────────────────────
  // `current` MUST precede `:id` (otherwise `:id` captures "current").
  http.get('*/api/plans/current', ({ request }) => {
    const me = persona(request);
    if (isManagerPersona(me)) {
      return NOT_FOUND();
    }
    return HttpResponse.json(getPlanForPersona(me));
  }),
  http.get('*/api/plans/:id', ({ request, params }) => {
    const plan = getPlanById(String(params.id), persona(request));
    return plan ? HttpResponse.json(plan) : NOT_FOUND();
  }),

  // ── Manager reads (E13/E14/E15) — manager-only (IDOR 404 otherwise) ─────────
  http.get('*/api/manager/command-center', ({ request }) => {
    return isManagerPersona(persona(request))
      ? HttpResponse.json(commandCenterPage())
      : NOT_FOUND();
  }),
  http.get('*/api/manager/heatmap/:cellId/drilldown', ({ request, params }) => {
    const me = persona(request);
    return isManagerPersona(me)
      ? HttpResponse.json(drilldownForCell(String(params.cellId)))
      : NOT_FOUND();
  }),
  http.get('*/api/manager/heatmap', ({ request }) => {
    return isManagerPersona(persona(request))
      ? HttpResponse.json(heatmapResponse())
      : NOT_FOUND();
  }),

  // ── Outlook sync (E22) + comments (E20) reads ──────────────────────────────
  http.get('*/api/outlook-sync', ({ request }) => {
    const planId = new URL(request.url).searchParams.get('planId') ?? '';
    return HttpResponse.json(syncRecordsForPlan(planId));
  }),
  http.get('*/api/comments', ({ request }) => {
    const url = new URL(request.url).searchParams;
    return HttpResponse.json(
      commentsForTarget(url.get('targetType') ?? '', url.get('targetId') ?? ''),
    );
  }),

  // ── Plan lifecycle mutations (E8/E9/E10) — static coherent transitions ──────
  http.post('*/api/plans/:id/lock', ({ params }) => {
    const plan = getPlanById(String(params.id));
    return plan ? HttpResponse.json(planInState(plan, 'LOCKED')) : NOT_FOUND();
  }),
  http.post('*/api/plans/:id/start-reconciliation', ({ params }) => {
    const plan = getPlanById(String(params.id));
    return plan
      ? HttpResponse.json(planInState(plan, 'RECONCILING'))
      : NOT_FOUND();
  }),
  http.post('*/api/plans/:id/close-reconciliation', ({ params }) => {
    const plan = getPlanById(String(params.id));
    return plan
      ? HttpResponse.json(planInState(plan, 'RECONCILED'))
      : NOT_FOUND();
  }),

  // ── Commitment mutations (E5/E6/E7/E11/E12) — empty allowedActions ──────────
  http.post('*/api/plans/:planId/commitments', async ({ request, params }) => {
    const body = (await request.json()) as CreateCommitmentRequest;
    return HttpResponse.json(
      newCommitment(String(params.planId), body, 'PLANNED'),
      { status: 201 },
    );
  }),
  http.post(
    '*/api/plans/:planId/unplanned-commitments',
    async ({ request, params }) => {
      const body = (await request.json()) as CreateCommitmentRequest;
      return HttpResponse.json(
        newCommitment(String(params.planId), body, 'UNPLANNED'),
        { status: 201 },
      );
    },
  ),
  http.patch('*/api/commitments/:id', async ({ request, params }) => {
    const patch = (await request.json()) as Partial<WeeklyCommitmentDto>;
    const existing = findCommitment(String(params.id));
    if (!existing) {
      return NOT_FOUND();
    }
    return HttpResponse.json({ ...existing, ...patch, allowedActions: [] });
  }),
  http.delete(
    '*/api/commitments/:id',
    () => new HttpResponse(null, { status: 204 }),
  ),
  http.post('*/api/commitments/:id/carry-forward', ({ params }) => {
    const source = findCommitment(String(params.id));
    if (!source) {
      return NOT_FOUND();
    }
    return HttpResponse.json(
      {
        ...source,
        id: `${source.id}-cf`,
        carryForwardSourceCommitmentId: source.id,
        reconciliationOutcome: undefined,
        allowedActions: [],
      },
      { status: 201 },
    );
  }),

  // ── Alignment disputes (E17/E18/E19) — write THROUGH the mutable db (9.15) ──
  // The live loop: manager opens → IC responds → manager resolves; the plan
  // re-reads nest/clear the dispute (B.6) + re-derive the review (§3). Rejections
  // (§3 single-unresolved, ≥1-field, not-found) surface as RFC-7807 problems.
  http.post('*/api/commitments/:id/disputes', async ({ request, params }) => {
    const body = (await request.json()) as OpenDisputeRequest;
    try {
      return HttpResponse.json(
        openDispute(String(params.id), body, persona(request)),
        { status: 201 },
      );
    } catch (e) {
      return mapDbError(e);
    }
  }),
  http.post('*/api/disputes/:id/respond', async ({ request, params }) => {
    const body = (await request.json()) as RespondDisputeRequest;
    try {
      return HttpResponse.json(respondDispute(String(params.id), body));
    } catch (e) {
      return mapDbError(e);
    }
  }),
  http.post('*/api/disputes/:id/resolve', ({ params }) => {
    // The optional `resolutionNote` body is discarded (audit-only, not on the DTO).
    try {
      return HttpResponse.json(resolveDispute(String(params.id)));
    } catch (e) {
      return mapDbError(e);
    }
  }),

  // ── Manager review (E16) ───────────────────────────────────────────────────
  http.post(
    '*/api/manager/reviews/:reviewId/mark-reviewed',
    async ({ request, params }) => {
      const body = (await request.json().catch(() => ({}))) as {
        summaryNote?: string;
      };
      const result: ManagerReviewDto = {
        id: String(params.reviewId),
        weeklyPlanId: '',
        managerEmployeeId: persona(request),
        status: 'REVIEWED',
        reviewDueAt: '2026-06-09T17:00:00Z',
        isOverdue: false,
        reviewedAt: '2026-06-06T18:30:00Z',
        ...(body.summaryNote ? { summaryNote: body.summaryNote } : {}),
        unresolvedDisputeCount: 0,
        allowedActions: [],
        version: 2,
      };
      return HttpResponse.json(result);
    },
  ),

  // ── Outlook sync retry (E23) ───────────────────────────────────────────────
  http.post('*/api/outlook-sync/:syncRecordId/retry', ({ params }) => {
    const result: OutlookSyncRecordDto = {
      id: String(params.syncRecordId),
      ownerEmployeeId: IC_1,
      relatedType: 'WEEKLY_PLAN',
      relatedId: '',
      eventKind: 'IC_PLANNING',
      status: 'RETRY_REQUESTED',
      retryCount: 2,
      allowedActions: [],
      version: 3,
    };
    return HttpResponse.json(result);
  }),

  // ── Comment create (E21) ───────────────────────────────────────────────────
  http.post('*/api/comments', async ({ request }) => {
    const body = (await request.json()) as CreateCommentRequest;
    const me = persona(request);
    const result: CommentDto = {
      id: `comment-new-${body.targetId}`,
      targetType: body.targetType,
      targetId: body.targetId,
      authorEmployeeId: me,
      authorDisplayName: meForPersona(me).displayName,
      parentCommentId: null,
      depth: 0,
      body: body.body,
      createdAt: '2026-06-06T19:00:00Z',
    };
    return HttpResponse.json(result, { status: 201 });
  }),
];

// ── Helpers ──────────────────────────────────────────────────────────────────
let mockSeq = 0;
function newCommitment(
  planId: string,
  body: CreateCommitmentRequest,
  kind: 'PLANNED' | 'UNPLANNED',
): WeeklyCommitmentDto {
  mockSeq += 1;
  return {
    id: `commit-new-${mockSeq}`,
    weeklyPlanId: planId,
    commitmentKind: kind,
    title: body.title,
    ...(body.description !== undefined
      ? { description: body.description }
      : {}),
    ...(body.supportingOutcomeId !== undefined
      ? { supportingOutcomeId: body.supportingOutcomeId }
      : {}),
    priority: body.priority,
    workType: kind === 'UNPLANNED' ? 'UNPLANNED' : body.workType,
    confidence: body.confidence,
    alignmentStatus: body.alignmentStatus ?? 'NEEDS_REVIEW',
    allowedActions: [],
    version: 1,
  };
}

function findCommitment(id: string): WeeklyCommitmentDto | undefined {
  for (const plan of ALL_PLANS) {
    const found = plan.commitments.find((c) => c.id === id);
    if (found) {
      return found;
    }
  }
  return undefined;
}
