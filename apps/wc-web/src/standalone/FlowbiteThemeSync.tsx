import { useEffect, useRef } from 'react';
import { useThemeMode } from 'flowbite-react';
import { useThemePreference } from '../app/theme/useThemePreference';

/**
 * STANDALONE-ONLY (#6, ST.7d). flowbite-react 0.10.2's `<Flowbite>` mounts its
 * own `useThemeMode()`, which independently persists a `flowbite-theme-mode`
 * localStorage key (default `light`) and toggles the `.dark` class on `<html>` —
 * a SECOND source of truth that drifts from our `ThemeProvider`'s `[data-theme]`
 * (the QA saw `wc-theme=dark` vs `flowbite-theme-mode=light`). This null-component
 * makes our `ThemeProvider` the single source: it drives Flowbite's mode to match
 * our theme on every change, so Flowbite primitives (Drawer/Modal) render in the
 * app's theme. Mounted inside `<Flowbite>` (StandaloneShell); never in the remote
 * (the host owns theming there) — REQ-I-008 intact. (wc-web LESSONS §4 extension.)
 */
export function FlowbiteThemeSync() {
  const { theme } = useThemePreference();
  const { setMode } = useThemeMode();
  // `setMode` (flowbite's `handleSetMode`) is re-created each render; hold it in a
  // ref so the sync effect keys only on our theme (no per-render churn).
  const setModeRef = useRef(setMode);
  setModeRef.current = setMode;

  useEffect(() => {
    setModeRef.current(theme);
  }, [theme]);

  return null;
}
