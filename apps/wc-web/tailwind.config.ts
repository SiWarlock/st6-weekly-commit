import type { Config } from 'tailwindcss';
import flowbite from 'flowbite/plugin';

/**
 * Cadence design-system → Tailwind bridge (approach A, ST.1).
 *
 * Every semantic scale references a CSS custom property declared in
 * `src/styles/theme.css`. Tokens are NEVER hardcoded here — that single token
 * layer is the source of truth and the `[data-theme]` flip seam, so a theme
 * switch is one attribute change with no Tailwind rebuild.
 *
 * `darkMode` is bound to our `[data-theme="dark"]` attribute (Tailwind 3.4
 * custom-selector strategy) so Flowbite-React's baked-in `dark:` slots fire
 * only under our dark theme — never independently on OS `prefers-color-scheme`.
 * We author no `dark:` utilities of our own; our tokens flip via `var()`.
 */
export default {
  content: [
    './index.html',
    './src/**/*.{ts,tsx}',
    './node_modules/flowbite-react/**/*.{js,jsx,ts,tsx}',
    './node_modules/flowbite/**/*.js',
  ],
  darkMode: ['selector', '[data-theme="dark"]'],
  theme: {
    extend: {
      colors: {
        // Surfaces
        'surface-app': 'var(--surface-app)',
        surface: 'var(--surface)',
        'surface-raised': 'var(--surface-raised)',
        'surface-sunken': 'var(--surface-sunken)',
        'surface-hover': 'var(--surface-hover)',
        // Ink
        ink: {
          primary: 'var(--ink-primary)',
          secondary: 'var(--ink-secondary)',
          muted: 'var(--ink-muted)',
        },
        // Borders
        border: 'var(--border)',
        'border-strong': 'var(--border-strong)',
        'border-solid': 'var(--border-solid)',
        // Brand (interactive-only; theme-stable indigo)
        brand: {
          soft: 'var(--brand-soft)',
          400: 'var(--brand-400)',
          500: 'var(--brand-500)',
          600: 'var(--brand-600)',
          ink: 'var(--brand-ink)',
          ring: 'var(--brand-ring)',
        },
        // Six status tones × {fg,bg,border,solid}
        'tone-neutral-fg': 'var(--tone-neutral-fg)',
        'tone-neutral-bg': 'var(--tone-neutral-bg)',
        'tone-neutral-border': 'var(--tone-neutral-border)',
        'tone-neutral-solid': 'var(--tone-neutral-solid)',
        'tone-info-fg': 'var(--tone-info-fg)',
        'tone-info-bg': 'var(--tone-info-bg)',
        'tone-info-border': 'var(--tone-info-border)',
        'tone-info-solid': 'var(--tone-info-solid)',
        'tone-success-fg': 'var(--tone-success-fg)',
        'tone-success-bg': 'var(--tone-success-bg)',
        'tone-success-border': 'var(--tone-success-border)',
        'tone-success-solid': 'var(--tone-success-solid)',
        'tone-warning-fg': 'var(--tone-warning-fg)',
        'tone-warning-bg': 'var(--tone-warning-bg)',
        'tone-warning-border': 'var(--tone-warning-border)',
        'tone-warning-solid': 'var(--tone-warning-solid)',
        'tone-failure-fg': 'var(--tone-failure-fg)',
        'tone-failure-bg': 'var(--tone-failure-bg)',
        'tone-failure-border': 'var(--tone-failure-border)',
        'tone-failure-solid': 'var(--tone-failure-solid)',
        'tone-accent-fg': 'var(--tone-accent-fg)',
        'tone-accent-bg': 'var(--tone-accent-bg)',
        'tone-accent-border': 'var(--tone-accent-border)',
        'tone-accent-solid': 'var(--tone-accent-solid)',
        // Heatmap volume fill (neutral load encoding)
        'vol-light': 'var(--vol-light)',
        'vol-normal': 'var(--vol-normal)',
        'vol-heavy': 'var(--vol-heavy)',
        // Overlay scrim behind drawers/modals (ST.7e)
        scrim: 'var(--overlay-scrim)',
      },
      fontFamily: {
        sans: 'var(--font-sans)',
        mono: 'var(--font-mono)',
      },
      fontSize: {
        display: 'var(--text-display)',
        h2: 'var(--text-h2)',
        h3: 'var(--text-h3)',
        body: 'var(--text-body)',
        label: 'var(--text-label)',
        meta: 'var(--text-meta)',
        mono: 'var(--text-mono)',
      },
      spacing: {
        1: 'var(--space-1)',
        2: 'var(--space-2)',
        3: 'var(--space-3)',
        4: 'var(--space-4)',
        5: 'var(--space-5)',
        6: 'var(--space-6)',
        8: 'var(--space-8)',
      },
      borderRadius: {
        sm: 'var(--radius-sm)',
        md: 'var(--radius-md)',
        lg: 'var(--radius-lg)',
        xl: 'var(--radius-xl)',
        full: 'var(--radius-full)',
      },
      boxShadow: {
        card: 'var(--shadow-card)',
        pop: 'var(--shadow-pop)',
        drawer: 'var(--shadow-drawer)',
        modal: 'var(--shadow-modal)',
        hairline: 'var(--shadow-hairline)',
      },
      // Reading-column / content max-widths (Cadence surface widths, ST.4).
      maxWidth: {
        'reading-col': 'var(--reading-col)',
        'content-max': 'var(--content-max)',
        drawer: 'var(--drawer-w)', // Cadence right-slide overlay width (ST.7e)
      },
      transitionDuration: {
        fast: 'var(--dur-fast)',
        base: 'var(--dur-base)',
        panel: 'var(--dur-panel)',
      },
      transitionTimingFunction: {
        DEFAULT: 'var(--ease)',
      },
    },
  },
  plugins: [flowbite],
} satisfies Config;
