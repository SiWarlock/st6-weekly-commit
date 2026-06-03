import { describe, it, expect, vi, afterEach } from 'vitest';
import { configureStore } from '@reduxjs/toolkit';
import { waitFor } from '@testing-library/react';
import { baseApi } from '../../app/baseApi';
import {
  setAccessTokenProvider,
  setDemoAuthHeaderApplier,
} from '../../app/authAccessor';
import { syncApi } from './syncApi';
import type { OutlookSyncRecordDto } from '../../shared/lib/dtos';

function makeRecord(
  overrides: Partial<OutlookSyncRecordDto> = {},
): OutlookSyncRecordDto {
  return {
    id: 'sync-1',
    ownerEmployeeId: 'emp-1',
    relatedType: 'WEEKLY_PLAN',
    relatedId: 'plan-1',
    eventKind: 'IC_PLANNING',
    status: 'SYNCED',
    retryCount: 0,
    allowedActions: [],
    version: 0,
    ...overrides,
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

describe('syncApi.getSyncRecords (E22 → OutlookSyncRecordDto[], plain array)', () => {
  it('getSyncRecords_reads_by_planId_plain_array_tags_sync: GET /api/outlook-sync?planId= returns a plain array (no envelope); providesTags ["sync"] (a sync invalidation refetches it)', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'ic'));
    let req: Request | undefined;
    let calls = 0;
    const fetchMock = vi.fn(async (input: Request) => {
      req = input;
      calls += 1;
      return jsonResponse([makeRecord(), makeRecord({ id: 'sync-2' })]);
    });
    vi.stubGlobal('fetch', fetchMock);
    const store = makeStore();

    const sub = store.dispatch(
      syncApi.endpoints.getSyncRecords.initiate('plan-1'),
    );
    const result = await sub;

    const url = new URL(req!.url);
    expect(url.pathname).toBe('/api/outlook-sync');
    expect(url.searchParams.get('planId')).toBe('plan-1');
    const data = (result as { data?: OutlookSyncRecordDto[] }).data!;
    expect(Array.isArray(data)).toBe(true);
    expect(data).toHaveLength(2);
    expect(calls).toBe(1);

    // providesTags ['sync'] → a sync-tag invalidation forces a refetch.
    store.dispatch(baseApi.util.invalidateTags(['sync']));
    await waitFor(() => expect(calls).toBe(2));
    sub.unsubscribe();
  });

  it('sync_error_surfaces_safeMessage: a 404/409 parses to safeMessage only (no detail/traceId leak, rule #7)', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'ic'));
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse(
          {
            safeMessage: 'Sync status is unavailable.',
            code: 'NOT_FOUND',
            detail: 'plan 9 not visible',
            traceId: '00-sync-01',
          },
          404,
          'application/problem+json',
        ),
      ),
    );
    const store = makeStore();

    const result = await store.dispatch(
      syncApi.endpoints.getSyncRecords.initiate('plan-9'),
    );
    const err = (result as { error?: { safeMessage?: string } }).error;
    expect(err?.safeMessage).toBe('Sync status is unavailable.');
    expect(JSON.stringify(err)).not.toMatch(/not visible|sync-01/);
  });
});

describe('syncApi.retrySync (E23 → updated OutlookSyncRecordDto)', () => {
  it('retrySync_posts_no_body_and_invalidates_sync_success_only: POST /api/outlook-sync/{id}/retry with no body; success invalidates ["sync"] → refetch; an error invalidates nothing', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'ic'));

    // Success: retry → invalidate → getSyncRecords refetches.
    let listCalls = 0;
    let postRequest: Request | undefined;
    const okFetch = vi.fn(async (input: Request) => {
      const { url, method } = input;
      if (url.includes('/api/outlook-sync') && method === 'GET') {
        listCalls += 1;
        return jsonResponse([makeRecord({ status: 'FAILED' })]);
      }
      if (method === 'POST' && /\/outlook-sync\/sync-1\/retry$/.test(url)) {
        postRequest = input;
        return jsonResponse(makeRecord({ status: 'RETRY_REQUESTED' }));
      }
      throw new Error(`unexpected ${method} ${url}`);
    });
    vi.stubGlobal('fetch', okFetch);
    const store = makeStore();

    const sub = store.dispatch(
      syncApi.endpoints.getSyncRecords.initiate('plan-1'),
    );
    await sub;
    expect(listCalls).toBe(1);

    await store.dispatch(
      syncApi.endpoints.retrySync.initiate({ syncRecordId: 'sync-1' }),
    );
    expect(postRequest?.method).toBe('POST');
    expect(postRequest?.body).toBeNull();
    await waitFor(() => expect(listCalls).toBe(2));
    sub.unsubscribe();

    // Error: a failed retry invalidates nothing.
    let listCalls2 = 0;
    const errFetch = vi.fn(async (input: Request) => {
      const { url, method } = input;
      if (url.includes('/api/outlook-sync') && method === 'GET') {
        listCalls2 += 1;
        return jsonResponse([makeRecord({ status: 'FAILED' })]);
      }
      if (method === 'POST' && url.includes('/retry')) {
        return jsonResponse(
          {
            safeMessage: 'Only failed syncs can be retried.',
            code: 'ILLEGAL_STATE_TRANSITION',
          },
          409,
          'application/problem+json',
        );
      }
      throw new Error(`unexpected ${method} ${url}`);
    });
    vi.stubGlobal('fetch', errFetch);
    const store2 = makeStore();
    const sub2 = store2.dispatch(
      syncApi.endpoints.getSyncRecords.initiate('plan-1'),
    );
    await sub2;
    expect(listCalls2).toBe(1);
    const result = await store2.dispatch(
      syncApi.endpoints.retrySync.initiate({ syncRecordId: 'sync-1' }),
    );
    expect(
      (result as { error?: { safeMessage?: string } }).error?.safeMessage,
    ).toBe('Only failed syncs can be retried.');
    await new Promise((r) => setTimeout(r, 20));
    expect(listCalls2).toBe(1);
    sub2.unsubscribe();
  });
});
