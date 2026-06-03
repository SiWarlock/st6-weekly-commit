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

    // (c) the remote creates NO router of its own (consumes the host router),
    // and (d, fail-closed) carries NO demo-header / demo-token literal. baseApi
    // (which holds the demo branch) is NOT in the remote graph today; this guards
    // a future slice from pulling it in. See the Step-9 flag re splitting the
    // demo-header attach out of baseApi before 9.4 wires the store into the remote.
    for (const f of files) {
      const code = stripComments(readFileSync(f, 'utf8'));
      expect(code).not.toMatch(/\bBrowserRouter\b/);
      expect(code).not.toMatch(/X-Demo-Employee-Id/);
      expect(code).not.toMatch(/demo-token/);
    }
  });
});
