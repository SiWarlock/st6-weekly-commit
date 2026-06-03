import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { configureStore } from '@reduxjs/toolkit';
import { waitFor } from '@testing-library/react';
import { baseApi } from '../../app/baseApi';
import { setDemoAuthHeaderApplier } from '../../app/authAccessor';
import { plansApi } from '../plan/plansApi';
import { commitmentsApi } from './commitmentsApi';
import { stripComments } from '../../test/util';
import type {
  WeeklyPlanDto,
  WeeklyCommitmentDto,
  CreateCommitmentRequest,
} from '../../shared/lib/dtos';

const here = dirname(fileURLToPath(import.meta.url));

function makeCommitment(id: string, planId: string): WeeklyCommitmentDto {
  return {
    id,
    weeklyPlanId: planId,
    commitmentKind: 'PLANNED',
    title: `Commitment ${id}`,
    priority: 'P1',
    workType: 'STRATEGIC',
    confidence: 'HIGH',
    alignmentStatus: 'NEEDS_REVIEW',
    hasUnresolvedDispute: false,
    allowedActions: ['COMMENT'],
    version: 0,
  };
}

function makePlan(id: string, commitmentCount: number): WeeklyPlanDto {
  return {
    id,
    employeeId: 'emp-1',
    employeeDisplayName: 'Ivy Chen',
    weekStartDate: '2026-06-01',
    weekEndDate: '2026-06-07',
    state: 'DRAFT',
    plannedCount: commitmentCount,
    unplannedCount: 0,
    commitments: Array.from({ length: commitmentCount }, (_, i) =>
      makeCommitment(`c-${i + 1}`, id),
    ),
    managerReview: null,
    allowedActions: ['LOCK'],
    version: 1,
  };
}

const CREATE_BODY: CreateCommitmentRequest = {
  title: 'New commitment',
  priority: 'P1',
  workType: 'STRATEGIC',
  confidence: 'HIGH',
};

function makeStore() {
  return configureStore({
    reducer: { [baseApi.reducerPath]: baseApi.reducer },
    middleware: (gdm) => gdm().concat(baseApi.middleware),
  });
}

function jsonResponse(
  body: unknown,
  status = 200,
  contentType = 'application/json',
): Response {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: { 'content-type': contentType },
  });
}

function selectCurrent(store: ReturnType<typeof makeStore>) {
  return plansApi.endpoints.getCurrentPlan.select()(store.getState());
}

beforeEach(() => {
  vi.stubEnv('VITE_AUTH_MODE', 'demo');
  setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'ic'));
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  setDemoAuthHeaderApplier(null);
});

