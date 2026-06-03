import { describe, it, expect, vi, afterEach } from 'vitest';
import { configureStore } from '@reduxjs/toolkit';
import { waitFor } from '@testing-library/react';
import { baseApi } from '../../app/baseApi';
import {
  setAccessTokenProvider,
  setDemoAuthHeaderApplier,
} from '../../app/authAccessor';
import { managerApi } from './managerApi';
import type {
  ManagerCommandCenterRowDto,
  PageEnvelope,
} from '../../shared/lib/dtos';

function makeRow(
  overrides: Partial<ManagerCommandCenterRowDto> = {},
): ManagerCommandCenterRowDto {
  return {
    managerEmployeeId: 'mgr-1',
    employeeId: 'emp-1',
    employeeDisplayName: 'Ivy Chen',
    weeklyPlanId: 'plan-1',
    weekStartDate: '2026-06-01',
    planState: 'LOCKED',
    reviewStatus: 'NOT_REVIEWED',
    reviewDueAt: '2026-06-09T17:00:00Z',
    isReviewOverdue: false,
    plannedCount: 3,
    unplannedCount: 1,
    misalignedCount: 0,
    needsReviewCount: 1,
    blockedCount: 0,
    carryForwardCount: 0,
    unresolvedDisputeCount: 0,
    updatedAt: '2026-06-02T10:00:00Z',
    ...overrides,
  };
}

function envelope(
  rows: ManagerCommandCenterRowDto[],
): PageEnvelope<ManagerCommandCenterRowDto> {
  return {
    content: rows,
    page: { number: 0, size: 25, totalElements: rows.length, totalPages: 1 },
    sort: [{ property: 'weekStartDate', direction: 'DESC' }],
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
  setAccessTokenProvider(null);
  setDemoAuthHeaderApplier(null);
});

describe('managerApi.getCommandCenter (E13 → B.20 envelope of B.11 rows)', () => {
  it('getCommandCenter_requires_weekStart_and_passes_filters: weekStart + filters + page/size serialize into the query string; default sort weekStartDate,desc + employeeDisplayName,asc applied when unspecified (F.5); undefined filters omitted', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'mgr'));
    let req: Request | undefined;
    const fetchMock = vi.fn(async (input: Request) => {
      req = input;
      return jsonResponse(envelope([makeRow()]));
    });
    vi.stubGlobal('fetch', fetchMock);
    const store = makeStore();

    await store.dispatch(
      managerApi.endpoints.getCommandCenter.initiate({
        weekStart: '2026-06-01',
        reviewState: 'OVERDUE',
        priority: 'P0',
      }),
    );

    const url = new URL(req!.url);
    expect(url.pathname).toBe('/api/manager/command-center');
    expect(url.searchParams.get('weekStart')).toBe('2026-06-01');
    expect(url.searchParams.get('page')).toBe('0');
    expect(url.searchParams.get('size')).toBe('25');
    // Default sort applied verbatim when the client sends none (F.5).
    expect(url.searchParams.getAll('sort')).toEqual([
      'weekStartDate,desc',
      'employeeDisplayName,asc',
    ]);
    // reviewState=OVERDUE passes through verbatim (server derives; client never).
    expect(url.searchParams.get('reviewState')).toBe('OVERDUE');
    expect(url.searchParams.get('priority')).toBe('P0');
    // Undefined filters are omitted (clean cache key).
    expect(url.searchParams.has('employeeId')).toBe(false);
    expect(url.searchParams.has('workType')).toBe(false);
  });

  it('getCommandCenter_returns_B20_envelope_and_tags_manager: the response parses the B.20 {content,page,sort} shape; the query provides the manager tag (a manager-tag invalidation refetches it)', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'mgr'));
    let calls = 0;
    const fetchMock = vi.fn(async () => {
      calls += 1;
      return jsonResponse(
        envelope([makeRow(), makeRow({ employeeId: 'emp-2' })]),
      );
    });
    vi.stubGlobal('fetch', fetchMock);
    const store = makeStore();
    const select = () =>
      managerApi.endpoints.getCommandCenter.select({ weekStart: '2026-06-01' })(
        store.getState(),
      );

    const sub = store.dispatch(
      managerApi.endpoints.getCommandCenter.initiate({
        weekStart: '2026-06-01',
      }),
    );
    await sub;

    const data = select().data as PageEnvelope<ManagerCommandCenterRowDto>;
    expect(data.content).toHaveLength(2);
    expect(data.page.totalElements).toBe(2);
    expect(data.sort[0]!.property).toBe('weekStartDate');
    expect(calls).toBe(1);

    // providesTags ['manager'] → a manager-tag invalidation forces a refetch.
    store.dispatch(baseApi.util.invalidateTags(['manager']));
    await waitFor(() => expect(calls).toBe(2));
    sub.unsubscribe();
  });

  it('getCommandCenter_surfaces_safeMessage_on_error: a 403/404 parses to safeMessage only (IDOR-safe, never reveals existence — §6); no detail/traceId leak', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'mgr'));
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse(
          {
            safeMessage: 'You do not have access to this view.',
            code: 'NOT_FOUND',
            detail: 'actor mgr-9 is not a manager',
            traceId: '00-cc-01',
          },
          404,
          'application/problem+json',
        ),
      ),
    );
    const store = makeStore();

    const result = await store.dispatch(
      managerApi.endpoints.getCommandCenter.initiate({
        weekStart: '2026-06-01',
      }),
    );

    const err = (result as { error?: { safeMessage?: string; code?: string } })
      .error;
    expect(err?.safeMessage).toBe('You do not have access to this view.');
    expect(JSON.stringify(err)).not.toMatch(/not a manager|cc-01/);
  });
});
