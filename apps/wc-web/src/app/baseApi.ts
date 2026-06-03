import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import { TAG_TYPES } from './tags';
import { getAccessToken, getDemoEmployeeId } from './authAccessor';

/**
 * Attach exactly ONE auth header per `VITE_AUTH_MODE` (§7 XOR):
 *   auth0 ⇒ `Authorization: Bearer <jwt>` only
 *   demo  ⇒ `X-Demo-Employee-Id: <persona>` only
 * The two are never combined. Mode is read at call-time; the credentials come
 * from the injectable accessor seam, so the remote build carries no demo branch.
 *
 * Exported standalone (ignores the RTK `api` arg it doesn't need) so the XOR is
 * directly unit-testable; still assignable to `fetchBaseQuery`'s prepareHeaders.
 */
export async function prepareHeaders(headers: Headers): Promise<Headers> {
  const mode = import.meta.env.VITE_AUTH_MODE;
  if (mode === 'auth0') {
    let token: string;
    try {
      token = await getAccessToken();
    } catch (cause) {
      // Normalize: generic message; never surface the raw cause (safety rule #7).
      throw new Error('Failed to acquire access token', { cause });
    }
    headers.set('Authorization', `Bearer ${token}`);
  } else if (mode === 'demo') {
    // demo: attach the persona header only when the seam yields one.
    const demoEmployeeId = getDemoEmployeeId();
    // Falsy (null OR an empty-string persona) → attach nothing; the backend
    // demo-gate (DEMO_AUTH_ENABLED) rejects an unidentified demo request (403).
    // Keep the truthy check (don't tighten to `!= null`) so '' also degrades.
    if (demoEmployeeId) {
      headers.set('X-Demo-Employee-Id', demoEmployeeId);
    }
  } else {
    // Both real modes are wired (9.3) — fail loud on a misconfigured build
    // rather than silently falling through to a header-less request. No raw
    // value in the message (safety rule #7). DCE-friendly: dead per single-mode build.
    throw new Error('Unsupported VITE_AUTH_MODE');
  }
  return headers;
}

/**
 * Resolve the API base URL (Appendix D.1 marks `VITE_API_BASE_URL` as required).
 * Fails fast in a production build when unset rather than silently using `'/'`;
 * dev/test fall back to `'/'` (Vitest supplies an absolute value via test.env).
 */
export function resolveApiBaseUrl(env: {
  VITE_API_BASE_URL?: string;
  PROD: boolean;
}): string {
  if (env.VITE_API_BASE_URL) {
    return env.VITE_API_BASE_URL;
  }
  if (env.PROD) {
    throw new Error('VITE_API_BASE_URL is required in production builds');
  }
  return '/';
}

/**
 * RTK Query base API. Domain slices (9.5+) extend it via `injectEndpoints`;
 * mutations invalidate the `TAG_TYPES` registered here. Per-endpoint
 * `transformErrorResponse` (9.5+) will call the RFC-7807 problem-details parser.
 */
export const baseApi = createApi({
  reducerPath: 'api',
  baseQuery: fetchBaseQuery({
    // Resolved at module-eval: a prod build with VITE_API_BASE_URL unset throws
    // on import (fail-fast/loud) rather than silently using a relative '/'.
    baseUrl: resolveApiBaseUrl(import.meta.env),
    prepareHeaders,
    // Late-bind the global fetch at call-time (host/remote may swap it; also
    // keeps it mockable in tests) instead of capturing it at module import.
    fetchFn: (input, init) => globalThis.fetch(input, init),
  }),
  tagTypes: TAG_TYPES,
  endpoints: () => ({}),
});
