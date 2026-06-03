import { describe, it, expect, vi, afterEach } from 'vitest';
import { configureStore } from '@reduxjs/toolkit';
import { Provider } from 'react-redux';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { baseApi } from '../../app/baseApi';
import {
  setAccessTokenProvider,
  setDemoAuthHeaderApplier,
} from '../../app/authAccessor';
import { useCurrentUser } from './useCurrentUser';
import type { MeDto } from './meApi';

const IC_ME: MeDto = {
  employeeId: '22222222-2222-2222-2222-222222222222',
  email: 'ivy@example.com',
  displayName: 'Ivy Chen',
  role: 'IC',
  persona: 'demo-employee-ic-1',
  isManager: false,
};

const MANAGER_ME: MeDto = {
  ...IC_ME,
  role: 'MANAGER',
  displayName: 'Morgan Lee',
  persona: 'demo-employee-mgr-1',
  isManager: true,
};

function makeStore() {
  return configureStore({
    reducer: { [baseApi.reducerPath]: baseApi.reducer },
    middleware: (gdm) => gdm().concat(baseApi.middleware),
  });
}

function wrapperWith(store: ReturnType<typeof makeStore>) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <Provider store={store}>{children}</Provider>;
  };
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

describe('useCurrentUser (route-gating + persona-aware default seam)', () => {
  it('use_current_user_surfaces_role_isManager: exposes role/isManager/persona once getMe resolves; isManager defaults false while loading', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'mgr'));
    mockJson(MANAGER_ME);
    const store = makeStore();

    const { result } = renderHook(() => useCurrentUser(), {
      wrapper: wrapperWith(store),
    });

    // Fail-closed during the initial load: isManager false until confirmed.
    expect(result.current.isLoading).toBe(true);
    expect(result.current.isManager).toBe(false);

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.role).toBe('MANAGER');
    expect(result.current.isManager).toBe(true);
    expect(result.current.persona).toBe('demo-employee-mgr-1');
    expect(result.current.isError).toBe(false);
  });

  it('use_current_user_ic_is_not_manager: an IC identity surfaces isManager=false after load', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'ic'));
    mockJson(IC_ME);
    const store = makeStore();

    const { result } = renderHook(() => useCurrentUser(), {
      wrapper: wrapperWith(store),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.role).toBe('IC');
    expect(result.current.isManager).toBe(false);
  });
});
