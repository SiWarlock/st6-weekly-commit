import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { importGraph, stripComments } from '../test/util';

const here = dirname(fileURLToPath(import.meta.url));
const srcDir = resolve(here, '..');
const remoteEntry = resolve(srcDir, 'remote/WeeklyCommitApp.tsx');
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
});
