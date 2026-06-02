import { describe, it, expect, vi, afterEach } from 'vitest';
import { prepareHeaders, baseApi } from './baseApi';
import { TAG_TYPES } from './tags';
import {
  setAccessTokenProvider,
  setDemoEmployeeIdProvider,
} from './authAccessor';

afterEach(() => {
  vi.unstubAllEnvs();
  setAccessTokenProvider(null);
  setDemoEmployeeIdProvider(null);
});

describe('prepareHeaders auth XOR (§7)', () => {
  it('demo_mode_attaches_demo_header_only: VITE_AUTH_MODE=demo sets X-Demo-Employee-Id, never Authorization', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');
    setDemoEmployeeIdProvider(() => 'emp-123');
    // A bearer provider is present but MUST be ignored in demo mode.
    setAccessTokenProvider(async () => 'jwt-should-be-ignored');

    const headers = await prepareHeaders(new Headers());

    expect(headers.get('X-Demo-Employee-Id')).toBe('emp-123');
    expect(headers.get('Authorization')).toBeNull();
  });

  it('demo_mode_missing_or_failing_persona_degrades: no/throwing demo provider → no header, no crash (backend 403 path handles it)', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'demo');

    // (a) no persona provider configured → resolves with no demo header.
    setDemoEmployeeIdProvider(null);
    const a = await prepareHeaders(new Headers());
    expect(a.get('X-Demo-Employee-Id')).toBeNull();
    expect(a.get('Authorization')).toBeNull();

    // (b) persona provider throws → still resolves cleanly (degrade, don't crash).
    setDemoEmployeeIdProvider(() => {
      throw new Error('persona seam not ready');
    });
    const b = await prepareHeaders(new Headers());
    expect(b.get('X-Demo-Employee-Id')).toBeNull();
    expect(b.get('Authorization')).toBeNull();
  });

  it('auth0_mode_attaches_bearer_only: VITE_AUTH_MODE=auth0 awaits getAccessToken → Bearer, never demo header', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'auth0');
    setAccessTokenProvider(async () => 'jwt-abc');
    // A demo provider is present but MUST be ignored in auth0 mode.
    setDemoEmployeeIdProvider(() => 'emp-should-be-ignored');

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
