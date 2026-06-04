/**
 * 9.16 — build-target resolution. This app emits TWO artifacts from one
 * `vite.config.ts`:
 *
 *   - `'remote'`     → the Module Federation remote (`remoteEntry.js`), the
 *                      production PA-host integration path (§7). Federation
 *                      plugin ON. **The default** — `vite build` is unchanged.
 *   - `'standalone'` → the static SPA built from `index.html` (mounting
 *                      `src/standalone/main.tsx`) → `dist/index.html` + hashed
 *                      assets, servable from a static host. The deployable demo
 *                      artifact. Federation plugin OFF so Vite uses `index.html`
 *                      as the entry instead of building the `remoteEntry.js`
 *                      library.
 *
 * Pure + env-injected (mirrors `resolveApiBaseUrl` in `app/baseApi.ts`) so the
 * federation on/off decision is a unit-tested seam (`src/build/buildTarget.test.ts`)
 * rather than only build-verified. `vite.config.ts` reads `process.env` through
 * `shouldEnableFederation`; the `build:standalone` npm script sets the target.
 */
export type BuildTarget = 'remote' | 'standalone';

export interface BuildEnv {
  VITE_BUILD_TARGET?: string | undefined;
  VITEST?: string | undefined;
}

/**
 * Resolve the build target. Only the exact `'standalone'` opt-in switches off
 * the production remote build; any other/unset/unknown value resolves to
 * `'remote'` — fail-safe, so a typo'd target never silently disables the
 * production artifact (REQ-I-008 boundary stays the default path).
 */
export function resolveBuildTarget(env: BuildEnv): BuildTarget {
  return env.VITE_BUILD_TARGET === 'standalone' ? 'standalone' : 'remote';
}

/**
 * Whether to include the Module Federation plugin for this build. OFF under
 * Vitest (the jsdom unit suite proves the boundary via a static import-graph
 * test, not the rewritten module graph) and OFF for the standalone SPA build;
 * ON for the remote build (the default).
 */
export function shouldEnableFederation(env: BuildEnv): boolean {
  if (env.VITEST === 'true') {
    return false;
  }
  return resolveBuildTarget(env) === 'remote';
}
