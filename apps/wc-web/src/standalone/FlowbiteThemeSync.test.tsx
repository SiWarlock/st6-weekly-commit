import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach } from 'vitest';
import { Flowbite } from 'flowbite-react';
import { ThemeProvider } from '../app/theme/ThemeProvider';
import { useThemePreference } from '../app/theme/useThemePreference';
import { flowbiteTheme } from '../app/flowbiteTheme';
import { FlowbiteThemeSync } from './FlowbiteThemeSync';

// ST.7d #6 — Flowbite-react 0.10.2 tracks its OWN theme-mode (the
// `flowbite-theme-mode` localStorage key + the `.dark` class on <html>),
// independent of our ThemeProvider's `[data-theme]`. They drift (QA saw
// wc-theme=dark vs flowbite-theme-mode=light). FlowbiteThemeSync makes our
// ThemeProvider the single source of truth: Flowbite's mode tracks our theme.

function ToggleTheme() {
  const { toggle } = useThemePreference();
  return (
    <button type="button" onClick={toggle}>
      toggle theme
    </button>
  );
}

beforeEach(() => {
  // Clear Flowbite's independent persisted mode so each test starts clean.
  localStorage.removeItem('flowbite-theme-mode');
  document.documentElement.classList.remove('dark');
});

describe('FlowbiteThemeSync (#6 — Flowbite theme-mode follows our [data-theme])', () => {
  it('flowbite_mode_tracks_our_theme: with our theme dark, Flowbite mode is dark (.dark class + flowbite-theme-mode LS); toggling to light flips both — our ThemeProvider is the single source', async () => {
    const user = userEvent.setup();

    render(
      <ThemeProvider>
        <Flowbite theme={{ theme: flowbiteTheme }}>
          <FlowbiteThemeSync />
          <ToggleTheme />
        </Flowbite>
      </ThemeProvider>,
    );

    // Default resolves to dark (no stored pref; matchMedia stub → not light).
    // Our ThemeProvider sets [data-theme="dark"]; FlowbiteThemeSync drives
    // Flowbite's mode to match.
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(localStorage.getItem('flowbite-theme-mode')).toBe('dark');

    // Toggle our theme → light: Flowbite's mode follows (no desync).
    await user.click(screen.getByRole('button', { name: /toggle theme/i }));

    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(localStorage.getItem('flowbite-theme-mode')).toBe('light');
  });
});
