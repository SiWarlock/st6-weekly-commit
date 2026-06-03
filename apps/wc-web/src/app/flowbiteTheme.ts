import { createTheme, type CustomFlowbiteTheme } from 'flowbite-react';

/**
 * Flowbite-React custom theme (approach A, ST.1) — restyles the primitives this
 * product uses to the Cadence skin using ONLY token-backed Tailwind utilities
 * (which resolve through the `var(--…)` theme + flip via `[data-theme]`). No
 * bespoke `.wc-*` CSS. Applied app-wide via `<Flowbite theme={{ theme }}>`.
 */
export const flowbiteTheme: CustomFlowbiteTheme = createTheme({
  alert: {
    color: {
      failure:
        'bg-tone-failure-bg text-tone-failure-fg border border-tone-failure-border',
      warning:
        'bg-tone-warning-bg text-tone-warning-fg border border-tone-warning-border',
    },
  },
  badge: {
    root: {
      color: {
        neutral:
          'bg-tone-neutral-bg text-tone-neutral-fg border border-tone-neutral-border',
        info: 'bg-tone-info-bg text-tone-info-fg border border-tone-info-border',
        success:
          'bg-tone-success-bg text-tone-success-fg border border-tone-success-border',
        warning:
          'bg-tone-warning-bg text-tone-warning-fg border border-tone-warning-border',
        failure:
          'bg-tone-failure-bg text-tone-failure-fg border border-tone-failure-border',
        accent:
          'bg-tone-accent-bg text-tone-accent-fg border border-tone-accent-border',
      },
    },
  },
  button: {
    color: {
      primary:
        'bg-brand-600 text-brand-ink hover:bg-brand-500 focus:ring-brand-ring',
      secondary:
        'bg-surface-raised text-ink-primary border border-border-strong hover:bg-surface-hover focus:ring-brand-ring',
    },
  },
  table: {
    root: { base: 'w-full text-left text-body text-ink-secondary' },
    head: {
      base: 'text-label uppercase tracking-wide text-ink-secondary border-b border-border-strong',
    },
    body: {
      cell: { base: 'px-4 py-2.5 text-ink-primary border-b border-border' },
    },
  },
  // The Drawer needs its FULL positioning/backdrop slot owned here (ST.7e):
  // flowbite-react's default position/width/backdrop classes live only in its
  // `.mjs`/`.cjs` dist, which our tailwind content glob (`*.{js,jsx,ts,tsx}`)
  // never scans — so those default utilities are referenced-but-never-generated
  // (inert) and a partially-overridden Drawer renders as a bare `fixed` element
  // (top-left, content-sized, no scrim). Owning base + position.right + backdrop
  // here (all token-native, in the scanned src) makes the Cadence right-slide
  // full-height ~640px overlay + scrim actually render. (Don't widen the glob to
  // `.mjs` — that would emit ALL flowbite defaults → CSS bloat, REQ-NF-005.)
  drawer: {
    root: {
      base: 'fixed z-40 overflow-y-auto bg-surface-raised shadow-drawer transition-transform',
      backdrop: 'fixed inset-0 z-30 bg-scrim',
      position: {
        right: {
          on: 'right-0 top-0 h-screen w-full max-w-drawer transform-none',
          off: 'right-0 top-0 h-screen w-full max-w-drawer translate-x-full',
        },
      },
    },
  },
  modal: {
    content: { inner: 'relative rounded-xl bg-surface-raised shadow-modal' },
  },
  tooltip: {
    base: 'absolute z-10 rounded-md border border-border-strong bg-surface-raised px-2 py-1 text-meta text-ink-primary shadow-pop',
  },
});
