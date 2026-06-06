/**
 * Resolve the host's Auth0 SPA config from build-time env (Vite inlines
 * `import.meta.env`). Mirrors the remote/standalone `resolveAuth0Config`
 * (apps/wc-web/src/standalone/auth0Config.ts) so the host authenticates against
 * the SAME tenant/client/audience as the deployed Weekly Commit app.
 *
 * Fail-fast (loud, at first read) if any value is missing — a half-configured
 * Auth0 SPA otherwise yields opaque login failures.
 */
export interface Auth0HostConfig {
  domain: string;
  clientId: string;
  audience: string;
  /** Derived from the runtime origin — the SPA never hardcodes the host URL. */
  redirectUri: string;
}

interface Auth0Env {
  VITE_AUTH0_DOMAIN?: string | undefined;
  VITE_AUTH0_CLIENT_ID?: string | undefined;
  VITE_AUTH0_AUDIENCE?: string | undefined;
}

export function resolveAuth0Config(
  env: Auth0Env,
  origin: string,
): Auth0HostConfig {
  const domain = env.VITE_AUTH0_DOMAIN;
  const clientId = env.VITE_AUTH0_CLIENT_ID;
  const audience = env.VITE_AUTH0_AUDIENCE;

  const missing = [
    ["VITE_AUTH0_DOMAIN", domain],
    ["VITE_AUTH0_CLIENT_ID", clientId],
    ["VITE_AUTH0_AUDIENCE", audience],
  ]
    .filter(([, v]) => !v)
    .map(([k]) => k);

  if (missing.length > 0) {
    throw new Error(
      `Auth0 host config incomplete — set ${missing.join(", ")} (see .env.example)`,
    );
  }

  return {
    domain: domain as string,
    clientId: clientId as string,
    audience: audience as string,
    redirectUri: `${origin}/callback`,
  };
}
