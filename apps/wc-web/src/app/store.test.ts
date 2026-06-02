import { describe, it, expect, vi, afterEach } from 'vitest';
import { configureStore } from '@reduxjs/toolkit';
import { baseApi } from './baseApi';
import {
  setAccessTokenProvider,
  setDemoEmployeeIdProvider,
} from './authAccessor';

// A throwaway endpoint so we can drive a real request through baseApi end-to-end.
const testApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    ping: build.query<unknown, void>({ query: () => 'ping' }),
  }),
  overrideExisting: true,
});

function makeStore() {
  return configureStore({
    reducer: { [baseApi.reducerPath]: baseApi.reducer },
    middleware: (getDefault) => getDefault().concat(baseApi.middleware),
  });
}

function mockOkFetch() {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response('{}', {
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
  setDemoEmployeeIdProvider(null);
});

describe('store + baseApi integration (9.1)', () => {
  it('store_wires_baseApi_reducer_and_middleware: api slice present and a dispatched query resolves', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoEmployeeIdProvider(() => 'emp-1');
    mockOkFetch();

    const store = makeStore();
    expect(store.getState()[baseApi.reducerPath]).toBeDefined();

    const result = await store.dispatch(testApi.endpoints.ping.initiate());
    expect(result.status).toBe('fulfilled');
  });

  it('store_dispatch_sets_single_header_per_mode: exactly one auth header per VITE_AUTH_MODE (XOR end-to-end)', async () => {
    // ---- demo mode ----
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoEmployeeIdProvider(() => 'emp-123');
    const demoFetch = mockOkFetch();
    const demoStore = makeStore();
    await demoStore.dispatch(testApi.endpoints.ping.initiate());
    const demoReq = demoFetch.mock.calls[0]?.[0] as Request;
    expect(demoReq.headers.get('X-Demo-Employee-Id')).toBe('emp-123');
    expect(demoReq.headers.get('Authorization')).toBeNull();

    // ---- auth0 mode ----
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    setDemoEmployeeIdProvider(null);
    vi.stubEnv('VITE_AUTH_MODE', 'auth0');
    setAccessTokenProvider(async () => 'jwt-xyz');
    const authFetch = mockOkFetch();
    const authStore = makeStore();
    await authStore.dispatch(testApi.endpoints.ping.initiate());
    const authReq = authFetch.mock.calls[0]?.[0] as Request;
    expect(authReq.headers.get('Authorization')).toBe('Bearer jwt-xyz');
    expect(authReq.headers.get('X-Demo-Employee-Id')).toBeNull();
  });
});
