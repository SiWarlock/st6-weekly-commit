import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import tailwindConfig from '../../../tailwind.config';
import { asRecord, stripComments } from '../../test/util';

// ST.4 surface/density/elevation skin — config-token assertions + the structural
// guard (the ST.3 #7 analogue, extended to hex/px). Reading source-as-text +
// importing the config mirror `themeBridge.test.ts`.
const here = dirname(fileURLToPath(import.meta.url));
const srcDir = resolve(here, '../..');

const extend = asRecord(asRecord(asRecord(tailwindConfig).theme).extend);

const TOUCHED_SURFACES = [
  'features/plan/WeeklyPlanView.tsx',
  'features/plan/PlanLifecycleBar.tsx', // ST.5a stepper
  'features/manager/CommandCenter.tsx',
  'features/manager/HeatmapGrid.tsx',
  'features/commitment/CommitmentList.tsx',
  'features/commitment/RcdoBreadcrumb.tsx', // ST.5b
  'features/commitment/OutcomePill.tsx', // ST.5b
];

describe('ST.4 surface skin — token wiring + structural guard', () => {
  it('tailwind_config_exposes_surface_maxwidth_scale: theme.extend.maxWidth maps reading-col/content-max to their CSS vars (token-native, §7)', () => {
    const maxWidth = asRecord(extend.maxWidth);
    expect(maxWidth['reading-col']).toBe('var(--reading-col)');
    expect(maxWidth['content-max']).toBe('var(--content-max)');
  });

  it('tailwind_config_exposes_hairline_elevation_token: theme.extend.boxShadow.hairline is a var-backed token (composite lives in theme.css, per the config token-layer principle + the existing shadow tokens)', () => {
    const boxShadow = asRecord(extend.boxShadow);
    expect(boxShadow.hairline).toBe('var(--shadow-hairline)');
  });

  it('primary_surfaces_use_token_max_widths: WeeklyPlanView uses max-w-reading-col (not max-w-3xl); CommandCenter + HeatmapGrid use max-w-content-max (not max-w-6xl)', () => {
    const wpv = readFileSync(
      resolve(srcDir, 'features/plan/WeeklyPlanView.tsx'),
      'utf8',
    );
    expect(wpv).toContain('max-w-reading-col');
    expect(wpv).not.toContain('max-w-3xl');

    for (const f of [
      'features/manager/CommandCenter.tsx',
      'features/manager/HeatmapGrid.tsx',
    ]) {
      const code = readFileSync(resolve(srcDir, f), 'utf8');
      expect(code).toContain('max-w-content-max');
      expect(code).not.toContain('max-w-6xl');
    }
  });

  it('no_wc_star_css_or_hardcoded_token_introduced: the touched surfaces use no .wc-* class, import no stylesheet, and introduce no hardcoded hex / arbitrary px·rem·var hatches (forbidden #3, token-native)', () => {
    for (const f of TOUCHED_SURFACES) {
      const code = stripComments(readFileSync(resolve(srcDir, f), 'utf8'));
      expect(code).not.toMatch(/\bwc-[a-z]/);
      expect(code).not.toMatch(/import\s+['"][^'"]*\.css['"]/);
      expect(code).not.toMatch(/#[0-9a-fA-F]{3,8}\b/);
      expect(code).not.toMatch(/-\[[^\]]*(px|rem|var\()/);
    }
  });
});
