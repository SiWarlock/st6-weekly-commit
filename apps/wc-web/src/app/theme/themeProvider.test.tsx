import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { ThemeProvider } from './ThemeProvider';
import { useThemePreference } from './useThemePreference';

const STORAGE_KEY = 'wc-theme';

/** Stub matchMedia so `(prefers-color-scheme: light)` reports `prefersLight`. */
function setPrefersLight(prefersLight: boolean): void {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: query.includes('prefers-color-scheme: light')
      ? prefersLight
      : false,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
}

function Probe() {
  const { theme, setTheme, toggle } = useThemePreference();
  return (
    <div>
      <span data-testid="current-theme">{theme}</span>
      <button type="button" onClick={toggle}>
        toggle
      </button>
      <button type="button" onClick={() => setTheme('light')}>
        set-light
      </button>
      <button type="button" onClick={() => setTheme('dark')}>
        set-dark
      </button>
    </div>
  );
}

function rootTheme(): string | null {
  return document.documentElement.getAttribute('data-theme');
}

describe('theme preference resolution (F2)', () => {
  it('theme_default_is_dark: no storage + no prefers-color-scheme → dark', () => {
    setPrefersLight(false);
    render(
      <ThemeProvider>
        <Probe />
      </ThemeProvider>,
    );
    expect(rootTheme()).toBe('dark');
    expect(screen.getByTestId('current-theme')).toHaveTextContent('dark');
  });

  it('theme_initial_respects_prefers_and_storage: storage wins over prefers; prefers-light w/o storage → light', () => {
    // (a) storage = light beats a dark system preference.
    localStorage.setItem(STORAGE_KEY, 'light');
    setPrefersLight(false);
    const a = render(
      <ThemeProvider>
        <Probe />
      </ThemeProvider>,
    );
    expect(rootTheme()).toBe('light');
    a.unmount();

    // (b) storage = dark beats a light system preference.
    localStorage.setItem(STORAGE_KEY, 'dark');
    document.documentElement.removeAttribute('data-theme');
    setPrefersLight(true);
    const b = render(
      <ThemeProvider>
        <Probe />
      </ThemeProvider>,
    );
    expect(rootTheme()).toBe('dark');
    b.unmount();

    // (c) no storage + prefers-light → light.
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    setPrefersLight(true);
    render(
      <ThemeProvider>
        <Probe />
      </ThemeProvider>,
    );
    expect(rootTheme()).toBe('light');
  });

  it('toggle_flips_and_persists: toggling flips data-theme and writes localStorage', async () => {
    setPrefersLight(false); // default dark
    const user = userEvent.setup();
    render(
      <ThemeProvider>
        <Probe />
      </ThemeProvider>,
    );
    expect(rootTheme()).toBe('dark');

    await user.click(screen.getByRole('button', { name: 'toggle' }));
    expect(rootTheme()).toBe('light');
    expect(localStorage.getItem(STORAGE_KEY)).toBe('light');

    await user.click(screen.getByRole('button', { name: 'toggle' }));
    expect(rootTheme()).toBe('dark');
    expect(localStorage.getItem(STORAGE_KEY)).toBe('dark');
  });

  it('set_theme_sets_and_persists: setTheme writes the root attribute and localStorage', async () => {
    setPrefersLight(false); // default dark
    const user = userEvent.setup();
    render(
      <ThemeProvider>
        <Probe />
      </ThemeProvider>,
    );
    expect(rootTheme()).toBe('dark');

    await user.click(screen.getByRole('button', { name: 'set-light' }));
    expect(rootTheme()).toBe('light');
    expect(localStorage.getItem(STORAGE_KEY)).toBe('light');

    await user.click(screen.getByRole('button', { name: 'set-dark' }));
    expect(rootTheme()).toBe('dark');
    expect(localStorage.getItem(STORAGE_KEY)).toBe('dark');
  });

  it('toggle_survives_storage_failure: a throwing localStorage still flips data-theme (in-memory)', async () => {
    setPrefersLight(false); // default dark
    const setItem = vi
      .spyOn(Storage.prototype, 'setItem')
      .mockImplementation(() => {
        throw new Error('quota exceeded');
      });
    const user = userEvent.setup();
    render(
      <ThemeProvider>
        <Probe />
      </ThemeProvider>,
    );
    expect(rootTheme()).toBe('dark');

    // Persistence throws, but the theme must still flip (graceful degradation).
    await user.click(screen.getByRole('button', { name: 'toggle' }));
    expect(rootTheme()).toBe('light');

    setItem.mockRestore();
  });
});
