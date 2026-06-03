/**
 * Auth accessor seam (§7 host-integration contract). Thin, injectable contract —
 * NO demo/persona logic lives here. Providers are injected by the host (remote
 * mode, 9.3) or the standalone `DemoIdentityProvider` (standalone mode, 9.3);
 * `prepareHeaders` reads them to attach exactly one auth header per mode.
 */
export type AccessTokenProvider = () => Promise<string>;
/**
 * Applies the demo auth header(s) onto an outgoing request. The applier OWNS the
 * demo-header name literal — it lives in `src/standalone/` only (REQ-I-008), so
 * the shared, remote-reachable `baseApi`/`authAccessor` never carry it.
 */
export type DemoAuthHeaderApplier = (headers: Headers) => void;

let accessTokenProvider: AccessTokenProvider | null = null;
let demoAuthHeaderApplier: DemoAuthHeaderApplier | null = null;

/** Inject the auth0 bearer-token provider (or `null` to clear). */
export function setAccessTokenProvider(
  provider: AccessTokenProvider | null,
): void {
  accessTokenProvider = provider;
}

/** Inject the demo auth-header applier (or `null` to clear). */
export function setDemoAuthHeaderApplier(
  applier: DemoAuthHeaderApplier | null,
): void {
  demoAuthHeaderApplier = applier;
}

/**
 * Resolve the auth0 access token. Throws if no provider is configured — callers
 * (prepareHeaders) normalize that into a safe error.
 */
export async function getAccessToken(): Promise<string> {
  if (accessTokenProvider === null) {
    throw new Error('No access-token provider configured');
  }
  return accessTokenProvider();
}

/** Whether an access-token provider has been injected (remote/standalone wiring). */
export function hasAccessTokenProvider(): boolean {
  return accessTokenProvider !== null;
}

/**
 * Apply the demo auth header(s) via the injected applier. No-op when no applier
 * is registered (the exposed remote's default — it carries no demo path) and
 * degrades on a throwing applier — a demo request with no persona then sends no
 * header and the backend demo-gate (`DEMO_AUTH_ENABLED`) rejects with 403.
 */
export function applyDemoAuthHeader(headers: Headers): void {
  if (demoAuthHeaderApplier === null) {
    return;
  }
  try {
    demoAuthHeaderApplier(headers);
  } catch {
    /* degrade: a broken demo seam sends no demo header (backend 403 path). */
  }
}
