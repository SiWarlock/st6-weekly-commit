import { Badge, Button } from 'flowbite-react';

/**
 * Minimal themed shell root (0.6). Proves the Cadence skin resolves through the
 * token bridge in both themes. The full feature surface (RTK Query, routes,
 * IC/manager views) lands across Phase 9; this is the buildable foundation.
 *
 * `App` is theme-agnostic product surface — it does NOT import the standalone
 * ThemeToggle (that is standalone-only chrome wired in `standalone/main.tsx`).
 */
export default function App() {
  return (
    <main className="min-h-screen bg-surface-app px-6 py-6 text-ink-primary">
      <section
        data-cy="token-probe"
        className="mx-auto max-w-[var(--reading-col)] rounded-lg border border-border bg-surface p-4 shadow-card"
      >
        <h1 className="text-display font-semibold tracking-tight text-ink-primary">
          Weekly Commit
        </h1>
        <p className="mt-2 text-body text-ink-secondary">
          Cadence-themed shell — dark default with a light toggle; tokens
          bridged into Tailwind utilities and the Flowbite skin.
        </p>
        <div className="mt-4 flex items-center gap-2">
          <Badge color="failure">Misaligned</Badge>
          <Badge color="success">Aligned</Badge>
          <Button color="primary">Lock week</Button>
        </div>
      </section>
    </main>
  );
}
