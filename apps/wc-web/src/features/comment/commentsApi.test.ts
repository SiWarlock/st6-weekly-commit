import { describe, it, expect, vi, afterEach } from 'vitest';
import { configureStore } from '@reduxjs/toolkit';
import { waitFor } from '@testing-library/react';
import { baseApi } from '../../app/baseApi';
import {
  setAccessTokenProvider,
  setDemoAuthHeaderApplier,
} from '../../app/authAccessor';
import { commentsApi } from './commentsApi';
import type { CommentDto, PageEnvelope } from '../../shared/lib/dtos';

function makeComment(overrides: Partial<CommentDto> = {}): CommentDto {
  return {
    id: 'cm-1',
    targetType: 'COMMITMENT',
    targetId: 'c-1',
    authorEmployeeId: 'emp-1',
    authorDisplayName: 'Ivy Chen',
    parentCommentId: null,
    depth: 0,
    body: 'Looks good to me.',
    createdAt: '2026-06-02T10:00:00Z',
    ...overrides,
  };
}

function envelope(comments: CommentDto[]): PageEnvelope<CommentDto> {
  return {
    content: comments,
    page: {
      number: 0,
      size: 25,
      totalElements: comments.length,
      totalPages: 1,
    },
    sort: [{ property: 'createdAt', direction: 'ASC' }],
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

describe('commentsApi (E20/E21 — flat comments, B.9 + B.20 envelope, F.5 sort)', () => {
  it('getComments_reads_by_target_paginated_default_sort: GET /api/comments serializes targetType+targetId+page+size; default sort createdAt,asc applied when the client sends none (F.5); the response parses the B.20 {content,page,sort} envelope', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'emp-1'));
    let req: Request | undefined;
    const fetchMock = vi.fn(async (input: Request) => {
      req = input;
      return jsonResponse(envelope([makeComment()]));
    });
    vi.stubGlobal('fetch', fetchMock);
    const store = makeStore();

    const result = await store.dispatch(
      commentsApi.endpoints.getComments.initiate({
        targetType: 'COMMITMENT',
        targetId: 'c-1',
      }),
    );

    const url = new URL(req!.url);
    expect(url.pathname).toBe('/api/comments');
    expect(url.searchParams.get('targetType')).toBe('COMMITMENT');
    expect(url.searchParams.get('targetId')).toBe('c-1');
    expect(url.searchParams.get('page')).toBe('0');
    expect(url.searchParams.get('size')).toBe('25');
    // F.5: chronological thread — default sort createdAt ASC when unspecified.
    expect(url.searchParams.getAll('sort')).toEqual(['createdAt,asc']);
    // B.20 envelope parses through to the typed shape.
    expect(result.data?.content[0]?.body).toBe('Looks good to me.');
    expect(result.data?.page.size).toBe(25);
  });

  it('createComment_posts_body_and_invalidates_target_success_only: POST /api/comments with {targetType,targetId,body}; a success invalidates ONLY the posted target tag → that target refetches while a DIFFERENT target is untouched (per-target precision, Q2)', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'emp-1'));
    let t1 = 0;
    let t2 = 0;
    let postBody: unknown;
    const fetchMock = vi.fn(async (input: Request) => {
      const { url, method } = input;
      if (method === 'POST') {
        postBody = await input.clone().json();
        return jsonResponse(makeComment({ targetId: 'c-1' }), 201);
      }
      const u = new URL(url);
      const id = u.searchParams.get('targetId');
      if (id === 'c-1') {
        t1 += 1;
        return jsonResponse(envelope([makeComment({ targetId: 'c-1' })]));
      }
      t2 += 1;
      return jsonResponse(envelope([makeComment({ targetId: 'c-2' })]));
    });
    vi.stubGlobal('fetch', fetchMock);
    const store = makeStore();

    const s1 = store.dispatch(
      commentsApi.endpoints.getComments.initiate({
        targetType: 'COMMITMENT',
        targetId: 'c-1',
      }),
    );
    const s2 = store.dispatch(
      commentsApi.endpoints.getComments.initiate({
        targetType: 'COMMITMENT',
        targetId: 'c-2',
      }),
    );
    await Promise.all([s1, s2]);
    expect(t1).toBe(1);
    expect(t2).toBe(1);

    await store.dispatch(
      commentsApi.endpoints.createComment.initiate({
        targetType: 'COMMITMENT',
        targetId: 'c-1',
        body: 'Nice work',
      }),
    );

    // POST carries exactly the E21 request shape.
    expect(postBody).toEqual({
      targetType: 'COMMITMENT',
      targetId: 'c-1',
      body: 'Nice work',
    });
    // Per-target invalidation: c-1 refetches, c-2 is left alone.
    await waitFor(() => expect(t1).toBe(2));
    expect(t2).toBe(1);
    s1.unsubscribe();
    s2.unsubscribe();
  });

  it('comments_error_surfaces_safeMessage_and_no_invalidate: a 404 (unseeable target, IDOR-safe) on createComment parses to {safeMessage,code} only — no detail/traceId leak (§6/§16) — AND a failed post invalidates nothing (no refetch, LESSONS §10)', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoAuthHeaderApplier((h) => h.set('X-Demo-Employee-Id', 'emp-1'));
    let listCalls = 0;
    const fetchMock = vi.fn(async (input: Request) => {
      const { method } = input;
      if (method === 'POST') {
        return jsonResponse(
          {
            safeMessage: 'That item is not available.',
            code: 'NOT_FOUND',
            detail: 'target commitment c-x not visible to emp-1',
            traceId: '00-cmt-404',
          },
          404,
          'application/problem+json',
        );
      }
      listCalls += 1;
      return jsonResponse(envelope([makeComment({ targetId: 'c-1' })]));
    });
    vi.stubGlobal('fetch', fetchMock);
    const store = makeStore();

    const sub = store.dispatch(
      commentsApi.endpoints.getComments.initiate({
        targetType: 'COMMITMENT',
        targetId: 'c-1',
      }),
    );
    await sub;
    expect(listCalls).toBe(1);

    const result = await store.dispatch(
      commentsApi.endpoints.createComment.initiate({
        targetType: 'COMMITMENT',
        targetId: 'c-1',
        body: 'hi',
      }),
    );

    const err = (result as { error?: { safeMessage?: string; code?: string } })
      .error;
    expect(err?.safeMessage).toBe('That item is not available.');
    // Rule #7 / §16 — internal fields never surface through the parser.
    expect(JSON.stringify(err)).not.toMatch(
      /not visible|cmt-404|target commitment/,
    );

    // Invalidate-on-success-only: a FAILED post must not refetch the list.
    await new Promise((r) => setTimeout(r, 20));
    expect(listCalls).toBe(1);
    sub.unsubscribe();
  });
});
