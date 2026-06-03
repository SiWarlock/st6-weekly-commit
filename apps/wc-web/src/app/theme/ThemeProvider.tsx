import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import {
  ThemeContext,
  STORAGE_KEY,
  resolveInitialTheme,
  type Theme,
  type ThemeContextValue,
} from './useThemePreference';

interface ThemeProviderProps {
  children: ReactNode;
}

/** Persist the preference; degrade to in-memory only if storage is unavailable. */
function persist(theme: Theme): void {
  try {
    localStorage.setItem(STORAGE_KEY, theme);
  } catch {
    /* storage unavailable — in-memory only */
  }
}

/**
 * Owns the active theme: resolves the initial value (persisted → system → dark),
 * reflects it onto the document root as `data-theme`, and persists changes.
 * The token layer flips entirely through `var()` keyed on that attribute, so no
 * Tailwind rebuild or `dark:` utilities are involved. Reduced-motion is honored
 * declaratively in `theme.css`.
 */
export function ThemeProvider({ children }: ThemeProviderProps) {
  const [theme, setThemeState] = useState<Theme>(() => resolveInitialTheme());

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
  }, [theme]);

  const setTheme = useCallback((next: Theme) => {
    setThemeState(next);
    persist(next);
  }, []);

  const toggle = useCallback(() => {
    setThemeState((current) => {
      const next: Theme = current === 'dark' ? 'light' : 'dark';
      persist(next);
      return next;
    });
  }, []);

  const value = useMemo<ThemeContextValue>(
    () => ({ theme, setTheme, toggle }),
    [theme, setTheme, toggle],
  );

  return (
    <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
  );
}
