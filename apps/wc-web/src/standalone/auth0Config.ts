/**
 * 9.17 — the Auth0 SPA env contract (standalone-only). Mirrors `resolveApiBaseUrl`
 * (`app/baseApi.ts`): a pure resolver over the `VITE_AUTH0_*` build env that fails
 * fast + value-free when a required var is missing — a half-configured SDK must
 * not silently boot the deployed demo. The live tenant values are baked per build
 * (Appendix D.1 / §28); see `.env.example` for the `<ROOT_DOMAIN>`-keyed contract.
 *
 * Verified contract (backend-orch confirmed):
 *   - audience  = `https://api.wc.<ROOT_DOMAIN>` (== backend `auth0.audience`);
 *                 REQUIRED in `authorizationParams` so argless
 *                 `getAccessTokenSilently()` returns the API JWT (not an opaque
 *                 userinfo token the resource-server rejects).
 *   - domain    = the issuer tenant domain (SAME tenant as the backend's
 *                 `AUTH0_ISSUER_URI`).
 *   - redirect  = `<origin>/callback` (derived from `window.location.origin` at
 *                 runtime — never hardcoded). The `…/employee_id` custom claim the
 *                 resource-server reads is set by an Auth0 post-login Action, NOT
 *                 by the SPA.
 */
export interface Auth0Config {
  domain: string;
  clientId: string;
  audience: string;
  redirectUri: string;
}

export interface Auth0Env {
  VITE_AUTH0_DOMAIN?: string | undefined;
  VITE_AUTH0_CLIENT_ID?: string | undefined;
  VITE_AUTH0_AUDIENCE?: string | undefined;
}

function requireVar(value: string | undefined, name: string): string {
  if (!value) {
    // Name the missing var; never echo a configured value (safety-rule-#7 posture).
    throw new Error(`${name} is required for an auth0-mode build`);
  }
  return value;
}

export function resolveAuth0Config(env: Auth0Env, origin: string): Auth0Config {
  return {
    domain: requireVar(env.VITE_AUTH0_DOMAIN, 'VITE_AUTH0_DOMAIN'),
    clientId: requireVar(env.VITE_AUTH0_CLIENT_ID, 'VITE_AUTH0_CLIENT_ID'),
    audience: requireVar(env.VITE_AUTH0_AUDIENCE, 'VITE_AUTH0_AUDIENCE'),
    redirectUri: `${origin}/callback`,
  };
}
