import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import { TAG_TYPES } from './tags';
import { getAccessToken, applyDemoAuthHeader } from './authAccessor';

/**
 * Attach exactly ONE auth header per `VITE_AUTH_MODE` (§7 XOR):
 *   auth0 ⇒ `Authorization: Bearer <jwt>` only
 *   demo  ⇒ the demo persona header only, attached by the injected applier seam
 * The two are never combined. Mode is read at call-time; the auth0 token comes
 * from the injectable accessor seam and the demo header from the injected
 * applier (standalone-only), so this shared module carries no demo-header
 * literal and the remote build carries no demo branch (REQ-I-008).
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
    // demo: the injected applier (standalone-only) attaches the persona header.
    // No-op when no applier is registered (the exposed remote) and degrades on a
    // throwing/empty applier — the backend demo-gate (DEMO_AUTH_ENABLED) then
    // rejects an unidentified demo request (403). The header-name literal and the
    // empty-persona truthy guard live in the standalone applier, not here.
    applyDemoAuthHeader(headers);
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
    // Parse RFC-7807 error bodies as JSON. fetchBaseQuery's default predicate
    // only matches `application/json`, so an `application/problem+json` (B.21)
    // body would arrive as a raw text string and never reach a slice's
    // `transformErrorResponse`→`parseProblemDetail` (it would yield the generic
    // message). Accept both so the safe error shape surfaces (safety rule #7).
    isJsonContentType: (headers) =>
      /application\/(problem\+)?json/.test(headers.get('content-type') ?? ''),
    // Late-bind the global fetch at call-time (host/remote may swap it; also
    // keeps it mockable in tests) instead of capturing it at module import.
    fetchFn: (input, init) => globalThis.fetch(input, init),
  }),
  tagTypes: TAG_TYPES,
  endpoints: () => ({}),
});
