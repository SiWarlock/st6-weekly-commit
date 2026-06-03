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
  HeatmapResponseDto,
  HeatmapCellDto,
  HeatmapDrilldownDto,
  WeeklyCommitmentDto,
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

function makeCell(overrides: Partial<HeatmapCellDto> = {}): HeatmapCellDto {
  return {
    cellId: 'cell-1',
    managerEmployeeId: 'mgr-1',
    employeeId: 'emp-1',
    employeeDisplayName: 'Ivy Chen',
    weekStartDate: '2026-06-01',
    definingObjectiveId: 'do-1',
    definingObjectiveTitle: 'Grow activation',
    commitmentCount: 3,
    plannedCount: 2,
    unplannedCount: 1,
    misalignedCount: 1,
    needsReviewCount: 0,
    blockedCount: 0,
    carryForwardCount: 0,
    unresolvedDisputeCount: 0,
    riskBadges: ['MISALIGNED'],
    ...overrides,
  };
}

function heatmapResponse(cells: HeatmapCellDto[]): HeatmapResponseDto {
  return { weekStart: '2026-06-01', cells };
}

function makeCommitment(id: string): WeeklyCommitmentDto {
  return {
    id,
    weeklyPlanId: 'plan-1',
    commitmentKind: 'PLANNED',
    title: `Commitment ${id}`,
    priority: 'P1',
    workType: 'STRATEGIC',
    confidence: 'HIGH',
    alignmentStatus: 'MISALIGNED',
    hasUnresolvedDispute: false,
    allowedActions: [],
    version: 0,
  };
}

function drilldown(): HeatmapDrilldownDto {
  return {
    cellId: 'cell-1',
    employeeId: 'emp-1',
    definingObjectiveId: 'do-1',
    supportingOutcomes: [
      {
        supportingOutcomeId: 'so-1',
        supportingOutcomeTitle: 'Streamline onboarding',
        commitments: {
          content: [makeCommitment('c-1')],
          page: { number: 0, size: 25, totalElements: 1, totalPages: 1 },
          sort: [{ property: 'priority', direction: 'ASC' }],
        },
      },
    ],
  };
}

describe('managerApi.getHeatmap (E14 → HeatmapResponseDto, NOT paginated)', () => {
  it('getHeatmap_passes_weekStart_and_optional_filters_not_paginated: weekStart + optional definingObjectiveId/supportingOutcomeId serialize; NO page/size/sort (E14 is not paginated); returns {weekStart, cells}', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'mgr'));
    let req: Request | undefined;
    const fetchMock = vi.fn(async (input: Request) => {
      req = input;
      return jsonResponse(heatmapResponse([makeCell()]));
    });
    vi.stubGlobal('fetch', fetchMock);
    const store = makeStore();

    const result = await store.dispatch(
      managerApi.endpoints.getHeatmap.initiate({
        weekStart: '2026-06-01',
        definingObjectiveId: 'do-1',
      }),
    );

    const url = new URL(req!.url);
    expect(url.pathname).toBe('/api/manager/heatmap');
    expect(url.searchParams.get('weekStart')).toBe('2026-06-01');
    expect(url.searchParams.get('definingObjectiveId')).toBe('do-1');
    // E14 is NOT paginated — no Pageable params.
    expect(url.searchParams.has('page')).toBe(false);
    expect(url.searchParams.has('size')).toBe(false);
    expect(url.searchParams.has('sort')).toBe(false);
    // omitted optional filter absent.
    expect(url.searchParams.has('supportingOutcomeId')).toBe(false);
    const data = (result as { data?: HeatmapResponseDto }).data!;
    expect(data.weekStart).toBe('2026-06-01');
    expect(data.cells).toHaveLength(1);
  });

  it('getHeatmap_tags_manager_and_refetches_on_invalidate: providesTags ["manager"] → a manager-tag invalidation re-runs the heatmap (NO heatmap tag — manager co-covers both projections, §9)', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'mgr'));
    let calls = 0;
    const fetchMock = vi.fn(async () => {
      calls += 1;
      return jsonResponse(heatmapResponse([makeCell()]));
    });
    vi.stubGlobal('fetch', fetchMock);
    const store = makeStore();

    const sub = store.dispatch(
      managerApi.endpoints.getHeatmap.initiate({ weekStart: '2026-06-01' }),
    );
    await sub;
    expect(calls).toBe(1);

    store.dispatch(baseApi.util.invalidateTags(['manager']));
    await waitFor(() => expect(calls).toBe(2));
    sub.unsubscribe();
  });

  it('getHeatmap_surfaces_safeMessage_on_error: a 403/404 parses to safeMessage only (IDOR-safe, §6); no detail/traceId leak', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'mgr'));
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse(
          {
            safeMessage: 'You do not have access to this heatmap.',
            code: 'NOT_FOUND',
            detail: 'actor mgr-9 not a manager',
            traceId: '00-hm-01',
          },
          404,
          'application/problem+json',
        ),
      ),
    );
    const store = makeStore();

    const result = await store.dispatch(
      managerApi.endpoints.getHeatmap.initiate({ weekStart: '2026-06-01' }),
    );
    const err = (result as { error?: { safeMessage?: string } }).error;
    expect(err?.safeMessage).toBe('You do not have access to this heatmap.');
    expect(JSON.stringify(err)).not.toMatch(/not a manager|hm-01/);
  });
});

describe('managerApi.getHeatmapDrilldown (E15 → HeatmapDrilldownDto, paginated commitments)', () => {
  it('getHeatmapDrilldown_requests_by_cellId_with_page_size: GET /api/manager/heatmap/{cellId}/drilldown?page=&size=; returns supportingOutcomes[] each with a B.20 commitments envelope', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'mgr'));
    let req: Request | undefined;
    const fetchMock = vi.fn(async (input: Request) => {
      req = input;
      return jsonResponse(drilldown());
    });
    vi.stubGlobal('fetch', fetchMock);
    const store = makeStore();

    const result = await store.dispatch(
      managerApi.endpoints.getHeatmapDrilldown.initiate({
        cellId: 'cell-1',
        page: 0,
        size: 25,
      }),
    );

    const url = new URL(req!.url);
    expect(url.pathname).toBe('/api/manager/heatmap/cell-1/drilldown');
    expect(url.searchParams.get('page')).toBe('0');
    expect(url.searchParams.get('size')).toBe('25');
    const data = (result as { data?: HeatmapDrilldownDto }).data!;
    expect(data.supportingOutcomes).toHaveLength(1);
    expect(data.supportingOutcomes[0]!.commitments.content).toHaveLength(1);
    expect(data.supportingOutcomes[0]!.commitments.page.totalPages).toBe(1);
  });

  it('getHeatmapDrilldown_404_surfaces_idor_safe_safeMessage: a 404 (cell not the manager own) parses to safeMessage only — never reveals existence (§6)', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'mgr'));
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse(
          {
            safeMessage: 'That cell is not available.',
            code: 'NOT_FOUND',
            detail: 'cell cell-9 belongs to mgr-2',
            traceId: '00-dd-01',
          },
          404,
          'application/problem+json',
        ),
      ),
    );
    const store = makeStore();

    const result = await store.dispatch(
      managerApi.endpoints.getHeatmapDrilldown.initiate({ cellId: 'cell-9' }),
    );
    const err = (result as { error?: { safeMessage?: string } }).error;
    expect(err?.safeMessage).toBe('That cell is not available.');
    expect(JSON.stringify(err)).not.toMatch(/belongs to|dd-01/);
  });
});
