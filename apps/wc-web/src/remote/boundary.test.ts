import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { importGraph, stripComments } from '../test/util';

const here = dirname(fileURLToPath(import.meta.url));
const srcDir = resolve(here, '..');
const remoteEntry = resolve(srcDir, 'remote/WeeklyCommitApp.tsx');
const standaloneEntry = resolve(srcDir, 'standalone/main.tsx');
const standaloneDir = resolve(srcDir, 'standalone');

describe('REQ-I-008 — exposed remote excludes demo/persona/chrome (frontend mirror of safety rule #5)', () => {
  it('REQ_I_008_remote_excludes_demo_and_chrome: no standalone-only module is reachable from the remote entry', () => {
    const files = [...importGraph(remoteEntry)];

    // (a) NO module under src/standalone/ is reachable from the exposed remote.
    const standaloneReachable = files.filter((f) =>
      f.startsWith(standaloneDir),
    );
    expect(standaloneReachable).toEqual([]);

    // (b) the named standalone-only modules are absent from the remote graph.
    for (const name of [
      'PersonaSwitcher',
      'DemoIdentityProvider',
      'ThemeToggle',
      'StandaloneShell',
      'main',
    ]) {
      expect(
        files.some(
          (f) => f.endsWith(`/${name}.tsx`) || f.endsWith(`/${name}.ts`),
        ),
      ).toBe(false);
    }

    // (c) POSITIVE CONTROL: the route tree AND — as of 9.5 — the eager RTK Query
    // gating chain (AppRoutes→useIsManager→useCurrentUser→meApi→baseApi/authAccessor)
    // ARE reachable from the remote entry. Guards against this test silently passing
    // if the mount/gating is removed, and confirms the demo-literal scan below
    // actually covers baseApi now that it is in the remote build closure (REQ-I-008).
    for (const required of [
      '/AppRoutes.tsx',
      '/app/baseApi.ts',
      '/app/authAccessor.ts',
      '/features/me/meApi.ts',
    ]) {
      expect(files.some((f) => f.endsWith(required))).toBe(true);
    }

    // (d) the remote creates NO router of its own (consumes the host router), and
    // (e, fail-closed) carries NO demo-header / demo-token literal anywhere in the
    // enlarged closure. The 9.4 split moved the X-Demo-Employee-Id attach out of
    // the shared baseApi/authAccessor into a standalone-only applier seam, so even
    // now that baseApi/meApi ARE eagerly in the remote graph (9.5 gating), the demo
    // literal stays out of it (REQ-I-008, the safety pin of this slice).
    for (const f of files) {
      const code = stripComments(readFileSync(f, 'utf8'));
      expect(code).not.toMatch(/\bBrowserRouter\b/);
      expect(code).not.toMatch(/X-Demo-Employee-Id/);
      expect(code).not.toMatch(/demo-token/);
    }
  });

  it('msw_absent_from_remote_build: the MSW mock layer (ST.7a) is standalone-only — reachable from the standalone entry (positive control) but NEVER from the exposed remote (fail-open import-graph + fail-closed literal scan)', () => {
    const remoteFiles = [...importGraph(remoteEntry)];
    const standaloneFiles = [...importGraph(standaloneEntry)];

    // POSITIVE CONTROL — the mock layer IS part of the standalone build graph
    // (main.tsx dev-imports ./mocks/browser). Without this the fail-closed scan
    // below could pass vacuously (e.g. if the mock layer were never wired at all).
    expect(standaloneFiles.some((f) => f.includes('/mocks/'))).toBe(true);
    // …and the `msw` import literal survives into the standalone closure (the
    // string the scan below excludes from the remote — proving it is live).
    expect(
      standaloneFiles.some((f) =>
        /from\s+['"]msw/.test(readFileSync(f, 'utf8')),
      ),
    ).toBe(true);

    // FAIL-OPEN import-graph: no mocks/* module is reachable from the remote.
    expect(remoteFiles.filter((f) => f.includes('/mocks/'))).toEqual([]);

    // FAIL-CLOSED literal scan: no `msw`/setupWorker/mockServiceWorker reference
    // anywhere in the remote closure (REQ-I-008 — the demo/dev mock never ships
    // in the federation-exposed build, mirroring the demo-identity boundary §6/§8).
    for (const f of remoteFiles) {
      const code = stripComments(readFileSync(f, 'utf8'));
      expect(code).not.toMatch(/from\s+['"]msw/);
      expect(code).not.toMatch(/setupWorker/);
      expect(code).not.toMatch(/mockServiceWorker/);
    }
  });

  it('auth0_sdk_absent_from_remote_build: the Auth0 OAuth-login SDK (9.17) is standalone-only — reachable from the standalone entry (positive control) but NEVER from the exposed remote (fail-open import-graph + fail-closed literal scan)', () => {
    const remoteFiles = [...importGraph(remoteEntry)];
    const standaloneFiles = [...importGraph(standaloneEntry)];

    // POSITIVE CONTROL — the auth0 SDK literal is live in the standalone closure
    // (the standalone shell statically imports Auth0IdentityProvider). Without
    // this the fail-closed scan below could pass vacuously.
    expect(
      standaloneFiles.some((f) =>
        /@auth0\/auth0-react/.test(readFileSync(f, 'utf8')),
      ),
    ).toBe(true);

    // FAIL-OPEN import-graph: no standalone-only module carrying the SDK is
    // relative-reachable from the remote (the standalone dir exclusion above
    // already covers this; restated for the auth0 surface).
    expect(remoteFiles.filter((f) => f.startsWith(standaloneDir))).toEqual([]);

    // FAIL-CLOSED literal scan: no @auth0/auth0-react import nor login markers
    // anywhere in the remote closure (REQ-I-008 — the OAuth login producer ships
    // only in the standalone demo build, never the federation-exposed remote).
    for (const f of remoteFiles) {
      const code = stripComments(readFileSync(f, 'utf8'));
      expect(code).not.toMatch(/@auth0\/auth0-react/);
      expect(code).not.toMatch(/loginWithRedirect/);
      expect(code).not.toMatch(/getAccessTokenSilently/);
    }
  });
});
