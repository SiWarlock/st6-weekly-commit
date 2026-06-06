import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

// Anti-drift guard for the 9.13 host-integration contract (REQ-I-007/REQ-I-013):
// the README documents a federation surface + host accessor signature that MUST
// stay in lockstep with the realized config. Reading source-as-text mirrors the
// REQ-I-008 boundary test in this directory.
const here = dirname(fileURLToPath(import.meta.url));
const srcDir = resolve(here, '..');
const appDir = resolve(srcDir, '..');

const viteConfig = readFileSync(resolve(appDir, 'vite.config.ts'), 'utf8');
const readme = readFileSync(resolve(appDir, 'README.md'), 'utf8');
const authAccessor = readFileSync(
  resolve(srcDir, 'app/authAccessor.ts'),
  'utf8',
);

// The shared singletons the host + remote must agree on (federation `shared`).
// ALL React-coupled singletons are shared so the remote uses the host's single
// React instance — a bundled react-redux/@reduxjs/toolkit runs hooks against a
// second React → "Cannot read properties of null (reading 'useRef')" dual-React
// crash. The store is still self-provided by the remote (no `./store` expose);
// sharing redux doesn't reintroduce the deadlock because there's no separate
// pre-ensure store import (its importShared resolves inside the WeeklyCommitApp ensure).
const SHARED_SINGLETONS = [
  'react',
  'react-dom',
  '@reduxjs/toolkit',
  'react-redux',
  'react-router-dom',
];

describe('9.13 host-integration contract — README matches the realized config', () => {
  it('contract_doc_present_with_host_integration_section: README.md exists and carries the Host integration contract', () => {
    expect(readme).toMatch(/##\s+Host integration/);
  });

  it('documented_federation_identity_matches_vite_config: the README remote name / entry filename / exposed module match vite.config.ts', () => {
    // Realized in vite.config.ts.
    expect(viteConfig).toContain("name: 'wc_web'");
    expect(viteConfig).toContain("filename: 'remoteEntry.js'");
    expect(viteConfig).toContain("'./WeeklyCommitApp'");
    // Documented identically in the contract.
    expect(readme).toContain('wc_web');
    expect(readme).toContain('remoteEntry.js');
    expect(readme).toContain('./WeeklyCommitApp');
  });

  it('documented_shared_singletons_match_vite_config: every shared singleton in vite.config.ts is documented (and vice-versa)', () => {
    for (const dep of SHARED_SINGLETONS) {
      const escaped = dep.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      // Each is a (optionally quoted) key in the federation `shared` block with
      // a requiredVersion — e.g. `react: { requiredVersion` / `'react-dom': { …`.
      expect(viteConfig).toMatch(
        new RegExp(`'?${escaped}'?:\\s*\\{\\s*requiredVersion`),
      );
      // ...and is listed in the README contract.
      expect(readme).toContain(dep);
    }
  });

  it('documented_getAccessToken_signature_matches_authAccessor: the host accessor signature in the contract matches src/app/authAccessor.ts', () => {
    // The realized accessor type.
    expect(authAccessor).toContain(
      'export type AccessTokenProvider = () => Promise<string>',
    );
    // The contract documents that same signature.
    expect(readme).toContain('getAccessToken(): Promise<string>');
  });
});
