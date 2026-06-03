import { describe, it, expect, vi, afterEach } from 'vitest';
import { configureStore } from '@reduxjs/toolkit';
import { baseApi } from '../../app/baseApi';
import {
  setAccessTokenProvider,
  setDemoAuthHeaderApplier,
} from '../../app/authAccessor';
import { meApi, type MeDto } from './meApi';

const MANAGER_ME: MeDto = {
  employeeId: '11111111-1111-1111-1111-111111111111',
  email: 'morgan@example.com',
  displayName: 'Morgan Lee',
  role: 'MANAGER',
  persona: 'demo-employee-mgr-1',
  isManager: true,
  timezone: 'America/New_York',
};

function makeStore() {
  return configureStore({
    reducer: { [baseApi.reducerPath]: baseApi.reducer },
    middleware: (gdm) => gdm().concat(baseApi.middleware),
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

describe('meApi (GET /api/me → MeDto, E1)', () => {
  it('me_query_returns_medto_shape: getMe resolves the MeDto contract through the store and rides prepareHeaders (single auth header)', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) =>
      h.set('X-Demo-Employee-Id', 'demo-employee-mgr-1'),
    );
    const fetchMock = mockJson(MANAGER_ME);
    const store = makeStore();

    const result = await store.dispatch(meApi.endpoints.getMe.initiate());

    expect(result.status).toBe('fulfilled');
    expect(result.data).toEqual(MANAGER_ME);
    // The meApi request rides the shared prepareHeaders → the demo header is on it.
    const req = fetchMock.mock.calls[0]?.[0] as Request;
    expect(req.url).toContain('/api/me');
    expect(req.headers.get('X-Demo-Employee-Id')).toBe('demo-employee-mgr-1');
    expect(req.headers.get('Authorization')).toBeNull();
  });

  it('me_error_parses_problem_detail: a 401 application/problem+json surfaces safeMessage/code via parseProblemDetail and never leaks detail/traceId (safety rule #7)', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'x'));
    mockJson(
      {
        safeMessage: 'You are not signed in.',
        code: 'UNAUTHENTICATED',
        detail: 'jwt expired at 2026-06-03T00:00:00Z',
        traceId: '00-abc-trace-01',
      },
      { status: 401, contentType: 'application/problem+json' },
    );
    const store = makeStore();

    const result = await store.dispatch(meApi.endpoints.getMe.initiate());

    expect(result.status).toBe('rejected');
    const err = result.error as { safeMessage?: string; code?: string | null };
    expect(err.safeMessage).toBe('You are not signed in.');
    expect(err.code).toBe('UNAUTHENTICATED');
    // The transformed error carries ONLY the safe shape — no internal leakage.
    expect(JSON.stringify(err)).not.toMatch(/jwt expired|abc-trace/);
  });
});
