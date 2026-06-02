import { createContext, useContext } from 'react';

export type Theme = 'dark' | 'light';

/** localStorage key for the persisted theme preference (in-slice literal). */
export const STORAGE_KEY = 'wc-theme';

export interface ThemeContextValue {
  /** The active theme. */
  theme: Theme;
  /** Set the theme explicitly (persisted). */
  setTheme: (theme: Theme) => void;
  /** Flip dark↔light (persisted). */
  toggle: () => void;
}

export const ThemeContext = createContext<ThemeContextValue | null>(null);

/** Read the persisted preference, or null when absent/invalid. */
export function getStoredTheme(): Theme | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    return value === 'dark' || value === 'light' ? value : null;
  } catch {
    /* storage unavailable */
    return null;
  }
}

/** System color-scheme preference; defaults to dark when undetectable. */
export function getSystemPreference(): Theme {
  try {
    if (window.matchMedia('(prefers-color-scheme: light)').matches) {
      return 'light';
    }
  } catch {
    /* matchMedia unavailable */
  }
  return 'dark';
}

/** Resolution order: persisted preference → system preference → dark default. */
export function resolveInitialTheme(): Theme {
  return getStoredTheme() ?? getSystemPreference();
}

/**
 * Access the active theme + controls. Must be called within a `ThemeProvider`.
 */
export function useThemePreference(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (ctx === null) {
    throw new Error('useThemePreference must be used within a ThemeProvider');
  }
  return ctx;
}
