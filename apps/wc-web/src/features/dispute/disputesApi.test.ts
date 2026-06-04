import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { configureStore } from '@reduxjs/toolkit';
import { waitFor } from '@testing-library/react';
import { baseApi } from '../../app/baseApi';
import { setDemoAuthHeaderApplier } from '../../app/authAccessor';
import { plansApi } from '../plan/plansApi';
import { disputesApi } from './disputesApi';
import { stripComments } from '../../test/util';
import type { WeeklyPlanDto, WeeklyCommitmentDto } from '../../shared/lib/dtos';

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
    allowedActions: ['OPEN_DISPUTE'],
    version: 0,
  };
}

function makePlan(id: string): WeeklyPlanDto {
  return {
    id,
    employeeId: 'emp-1',
    employeeDisplayName: 'Ivy Chen',
    weekStartDate: '2026-06-01',
    weekEndDate: '2026-06-07',
    state: 'LOCKED',
    plannedCount: 1,
    unplannedCount: 0,
    commitments: [makeCommitment('c-1', id)],
    managerReview: null,
    allowedActions: [],
    version: 1,
  };
}

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
  setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'mgr'));
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  setDemoAuthHeaderApplier(null);
});

describe('disputesApi (E17/E18/E19 — open/respond/resolve + success-only invalidation)', () => {
  it('open_respond_resolve_post_correct_path_and_body: each mutation POSTs its E17/E18/E19 path with the request body', async () => {
    const calls: { url: string; method: string; body: string }[] = [];
    const fetchMock = vi.fn(async (input: Request) => {
      calls.push({
        url: input.url,
        method: input.method,
        body: await input.clone().text(),
      });
      return jsonResponse({}, 201);
    });
    vi.stubGlobal('fetch', fetchMock);
    const store = makeStore();

    await store.dispatch(
      disputesApi.endpoints.openDispute.initiate({
        commitmentId: 'c-1',
        planId: 'plan-1',
        body: { flagType: 'MISALIGNED', managerNote: 'Off-strategy.' },
      }),
    );
    await store.dispatch(
      disputesApi.endpoints.respondDispute.initiate({
        disputeId: 'd-1',
        planId: 'plan-1',
        body: { icResponse: 'Re-scoped.' },
      }),
    );
    await store.dispatch(
      disputesApi.endpoints.resolveDispute.initiate({
        disputeId: 'd-1',
        planId: 'plan-1',
        body: { resolutionNote: 'Agreed.' },
      }),
    );

    const open = calls.find(
      (c) => c.method === 'POST' && /\/commitments\/c-1\/disputes$/.test(c.url),
    );
    expect(open).toBeTruthy();
    expect(JSON.parse(open!.body)).toEqual({
      flagType: 'MISALIGNED',
      managerNote: 'Off-strategy.',
    });

    const respond = calls.find((c) => /\/disputes\/d-1\/respond$/.test(c.url));
    expect(respond).toBeTruthy();
    expect(JSON.parse(respond!.body)).toEqual({ icResponse: 'Re-scoped.' });

    const resolve = calls.find((c) => /\/disputes\/d-1\/resolve$/.test(c.url));
    expect(resolve).toBeTruthy();
    expect(JSON.parse(resolve!.body)).toEqual({ resolutionNote: 'Agreed.' });
  });

  // Open-success is the most behaviorally-critical path (the dispute must APPEAR
  // + OPEN_DISPUTE must vanish on refetch); parametrize the success-invalidation
  // assertion across ALL THREE mutations so none silently skips invalidatesTags.
  // Each invalidates the parent plan (CURRENT + planId) + Manager (+ Heatmap, which
  // rides the `manager` tag — no separate heatmap tag, §9) → getCurrentPlan refetches.
  const successCases = [
    {
      name: 'openDispute',
      run: (store: ReturnType<typeof makeStore>) =>
        store.dispatch(
          disputesApi.endpoints.openDispute.initiate({
            commitmentId: 'c-1',
            planId: 'plan-1',
            body: { flagType: 'MISALIGNED', managerNote: 'x' },
          }),
        ),
      matcher: (url: string) => /\/commitments\/c-1\/disputes$/.test(url),
    },
    {
      name: 'respondDispute',
      run: (store: ReturnType<typeof makeStore>) =>
        store.dispatch(
          disputesApi.endpoints.respondDispute.initiate({
            disputeId: 'd-1',
            planId: 'plan-1',
            body: { icResponse: 'x' },
          }),
        ),
      matcher: (url: string) => /\/disputes\/d-1\/respond$/.test(url),
    },
    {
      name: 'resolveDispute',
      run: (store: ReturnType<typeof makeStore>) =>
        store.dispatch(
          disputesApi.endpoints.resolveDispute.initiate({
            disputeId: 'd-1',
            planId: 'plan-1',
            body: {},
          }),
        ),
      matcher: (url: string) => /\/disputes\/d-1\/resolve$/.test(url),
    },
  ];

  it.each(successCases)(
    'each_mutation_invalidates_plan_on_success: $name success invalidates the parent plan → getCurrentPlan refetches (§10; Plans+Manager+Heatmap)',
    async ({ run, matcher }) => {
      let currentCalls = 0;
      const fetchMock = vi.fn(async (input: Request) => {
        const { url, method } = input;
        if (url.includes('/api/plans/current')) {
          currentCalls += 1;
          return jsonResponse(makePlan('plan-1'));
        }
        if (method === 'POST' && matcher(url)) {
          return jsonResponse({}, 201);
        }
        throw new Error(`unexpected ${method} ${url}`);
      });
      vi.stubGlobal('fetch', fetchMock);
      const store = makeStore();

      const sub = store.dispatch(plansApi.endpoints.getCurrentPlan.initiate());
      await sub;
      expect(currentCalls).toBe(1);

      await run(store);

      await waitFor(() => expect(currentCalls).toBe(2)); // plan refetched
      expect(selectCurrent(store).data).toBeTruthy();
      sub.unsubscribe();
    },
  );

  it('failed_mutation_invalidates_nothing: a 409 openDispute does NOT invalidate (success-only guard, §10) — getCurrentPlan is not refetched', async () => {
    let currentCalls = 0;
    const fetchMock = vi.fn(async (input: Request) => {
      const { url, method } = input;
      if (url.includes('/api/plans/current')) {
        currentCalls += 1;
        return jsonResponse(makePlan('plan-1'));
      }
      if (method === 'POST' && /\/commitments\/c-1\/disputes$/.test(url)) {
        return jsonResponse(
          {
            safeMessage: 'A dispute is already open.',
            code: 'SECOND_OPEN_DISPUTE',
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
      disputesApi.endpoints.openDispute.initiate({
        commitmentId: 'c-1',
        planId: 'plan-1',
        body: { flagType: 'MISALIGNED', managerNote: 'x' },
      }),
    );
    expect('error' in result).toBe(true);

    // No invalidation on error → the plan is NOT refetched.
    await new Promise((r) => setTimeout(r, 20));
    expect(currentCalls).toBe(1);
    sub.unsubscribe();
  });

  it('transform_error_surfaces_safe_message_only: a 409 problem+json is parsed to {safeMessage, code} — no detail/traceId leak (§16)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse(
          {
            safeMessage: 'A dispute is already open on this commitment.',
            code: 'SECOND_OPEN_DISPUTE',
            detail: 'commitment c-1 already has an OPEN dispute d-0',
            traceId: '00-dispute-01',
          },
          409,
          'application/problem+json',
        ),
      ),
    );
    const store = makeStore();

    const result = await store.dispatch(
      disputesApi.endpoints.openDispute.initiate({
        commitmentId: 'c-1',
        planId: 'plan-1',
        body: { flagType: 'MISALIGNED', managerNote: 'x' },
      }),
    );
    const error = (result as { error?: unknown }).error as {
      safeMessage: string;
      code: string | null;
    } & Record<string, unknown>;
    expect(error.safeMessage).toBe(
      'A dispute is already open on this commitment.',
    );
    expect(error.code).toBe('SECOND_OPEN_DISPUTE');
    expect(error).not.toHaveProperty('detail');
    expect(error).not.toHaveProperty('traceId');
  });

  it('no_optimistic_update: the slice defines no updateQueryData (no manual cache patch — invalidate→refetch only, §10)', () => {
    const src = stripComments(
      readFileSync(resolve(here, 'disputesApi.ts'), 'utf8'),
    );
    expect(src).not.toMatch(/updateQueryData/);
  });
});