describe('commitmentsApi (E5/E6/E7/E11 mutations + tag invalidation, the invalidatesTags side)', () => {
  it('create_commitment_invalidates_plans_and_refetches: a successful createCommitment invalidates the plan tag → getCurrentPlan refetches; NO optimistic write mid-flight', async () => {
    let currentCalls = 0;
    let releaseRefetch!: () => void;
    const refetchGate = new Promise<void>((r) => {
      releaseRefetch = r;
    });
    const fetchMock = vi.fn(async (input: Request) => {
      const { url, method } = input;
      if (url.includes('/api/plans/current')) {
        currentCalls += 1;
        if (currentCalls === 1) return jsonResponse(makePlan('plan-1', 1));
        await refetchGate; // hold the refetch open so we can inspect mid-flight
        return jsonResponse(makePlan('plan-1', 2));
      }
      if (method === 'POST' && /\/plans\/plan-1\/commitments$/.test(url)) {
        return jsonResponse(makeCommitment('c-2', 'plan-1'), 201);
      }
      throw new Error(`unexpected ${method} ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    const store = makeStore();

    const sub = store.dispatch(plansApi.endpoints.getCurrentPlan.initiate());
    await sub;
    expect(selectCurrent(store).data?.commitments).toHaveLength(1);

    await store.dispatch(
      commitmentsApi.endpoints.createCommitment.initiate({
        planId: 'plan-1',
        body: CREATE_BODY,
      }),
    );

    // Refetch is in-flight (gated, status='pending') — and the cache still shows
    // the OLD plan: no optimistic write injected a synthetic commitment.
    await waitFor(() => expect(selectCurrent(store).status).toBe('pending'));
    expect(selectCurrent(store).data?.commitments).toHaveLength(1);

    releaseRefetch();
    await waitFor(() =>
      expect(selectCurrent(store).data?.commitments).toHaveLength(2),
    );
    expect(currentCalls).toBe(2);
    sub.unsubscribe();
  });

  it('create_commitment_invalidates_per_id_only: invalidating plan p-1 refetches getPlanById(p-1) but NOT getPlanById(p-2) (per-id precision)', async () => {
    let p1 = 0;
    let p2 = 0;
    const fetchMock = vi.fn(async (input: Request) => {
      const { url, method } = input;
      if (url.includes('/api/plans/p-1/commitments') && method === 'POST') {
        return jsonResponse(makeCommitment('c-9', 'p-1'), 201);
      }
      if (url.includes('/api/plans/p-1')) {
        p1 += 1;
        return jsonResponse(makePlan('p-1', 1));
      }
      if (url.includes('/api/plans/p-2')) {
        p2 += 1;
        return jsonResponse(makePlan('p-2', 1));
      }
      throw new Error(`unexpected ${method} ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    const store = makeStore();

    const s1 = store.dispatch(plansApi.endpoints.getPlanById.initiate('p-1'));
    const s2 = store.dispatch(plansApi.endpoints.getPlanById.initiate('p-2'));
    await Promise.all([s1, s2]);
    expect(p1).toBe(1);
    expect(p2).toBe(1);

    await store.dispatch(
      commitmentsApi.endpoints.createCommitment.initiate({
        planId: 'p-1',
        body: CREATE_BODY,
      }),
    );

    await waitFor(() => expect(p1).toBe(2)); // p-1 refetched
    expect(p2).toBe(1); // p-2 untouched — invalidation was per-id
    s1.unsubscribe();
    s2.unsubscribe();
  });

  it('patch_immutable_baseline_surfaces_locked_baseline_edit: a 409 LOCKED_BASELINE_EDIT problem+json is parsed to {safeMessage,code}; no detail/traceId leak', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse(
          {
            safeMessage: 'This field is locked after the week is committed.',
            code: 'LOCKED_BASELINE_EDIT',
            detail: 'title is baseline-immutable on LOCKED plan plan-1',
            traceId: '00-lock-01',
          },
          409,
          'application/problem+json',
        ),
      ),
    );
    const store = makeStore();

    const result = await store.dispatch(
      commitmentsApi.endpoints.updateCommitment.initiate({
        id: 'c-1',
        planId: 'plan-1',
        patch: { title: 'new title' },
      }),
    );

    const err = (result as { error?: { safeMessage?: string; code?: string } })
      .error;
    expect(err?.code).toBe('LOCKED_BASELINE_EDIT');
    expect(err?.safeMessage).toBe(
      'This field is locked after the week is committed.',
    );
    expect(JSON.stringify(err)).not.toMatch(/baseline-immutable|lock-01/);
  });

  it('delete_in_non_draft_surfaces_safeMessage_and_does_not_invalidate: a failed deleteCommitment parses the safe message AND triggers no refetch (invalidate-on-success-only)', async () => {
    let currentCalls = 0;
    const fetchMock = vi.fn(async (input: Request) => {
      const { url, method } = input;
      if (url.includes('/api/plans/current')) {
        currentCalls += 1;
        return jsonResponse(makePlan('plan-1', 1));
      }
      if (method === 'DELETE') {
        return jsonResponse(
          {
            safeMessage: 'Only draft commitments can be removed.',
            code: 'ILLEGAL_STATE_TRANSITION',
          },
          409,
          'application/problem+json',
        );
      }
      throw new Error(`unexpected ${method} ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    const store = makeStore();

    const sub = store.dispatch(plansApi.endpoints.getCurrentPlan.initiate());
    await sub;
    expect(currentCalls).toBe(1);

    const result = await store.dispatch(
      commitmentsApi.endpoints.deleteCommitment.initiate({
        id: 'c-1',
        planId: 'plan-1',
      }),
    );
    const err = (result as { error?: { safeMessage?: string } }).error;
    expect(err?.safeMessage).toBe('Only draft commitments can be removed.');

    // A failed mutation must NOT invalidate → no refetch.
    await new Promise((r) => setTimeout(r, 20));
    expect(currentCalls).toBe(1);
    sub.unsubscribe();
  });

  it('add_unplanned_invalidates_plan: a successful addUnplannedCommitment invalidates the plan tag → getCurrentPlan refetches', async () => {
    let currentCalls = 0;
    const fetchMock = vi.fn(async (input: Request) => {
      const { url, method } = input;
      if (url.includes('/api/plans/current')) {
        currentCalls += 1;
        return jsonResponse(makePlan('plan-1', currentCalls));
      }
      if (method === 'POST' && url.includes('/unplanned-commitments')) {
        return jsonResponse(makeCommitment('u-1', 'plan-1'), 201);
      }
      throw new Error(`unexpected ${method} ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    const store = makeStore();

    const sub = store.dispatch(plansApi.endpoints.getCurrentPlan.initiate());
    await sub;
    expect(currentCalls).toBe(1);

    await store.dispatch(
      commitmentsApi.endpoints.addUnplannedCommitment.initiate({
        planId: 'plan-1',
        body: {
          title: 'Unplanned firefight',
          priority: 'P0',
          confidence: 'MEDIUM',
        },
      }),
    );

    await waitFor(() => expect(currentCalls).toBe(2));
    sub.unsubscribe();
  });

  it('mutations_never_optimistically_write: the slice defines no onQueryStarted/updateQueryData optimistic handler (§7 no-optimistic-updates)', () => {
    const code = stripComments(
      readFileSync(resolve(here, 'commitmentsApi.ts'), 'utf8'),
    );
    expect(code).not.toMatch(/onQueryStarted/);
    expect(code).not.toMatch(/updateQueryData/);
    expect(code).not.toMatch(/patchQueryData/);
  });
});

describe('carryForward (E12 commitment-level lifecycle mutation)', () => {
  it('carryForward_posts_E12_no_body_and_invalidates_source_plan: POST /api/commitments/{id}/carry-forward (no body, arg {id, planId}) returns the successor WeeklyCommitmentDto; success invalidates planTags(planId) → refetch', async () => {
    let currentCalls = 0;
    let postRequest: Request | undefined;
    const successor: WeeklyCommitmentDto = {
      ...makeCommitment('c-1-next', 'plan-2'),
      carryForwardSourceCommitmentId: 'c-1',
    };
    const fetchMock = vi.fn(async (input: Request) => {
      const { url, method } = input;
      if (url.includes('/api/plans/current')) {
        currentCalls += 1;
        return jsonResponse(makePlan('plan-1', 1));
      }
      if (method === 'POST' && /\/commitments\/c-1\/carry-forward$/.test(url)) {
        postRequest = input;
        return jsonResponse(successor, 201);
      }
      throw new Error(`unexpected ${method} ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    const store = makeStore();

    const sub = store.dispatch(plansApi.endpoints.getCurrentPlan.initiate());
    await sub;
    expect(currentCalls).toBe(1);

    const res = await store.dispatch(
      commitmentsApi.endpoints.carryForward.initiate({
        id: 'c-1',
        planId: 'plan-1',
      }),
    );

    // E12 is a no-body POST; it returns the next-week successor commitment.
    expect(postRequest?.method).toBe('POST');
    expect(postRequest?.body).toBeNull();
    expect(
      (res as { data?: WeeklyCommitmentDto }).data
        ?.carryForwardSourceCommitmentId,
    ).toBe('c-1');
    // Success invalidated the SOURCE plan tag → current-plan refetched.
    await waitFor(() => expect(currentCalls).toBe(2));
    sub.unsubscribe();
  });

  it('carryForward_error_does_not_invalidate: a failed carryForward parses safeMessage and triggers no refetch (invalidate-on-success-only, LESSONS §10)', async () => {
    let currentCalls = 0;
    const fetchMock = vi.fn(async (input: Request) => {
      const { url, method } = input;
      if (url.includes('/api/plans/current')) {
        currentCalls += 1;
        return jsonResponse(makePlan('plan-1', 1));
      }
      if (method === 'POST' && url.includes('/carry-forward')) {
        return jsonResponse(
          {
            safeMessage: 'Only reconciling commitments can be carried forward.',
            code: 'ILLEGAL_STATE_TRANSITION',
          },
          409,
          'application/problem+json',
        );
      }
      throw new Error(`unexpected ${method} ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    const store = makeStore();

    const sub = store.dispatch(plansApi.endpoints.getCurrentPlan.initiate());
    await sub;
    expect(currentCalls).toBe(1);

    const result = await store.dispatch(
      commitmentsApi.endpoints.carryForward.initiate({
        id: 'c-1',
        planId: 'plan-1',
      }),
    );
    expect(
      (result as { error?: { safeMessage?: string } }).error?.safeMessage,
    ).toBe('Only reconciling commitments can be carried forward.');

    await new Promise((r) => setTimeout(r, 20));
    expect(currentCalls).toBe(1);
    sub.unsubscribe();
  });
});
