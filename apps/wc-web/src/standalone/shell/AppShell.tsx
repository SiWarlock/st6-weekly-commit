import type { ReactNode } from 'react';
import { AppBar } from './AppBar';
import { PrimaryNav } from './PrimaryNav';
import { Breadcrumb } from './Breadcrumb';

/**
 * ST.8a — the standalone demo app-shell: the global chrome (top app-bar +
 * persona-gated WC sub-nav + breadcrumb) the production PA host owns in the
 * composed app (§7). Standalone simulates it for the demo and is tree-shaken from
 * the exposed remote build (REQ-I-008 — only `StandaloneShell` mounts this).
 * Wraps the routed content (`<WeeklyCommitApp/>` in StandaloneShell; a stub in
 * tests) — the nav/breadcrumb consume the router that already lives above it.
 */
export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen bg-surface-app">
      <AppBar />
      <PrimaryNav />
      <Breadcrumb />
      {children}
    </div>
  );
}
