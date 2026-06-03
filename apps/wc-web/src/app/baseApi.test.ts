import { describe, it, expect, vi, afterEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { prepareHeaders, baseApi, resolveApiBaseUrl } from './baseApi';
import { TAG_TYPES } from './tags';
import {
  setAccessTokenProvider,
  setDemoAuthHeaderApplier,
} from './authAccessor';
import { stripComments } from '../test/util';

const here = dirname(fileURLToPath(import.meta.url));

afterEach(() => {
  vi.unstubAllEnvs();
  setAccessTokenProvider(null);
  setDemoAuthHeaderApplier(null);
});

describe('REQ-I-008 — baseApi source carries no demo-header literal (split out to standalone)', () => {
  it('baseapi_source_has_no_demo_header_literal: the demo-header name lives only in src/standalone/, never in the shared baseApi', () => {
    const code = stripComments(
      readFileSync(resolve(here, 'baseApi.ts'), 'utf8'),
    );
    // The X-Demo-Employee-Id literal (and any direct headers.set of it) must be
    // gone from the shared, remote-reachable baseApi — it is attached only via
    // the injected applier seam, whose closure lives in src/standalone/.
    expect(code).not.toMatch(/X-Demo-Employee-Id/);
    expect(code).not.toMatch(/headers\.set\(\s*['"]X-Demo/i);
  });
});

describe('prepareHeaders auth XOR (§7) — via the injected demo-applier seam', () => {
  it('demo_mode_attaches_demo_header_via_injected_applier: VITE_AUTH_MODE=demo runs the injected applier (sets X-Demo-Employee-Id), never Authorization', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    // The applier owns the header literal; baseApi only dispatches to it.
    setDemoAuthHeaderApplier((headers) =>
      headers.set('X-Demo-Employee-Id', 'emp-123'),
    );
    // A bearer provider is present but MUST be ignored in demo mode.
    setAccessTokenProvider(async () => 'jwt-should-be-ignored');

    const headers = await prepareHeaders(new Headers());

    expect(headers.get('X-Demo-Employee-Id')).toBe('emp-123');
    expect(headers.get('Authorization')).toBeNull();
  });

  it('demo_mode_with_no_or_throwing_applier_degrades: no applier (remote default) or a throwing applier → no demo header, no crash (backend 403 path handles it)', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');

    // (a) no applier registered (the exposed remote's default) → no demo header.
    setDemoAuthHeaderApplier(null);
    const a = await prepareHeaders(new Headers());
    expect(a.get('X-Demo-Employee-Id')).toBeNull();
    expect(a.get('Authorization')).toBeNull();

    // (b) applier throws → still resolves cleanly (degrade, don't crash).
    setDemoAuthHeaderApplier(() => {
      throw new Error('demo seam not ready');
    });
    const b = await prepareHeaders(new Headers());
    expect(b.get('X-Demo-Employee-Id')).toBeNull();
    expect(b.get('Authorization')).toBeNull();
  });

  it('auth0_mode_attaches_bearer_only: VITE_AUTH_MODE=auth0 awaits getAccessToken → Bearer, never demo header (applier injected but ignored)', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'auth0');
    setAccessTokenProvider(async () => 'jwt-abc');
    // A demo applier is present but MUST be ignored in auth0 mode.
    setDemoAuthHeaderApplier((headers) =>
      headers.set('X-Demo-Employee-Id', 'emp-should-be-ignored'),
    );

    const headers = await prepareHeaders(new Headers());

    expect(headers.get('Authorization')).toBe('Bearer jwt-abc');
    expect(headers.get('X-Demo-Employee-Id')).toBeNull();
  });

  it('auth0_missing_accessor_surfaces_error: missing/throwing getAccessToken → normalized rejection, not a raw/unhandled throw', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'auth0');

    // (a) no provider configured
    setAccessTokenProvider(null);
    await expect(prepareHeaders(new Headers())).rejects.toThrow(
      /access token/i,
    );

    // (b) provider throws — original error normalized (raw cause not surfaced as the message)
    setAccessTokenProvider(async () => {
      throw new Error('network down: secret-ish internal detail');
    });
    const err = await prepareHeaders(new Headers()).catch((e: unknown) => e);
    expect(err).toBeInstanceOf(Error);
    expect((err as Error).message).toMatch(/access token/i);
    expect((err as Error).message).not.toMatch(/secret-ish/);
    // The raw cause IS preserved (debug-only) — hidden from the message, kept for logs.
    expect((err as Error).cause).toBeInstanceOf(Error);
  });

  it('unknown_auth_mode_fails_defensively: an unrecognized VITE_AUTH_MODE throws (no silent demo fall-through)', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'bogus');
    await expect(prepareHeaders(new Headers())).rejects.toThrow(
      /unsupported .*auth.*mode/i,
    );
  });
});

describe('tag types (§7 cache invalidation)', () => {
  it('tag_types_cover_nine_domains: TAG_TYPES are exactly the nine domain constants and baseApi uses them', () => {
    expect(TAG_TYPES).toEqual([
      'me',
      'rcdo',
      'plans',
      'commitments',
      'review',
      'disputes',
      'manager',
      'comments',
      'sync',
    ]);
    expect(new Set(TAG_TYPES).size).toBe(9);
    expect(baseApi.reducerPath).toBe('api');
  });
});

describe('env enforcement (folded 9.1 TODOs)', () => {
  it('api_base_url_enforced: required in prod builds, dev/test fall back to /', () => {
    expect(
      resolveApiBaseUrl({ VITE_API_BASE_URL: 'http://api', PROD: true }),
    ).toBe('http://api');
    expect(() => resolveApiBaseUrl({ PROD: true })).toThrow(
      /VITE_API_BASE_URL/,
    );
    expect(resolveApiBaseUrl({ PROD: false })).toBe('/');
  });
});
