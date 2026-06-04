import { describe, it, expect } from 'vitest';
// References the not-yet-existing auth0 env resolver (RED until GREEN).
import { resolveAuth0Config } from './auth0Config';

/**
 * 9.17 — the Auth0 SPA env contract. Mirrors `resolveApiBaseUrl`'s injectable
 * shape: a pure resolver over the `VITE_AUTH0_*` build env that fails fast +
 * value-free when a required var is missing in an auth0-mode build (the deployed
 * demo can't silently boot a half-configured SDK). `redirect_uri` is derived as
 * `<origin>/callback` (the dedicated callback route, brief Q2).
 */
describe('9.17 — resolveAuth0Config (the VITE_AUTH0_* SPA contract)', () => {
  const full = {
    VITE_AUTH0_DOMAIN: 'tenant.us.auth0.com',
    VITE_AUTH0_CLIENT_ID: 'abc123',
    VITE_AUTH0_AUDIENCE: 'https://api.wc.example.com',
  };

  it('resolves domain/clientId/audience + derives redirect_uri = <origin>/callback', () => {
    const cfg = resolveAuth0Config(full, 'https://wc.example.com');
    expect(cfg).toEqual({
      domain: 'tenant.us.auth0.com',
      clientId: 'abc123',
      audience: 'https://api.wc.example.com',
      redirectUri: 'https://wc.example.com/callback',
    });
  });

  it.each([
    ['VITE_AUTH0_DOMAIN'],
    ['VITE_AUTH0_CLIENT_ID'],
    ['VITE_AUTH0_AUDIENCE'],
  ])('fails fast when %s is missing (loud, value-free error)', (missing) => {
    const env: Record<string, string> = { ...full };
    delete env[missing];
    let thrown: Error | undefined;
    try {
      resolveAuth0Config(env, 'https://wc.example.com');
    } catch (e) {
      thrown = e as Error;
    }
    expect(thrown).toBeInstanceOf(Error);
    // The message names the missing var but never leaks a configured value
    // (safety rule #7 posture — no secrets/values in errors).
    expect(thrown?.message).toContain(missing);
    expect(thrown?.message).not.toContain('abc123');
    expect(thrown?.message).not.toContain('tenant.us.auth0.com');
  });
});
