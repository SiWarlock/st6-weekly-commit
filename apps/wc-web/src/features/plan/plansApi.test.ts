import { describe, it, expect, vi, afterEach } from 'vitest';
import { configureStore } from '@reduxjs/toolkit';
import { waitFor } from '@testing-library/react';
import { baseApi } from '../../app/baseApi';
import {
  setAccessTokenProvider,
  setDemoAuthHeaderApplier,
} from '../../app/authAccessor';
import { plansApi } from './plansApi';
import type { WeeklyPlanDto } from '../../shared/lib/dtos';

const PLAN: WeeklyPlanDto = {
  id: 'plan-1',
  employeeId: 'emp-1',
  employeeDisplayName: 'Ivy Chen',
  weekStartDate: '2026-06-01',
  weekEndDate: '2026-06-07',
  state: 'DRAFT',
  plannedCount: 1,
  unplannedCount: 0,
  commitments: [
    {
      id: 'c-1',
      weeklyPlanId: 'plan-1',
      commitmentKind: 'PLANNED',
      title: 'Ship onboarding',
      priority: 'P1',
      workType: 'STRATEGIC',
      confidence: 'HIGH',
      alignmentStatus: 'NEEDS_REVIEW',
      hasUnresolvedDispute: false,
      allowedActions: ['COMMENT'],
      version: 0,
    },
  ],
  managerReview: null,
  allowedActions: ['LOCK'],
  version: 3,
};

function makeStore() {
  return configureStore({
    reducer: { [baseApi.reducerPath]: baseApi.reducer },
    middleware: (gdm) => gdm().concat(baseApi.middleware),
  });
}

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
}

function mockJson(
  body: unknown,
  init?: { status?: number; contentType?: string },
) {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(JSON.stringify(body), {
      status: init?.status ?? 200,
      headers: { 'content-type': init?.contentType ?? 'application/json' },
    }),
  );
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  setAccessTokenProvider(null);
  setDemoAuthHeaderApplier(null);
});

describe('plansApi (E3/E4 → WeeklyPlanDto, the providesTags side)', () => {
  it('get_current_plan_populates_plan_with_commitments_and_allowedActions: getCurrentPlan resolves a WeeklyPlanDto with nested commitments[], allowedActions[], version', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'ic'));
    const fetchMock = mockJson(PLAN);
    const store = makeStore();

    const result = await store.dispatch(
      plansApi.endpoints.getCurrentPlan.initiate(),
    );

    expect(result.status).toBe('fulfilled');
    const data = result.data as WeeklyPlanDto;
    expect(data.id).toBe('plan-1');
    expect(data.commitments).toHaveLength(1);
    expect(data.commitments[0]!.title).toBe('Ship onboarding');
    expect(data.allowedActions).toContain('LOCK');
    expect(data.version).toBe(3);
    const req = fetchMock.mock.calls[0]?.[0] as Request;
    expect(req.url).toContain('/api/plans/current');
    expect(req.headers.get('X-Demo-Employee-Id')).toBe('ic');
    expect(req.headers.get('Authorization')).toBeNull();
  });

  it('get_plan_read_error_parses_problem_detail: a failed read surfaces safeMessage/code, never detail/traceId (safety rule #7)', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'ic'));
    mockJson(
      {
        safeMessage: 'Plan not found.',
        code: 'NOT_FOUND',
        detail: 'plan 9 not visible to emp-1',
        traceId: '00-xyz-01',
      },
      { status: 404, contentType: 'application/problem+json' },
    );
    const store = makeStore();

    const result = await store.dispatch(
      plansApi.endpoints.getPlanById.initiate('missing'),
    );

    expect(result.status).toBe('rejected');
    const err = result.error as { safeMessage?: string; code?: string | null };
    expect(err.safeMessage).toBe('Plan not found.');
    expect(err.code).toBe('NOT_FOUND');
    expect(JSON.stringify(err)).not.toMatch(/not visible|xyz-01/);
  });
});

describe('lockPlan (E8 lifecycle mutation → invalidate→refetch into LOCKED)', () => {
  it('lock_success_refetches_plan_into_LOCKED: a successful lockPlan invalidates the plan → getCurrentPlan refetches to state LOCKED; NO optimistic flip mid-flight', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'ic'));
    let currentCalls = 0;
    let release!: () => void;
    const gate = new Promise<void>((r) => {
      release = r;
    });
    const draft = { ...PLAN, state: 'DRAFT' as const };
    const locked = { ...PLAN, state: 'LOCKED' as const };
    const fetchMock = vi.fn(async (input: Request) => {
      const { url, method } = input;
      if (url.includes('/api/plans/current')) {
        currentCalls += 1;
        if (currentCalls === 1) return jsonResponse(draft);
        await gate; // hold the refetch open to inspect for an optimistic flip
        return jsonResponse(locked);
      }
      if (method === 'POST' && /\/plans\/plan-1\/lock$/.test(url)) {
        return jsonResponse(locked);
      }
      throw new Error(`unexpected ${method} ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    const store = makeStore();
    const select = () =>
      plansApi.endpoints.getCurrentPlan.select()(store.getState());

    const sub = store.dispatch(plansApi.endpoints.getCurrentPlan.initiate());
    await sub;
    expect(select().data?.state).toBe('DRAFT');

    await store.dispatch(plansApi.endpoints.lockPlan.initiate('plan-1'));

    // Refetch in flight (gated) — the state is NOT optimistically flipped.
    await waitFor(() => expect(select().status).toBe('pending'));
    expect(select().data?.state).toBe('DRAFT');

    release();
    await waitFor(() => expect(select().data?.state).toBe('LOCKED'));
    expect(currentCalls).toBe(2);
    sub.unsubscribe();
  });
});
