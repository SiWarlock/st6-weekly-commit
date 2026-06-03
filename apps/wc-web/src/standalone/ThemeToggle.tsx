import { useThemePreference } from '../app/theme/useThemePreference';

/**
 * Dark/light toggle — STANDALONE-ONLY chrome (mirrors PersonaSwitcher). It lives
 * under `src/standalone/` and is never imported by the exposed remote, so it is
 * tree-shaken out of the Module Federation build (the host owns global chrome).
 * The bundle-absence proof lands in Phase 9.3 with the federation remote.
 */
export function ThemeToggle() {
  const { theme, toggle } = useThemePreference();
  const isDark = theme === 'dark';

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={`Switch to ${isDark ? 'light' : 'dark'} theme`}
      aria-pressed={isDark}
      className="inline-flex items-center gap-2 rounded-md border border-border-strong bg-surface-raised px-3 py-1.5 text-label text-ink-secondary transition-colors duration-base hover:bg-surface-hover hover:text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
    >
      {isDark ? 'Light mode' : 'Dark mode'}
    </button>
  );
}
