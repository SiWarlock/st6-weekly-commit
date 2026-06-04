import { describe, it, expect } from 'vitest';
// The 9.16 build-target resolver lives at the wc-web root, beside vite.config.ts
// (build config, not app source). This test sits at the src root so the Vitest
// `src/**` glob collects it AND it escapes the `.gitignore` `build/` rule.
import { resolveBuildTarget, shouldEnableFederation } from '../vite.buildTarget';

/**
 * 9.16 — the deterministic seam of the standalone-SPA build slice. The app emits
 * TWO artifacts from one vite.config: the Module Federation remote (remoteEntry.js,
 * the production PA-host path; federation ON) and the standalone static SPA
 * (index.html entry; federation OFF). This pins the pure mode-selection so the
 * config branch is unit-tested, not just build-verified. (Brief 083 RED #3.)
 */
describe('9.16 — build-target resolution (remote vs standalone SPA)', () => {
  it('defaults to the remote build with federation ON when no target is set', () => {
    expect(resolveBuildTarget({})).toBe('remote');
    expect(shouldEnableFederation({})).toBe(true);
  });

  it('resolves the standalone SPA target with federation OFF when VITE_BUILD_TARGET=standalone', () => {
    const env = { VITE_BUILD_TARGET: 'standalone' };
    expect(resolveBuildTarget(env)).toBe('standalone');
    expect(shouldEnableFederation(env)).toBe(false);
  });

  it('keeps federation OFF under Vitest regardless of target (the jsdom suite needs no module-graph rewrite)', () => {
    expect(shouldEnableFederation({ VITEST: 'true' })).toBe(false);
    expect(
      shouldEnableFederation({ VITEST: 'true', VITE_BUILD_TARGET: 'standalone' }),
    ).toBe(false);
    // Even an explicit remote target yields to the test env.
    expect(
      shouldEnableFederation({ VITEST: 'true', VITE_BUILD_TARGET: 'remote' }),
    ).toBe(false);
  });

  it('treats an explicit or unknown non-standalone target as the remote build (default-safe — never silently disables the production artifact)', () => {
    expect(resolveBuildTarget({ VITE_BUILD_TARGET: 'remote' })).toBe('remote');
    expect(shouldEnableFederation({ VITE_BUILD_TARGET: 'remote' })).toBe(true);
    // An unrecognized value must NOT accidentally turn federation off.
    expect(resolveBuildTarget({ VITE_BUILD_TARGET: 'spa' })).toBe('remote');
    expect(shouldEnableFederation({ VITE_BUILD_TARGET: 'spa' })).toBe(true);
  });
});
