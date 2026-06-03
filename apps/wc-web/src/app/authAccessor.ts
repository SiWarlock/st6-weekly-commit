/**
 * Auth accessor seam (§7 host-integration contract). Thin, injectable contract —
 * NO demo/persona logic lives here. Providers are injected by the host (remote
 * mode, 9.3) or the standalone `DemoIdentityProvider` (standalone mode, 9.3);
 * `prepareHeaders` reads them to attach exactly one auth header per mode.
 */
export type AccessTokenProvider = () => Promise<string>;
export type DemoEmployeeIdProvider = () => string | null;

let accessTokenProvider: AccessTokenProvider | null = null;
let demoEmployeeIdProvider: DemoEmployeeIdProvider | null = null;

/** Inject the auth0 bearer-token provider (or `null` to clear). */
export function setAccessTokenProvider(
  provider: AccessTokenProvider | null,
): void {
  accessTokenProvider = provider;
}

/** Inject the demo persona-id provider (or `null` to clear). */
export function setDemoEmployeeIdProvider(
  provider: DemoEmployeeIdProvider | null,
): void {
  demoEmployeeIdProvider = provider;
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
 * Resolve the demo persona id, or `null` when unset/unavailable. Degrades on a
 * throwing provider (returns null) — a demo request with no persona sends no
 * header and the backend demo-gate (`DEMO_AUTH_ENABLED`) rejects with 403.
 */
export function getDemoEmployeeId(): string | null {
  if (demoEmployeeIdProvider === null) {
    return null;
  }
  try {
    return demoEmployeeIdProvider();
  } catch {
    return null;
  }
}
