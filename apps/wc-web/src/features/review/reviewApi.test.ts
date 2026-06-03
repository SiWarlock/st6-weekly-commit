import { describe, it, expect, vi, afterEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { configureStore } from '@reduxjs/toolkit';
import { waitFor } from '@testing-library/react';
import { baseApi } from '../../app/baseApi';
import { setDemoAuthHeaderApplier } from '../../app/authAccessor';
import { managerApi } from '../manager/managerApi';
import { plansApi } from '../plan/plansApi';
import { reviewApi } from './reviewApi';
import { stripComments } from '../../test/util';
import type {
  WeeklyPlanDto,
  ManagerReviewDto,
  ManagerCommandCenterRowDto,
  PageEnvelope,
} from '../../shared/lib/dtos';

const here = dirname(fileURLToPath(import.meta.url));

const REVIEW: ManagerReviewDto = {
  id: 'rev-1',
  weeklyPlanId: 'plan-1',
  managerEmployeeId: 'mgr-1',
  status: 'REVIEWED',
  reviewDueAt: '2026-06-09T17:00:00Z',
  isOverdue: false,
  unresolvedDisputeCount: 0,
  allowedActions: [],
  version: 1,
};

function ccEnvelope(): PageEnvelope<ManagerCommandCenterRowDto> {
  return {
    content: [
      {
        managerEmployeeId: 'mgr-1',
        employeeId: 'emp-1',
        employeeDisplayName: 'Ivy Chen',
        weeklyPlanId: 'plan-1',
        weekStartDate: '2026-06-01',
        planState: 'LOCKED',
        reviewStatus: 'NOT_REVIEWED',
        isReviewOverdue: false,
        plannedCount: 1,
        unplannedCount: 0,
        misalignedCount: 0,
        needsReviewCount: 0,
        blockedCount: 0,
        carryForwardCount: 0,
        unresolvedDisputeCount: 0,
        updatedAt: '2026-06-02T10:00:00Z',
      },
    ],
    page: { number: 0, size: 25, totalElements: 1, totalPages: 1 },
    sort: [{ property: 'weekStartDate', direction: 'DESC' }],
  };
}

function plan(): WeeklyPlanDto {
  return {
    id: 'plan-1',
    employeeId: 'emp-1',
    employeeDisplayName: 'Ivy Chen',
    weekStartDate: '2026-06-01',
    weekEndDate: '2026-06-07',
    state: 'LOCKED',
    plannedCount: 1,
    unplannedCount: 0,
    commitments: [],
    managerReview: REVIEW,
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
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': contentType },
  });
}

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  setDemoAuthHeaderApplier(null);
});

describe('reviewApi.markReviewed (E16 → ManagerReviewDto)', () => {
  it('markReviewed_sends_only_summaryNote: POST /api/manager/reviews/{reviewId}/mark-reviewed carries only {summaryNote?} — never the derived REVIEWED/REVIEWED_WITH_DISPUTES status (server-derived, B.7/§3)', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'mgr'));
    let url = '';
    let sentBody: unknown;
    const fetchMock = vi.fn(async (input: Request) => {
      url = input.url;
      sentBody = await input.json();
      return jsonResponse(REVIEW);
    });
    vi.stubGlobal('fetch', fetchMock);
    const store = makeStore();

    await store.dispatch(
      reviewApi.endpoints.markReviewed.initiate({
        reviewId: 'rev-1',
        planId: 'plan-1',
        body: { summaryNote: 'Looks aligned.' },
      }),
    );

    expect(url).toContain('/api/manager/reviews/rev-1/mark-reviewed');
    expect(sentBody).toEqual({ summaryNote: 'Looks aligned.' });
    expect(sentBody).not.toHaveProperty('status');
  });

  it('markReviewed_invalidates_manager_review_plans_success_only: a successful markReviewed invalidates the manager + plans tags (getCommandCenter and getCurrentPlan refetch); the invalidation set is [...planTags(planId), "review"]', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'mgr'));
    let ccCalls = 0;
    let currentCalls = 0;
    const fetchMock = vi.fn(async (input: Request) => {
      const { url, method } = input;
      if (url.includes('/api/manager/command-center')) {
        ccCalls += 1;
        return jsonResponse(ccEnvelope());
      }
      if (url.includes('/api/plans/current')) {
        currentCalls += 1;
        return jsonResponse(plan());
      }
      if (method === 'POST' && url.includes('/mark-reviewed')) {
        return jsonResponse(REVIEW);
      }
      throw new Error(`unexpected ${method} ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    const store = makeStore();

    const ccSub = store.dispatch(
      managerApi.endpoints.getCommandCenter.initiate({
        weekStart: '2026-06-01',
      }),
    );
    const planSub = store.dispatch(
      plansApi.endpoints.getCurrentPlan.initiate(),
    );
    await Promise.all([ccSub, planSub]);
    expect(ccCalls).toBe(1);
    expect(currentCalls).toBe(1);

    await store.dispatch(
      reviewApi.endpoints.markReviewed.initiate({
        reviewId: 'rev-1',
        planId: 'plan-1',
        body: {},
      }),
    );

    await waitFor(() => expect(ccCalls).toBe(2)); // manager invalidated
    await waitFor(() => expect(currentCalls).toBe(2)); // plans invalidated
    ccSub.unsubscribe();
    planSub.unsubscribe();

    // The 'review' tag (no subscriber query yet — 9.11) is in the set structurally.
    const code = stripComments(
      readFileSync(resolve(here, 'reviewApi.ts'), 'utf8'),
    );
    expect(code).toMatch(/planTags\(planId\)/);
    expect(code).toMatch(/['"]review['"]/);
  });

  it('markReviewed_surfaces_safeMessage_and_does_not_invalidate_on_error: a 409/422 parses safeMessage verbatim AND triggers no refetch (invalidate-on-success-only, LESSONS §10)', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'mgr'));
    let ccCalls = 0;
    const fetchMock = vi.fn(async (input: Request) => {
      const { url, method } = input;
      if (url.includes('/api/manager/command-center')) {
        ccCalls += 1;
        return jsonResponse(ccEnvelope());
      }
      if (method === 'POST' && url.includes('/mark-reviewed')) {
        return jsonResponse(
          {
            safeMessage: 'This plan is not in a reviewable state.',
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

    const ccSub = store.dispatch(
      managerApi.endpoints.getCommandCenter.initiate({
        weekStart: '2026-06-01',
      }),
    );
    await ccSub;
    expect(ccCalls).toBe(1);

    const result = await store.dispatch(
      reviewApi.endpoints.markReviewed.initiate({
        reviewId: 'rev-1',
        planId: 'plan-1',
        body: {},
      }),
    );
    expect(
      (result as { error?: { safeMessage?: string } }).error?.safeMessage,
    ).toBe('This plan is not in a reviewable state.');

    await new Promise((r) => setTimeout(r, 20));
    expect(ccCalls).toBe(1); // a failed mark-reviewed invalidated nothing
    ccSub.unsubscribe();
  });
});
