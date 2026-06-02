import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import tailwindConfig from '../../../tailwind.config';
import { flowbiteTheme } from '../flowbiteTheme';
import {
  asRecord,
  collectLeafStrings,
  listFiles,
  readVarInSelector,
} from '../../test/util';

const here = dirname(fileURLToPath(import.meta.url));
const srcDir = resolve(here, '../..');
const themeCssPath = resolve(srcDir, 'styles/theme.css');

describe('Cadence token bridge (F1, approach A)', () => {
  it('tailwind_theme_exposes_cadence_tokens: semantic scales resolve to var(--…), never hardcoded hex', () => {
    const extend = asRecord(asRecord(asRecord(tailwindConfig).theme).extend);
    const colors = asRecord(extend.colors);

    // Representative semantic scales the product depends on.
    expect(colors.surface).toBe('var(--surface)');
    expect(asRecord(colors.brand)['600']).toBe('var(--brand-600)');
    expect(colors['tone-failure-fg']).toBe('var(--tone-failure-fg)');

    // EVERY color leaf must be a var(--…) reference — no literal hex anywhere.
    const leaves = collectLeafStrings(colors);
    expect(leaves.length).toBeGreaterThan(0);
    for (const leaf of leaves) {
      expect(leaf).toMatch(/^var\(--[a-z0-9-]+\)$/);
      expect(leaf).not.toMatch(/#[0-9a-fA-F]{3,8}/);
    }

    // EVERY bridged non-color scale is var-backed too (no scale ships un-bridged).
    for (const scale of [
      'spacing',
      'borderRadius',
      'boxShadow',
      'transitionDuration',
      'fontFamily',
      'fontSize',
      'transitionTimingFunction',
    ]) {
      const map = asRecord(extend[scale]);
      const vals = collectLeafStrings(map);
      expect(vals.length).toBeGreaterThan(0);
      for (const v of vals) {
        expect(v).toMatch(/var\(--[a-z0-9-]+\)/);
      }
    }
  });

  it('tailwind_darkmode_binds_to_data_theme: dark variant fires only under [data-theme="dark"], never OS media', () => {
    // Binds Flowbite-React's baked-in `dark:` slots to OUR attribute so the OS
    // `prefers-color-scheme` can't drive component theming independently of the
    // toggle. Tailwind 3.4 custom-selector strategy.
    expect(asRecord(tailwindConfig).darkMode).toEqual([
      'selector',
      '[data-theme="dark"]',
    ]);
  });

  it('flowbite_theme_object_skins_primitives: exports token-backed overrides for the primitives this product uses', () => {
    const theme = asRecord(flowbiteTheme);
    for (const key of [
      'badge',
      'button',
      'table',
      'drawer',
      'modal',
      'tooltip',
    ]) {
      expect(theme).toHaveProperty(key);
      expect(theme[key]).toBeTypeOf('object');
    }

    // Skins must use token-backed utilities (approach A — no raw hex / non-token).
    const buttonColor = asRecord(asRecord(theme.button).color);
    expect(String(buttonColor.primary)).toContain('bg-brand-600');
    const badgeColor = asRecord(asRecord(asRecord(theme.badge).root).color);
    expect(String(badgeColor.failure)).toContain('tone-failure');
  });

  it('no_wc_component_css: no .wc-* class-based stylesheet ships (token-var file only)', () => {
    const cssFiles = listFiles(srcDir, /\.css$/);
    // The only permitted stylesheet is the token-variable layer.
    expect(cssFiles.map((f) => f.replace(srcDir, ''))).toContain(
      '/styles/theme.css',
    );
    for (const file of cssFiles) {
      const text = readFileSync(file, 'utf8');
      expect(text).not.toMatch(/\.wc-[a-z]/i);
    }
  });

  it('token_value_differs_across_themes: --surface differs dark↔light; --brand-600 identical (#5E6AD2)', () => {
    const css = readFileSync(themeCssPath, 'utf8');

    const darkSurface = readVarInSelector(
      css,
      '[data-theme="dark"]',
      '--surface',
    );
    const lightSurface = readVarInSelector(
      css,
      '[data-theme="light"]',
      '--surface',
    );
    expect(darkSurface).toBeTruthy();
    expect(lightSurface).toBeTruthy();
    expect(lightSurface).not.toBe(darkSurface);

    // The indigo brand is theme-stable: every --brand-600 declaration is #5E6AD2.
    const brandDecls = [...css.matchAll(/--brand-600\s*:\s*([^;]+);/g)].map(
      (m) => (m[1] ?? '').trim().toLowerCase(),
    );
    expect(brandDecls.length).toBeGreaterThan(0);
    for (const decl of brandDecls) {
      expect(decl).toBe('#5e6ad2');
    }
  });
});
