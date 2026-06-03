import { describe, it, expect, vi, afterEach } from 'vitest';
import { configureStore } from '@reduxjs/toolkit';
import { baseApi } from '../../app/baseApi';
import {
  setAccessTokenProvider,
  setDemoAuthHeaderApplier,
} from '../../app/authAccessor';
import * as rcdoModule from './rcdoApi';
import { rcdoApi, type RcdoTreeDto } from './rcdoApi';

// Fixture: 1 Rally Cry / 3 Defining Objectives / 9 Supporting Outcomes (§13 shape).
const TREE: RcdoTreeDto = {
  rallyCries: [
    {
      id: 'rc-1',
      title: 'Win the quarter',
      active: true,
      definingObjectives: Array.from({ length: 3 }, (_, d) => ({
        id: `do-${d + 1}`,
        rallyCryId: 'rc-1',
        title: `Objective ${d + 1}`,
        active: true,
        supportingOutcomes: Array.from({ length: 3 }, (_, s) => ({
          id: `so-${d + 1}-${s + 1}`,
          definingObjectiveId: `do-${d + 1}`,
          title: `Outcome ${d + 1}.${s + 1}`,
          active: true,
        })),
      })),
    },
  ],
};

function makeStore() {
  return configureStore({
    reducer: { [baseApi.reducerPath]: baseApi.reducer },
    middleware: (gdm) => gdm().concat(baseApi.middleware),
  });
}

function mockJson(body: unknown) {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(JSON.stringify(body), {
      status: 200,
      headers: { 'content-type': 'application/json' },
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

describe('rcdoApi (GET /api/rcdo → RcdoTreeDto, E2)', () => {
  it('rcdo_query_returns_object_wrapper: getRcdo resolves { rallyCries: [...] } (object wrapper, not a bare array) with 1 RC / 3 DO / 9 SO typed', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'ic'));
    const fetchMock = mockJson(TREE);
    const store = makeStore();

    const result = await store.dispatch(rcdoApi.endpoints.getRcdo.initiate());

    expect(result.status).toBe('fulfilled');
    const data = result.data as RcdoTreeDto;
    // Object wrapper, not a bare array (B.4 / §5 envelope convention).
    expect(Array.isArray(data)).toBe(false);
    expect(data.rallyCries).toHaveLength(1);
    const dos = data.rallyCries[0]!.definingObjectives;
    expect(dos).toHaveLength(3);
    const soCount = dos.reduce((n, d) => n + d.supportingOutcomes.length, 0);
    expect(soCount).toBe(9);
    // Typed nesting: an SO carries its parent DO id.
    expect(dos[0]!.supportingOutcomes[0]!.definingObjectiveId).toBe('do-1');
    const req = fetchMock.mock.calls[0]?.[0] as Request;
    expect(req.url).toContain('/api/rcdo');
  });

  it('rcdo_tag_never_invalidated: the rcdo slice exposes a read query only (no mutation), so seeded RCDO data is never invalidated (REQ-D-003)', () => {
    // Enforceable half of "never invalidated": the slice defines NO mutation —
    // so nothing can list RCDO in invalidatesTags from this slice. (Convention:
    // no later slice's mutation may invalidate 'rcdo' either.)
    const exportNames = Object.keys(rcdoModule);
    expect(exportNames).toContain('useGetRcdoQuery');
    expect(exportNames.some((n) => /Mutation/.test(n))).toBe(false);
  });
});
