import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { readFileSync, existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { ThemeProvider } from '../app/theme/ThemeProvider';
import { ThemeToggle } from './ThemeToggle';
import { listFiles, stripComments } from '../test/util';

const here = dirname(fileURLToPath(import.meta.url));
const srcDir = resolve(here, '..');

describe('ThemeToggle (F2 — standalone-only chrome)', () => {
  it('theme_toggle_renders: renders an accessible control reflecting the active theme', () => {
    render(
      <ThemeProvider>
        <ThemeToggle />
      </ThemeProvider>,
    );
    const btn = screen.getByRole('button');
    expect(btn).not.toBeNull();
    // Default theme is dark → the control offers light + reflects pressed state.
    expect(btn).toHaveAttribute('aria-pressed', 'true');
    expect(btn).toHaveAttribute('aria-label', 'Switch to light theme');
  });

  it('theme_toggle_is_standalone_only: lives under src/standalone and no non-standalone module imports it', () => {
    // Placement: the toggle is standalone chrome (mirrors PersonaSwitcher).
    expect(existsSync(resolve(srcDir, 'standalone/ThemeToggle.tsx'))).toBe(
      true,
    );

    // No module OUTSIDE src/standalone may import ThemeToggle (tree-shake-from-remote
    // convention; full bundle-absence proof is deferred to Phase 9.3).
    const nonStandalone = listFiles(srcDir, /\.(ts|tsx)$/).filter(
      (f) => !f.includes(`${resolve(srcDir, 'standalone')}`),
    );
    const offenders = nonStandalone.filter((f) => {
      const text = stripComments(readFileSync(f, 'utf8'));
      return /import[^;]*\bThemeToggle\b/.test(text);
    });
    expect(offenders).toEqual([]);
  });
});
