import { render } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { stripComments } from '../../test/util';
import { PriorityTag } from './PriorityTag';
import { WorkTypeTag } from './WorkTypeTag';
import { ConfidenceMeter } from './ConfidenceMeter';
import { AlignmentChip } from './AlignmentChip';

describe('chess atoms — a11y + token-native (no .wc-* CSS)', () => {
  it('atoms_render_glyph_text_color_not_color_alone: each atom carries a non-color indicator AND a text label so meaning survives grayscale/colorblind (REQ-S, LESSONS §7)', () => {
    // WorkType — icon glyph + label.
    const wt = render(<WorkTypeTag value="STRATEGIC" />).container;
    expect(wt.querySelector('svg')).not.toBeNull();
    expect(wt).toHaveTextContent('Strategic');

    // Confidence — segment indicators + label.
    const cf = render(<ConfidenceMeter value="MEDIUM" />).container;
    expect(cf.querySelectorAll('[data-cy="conf-seg"]')).toHaveLength(3);
    expect(cf).toHaveTextContent('Medium');

    // Alignment — dot indicator + label.
    const al = render(<AlignmentChip value="MISALIGNED" />).container;
    expect(al.querySelector('[data-cy="alignment-dot"]')).not.toBeNull();
    expect(al).toHaveTextContent('Misaligned');

    // Priority — the literal P0/P1/P2 text is itself the color-independent signal.
    const pr = render(<PriorityTag value="P0" />).container;
    expect(pr).toHaveTextContent('P0');
  });

  it('no_wc_star_css_introduced: the new atom sources use no .wc-* className and import no stylesheet (forbidden #3 — Tailwind/token classes + Badge only)', () => {
    const here = dirname(fileURLToPath(import.meta.url));
    const sources = [
      'PriorityTag.tsx',
      'WorkTypeTag.tsx',
      'ConfidenceMeter.tsx',
      'AlignmentChip.tsx',
    ];
    for (const f of sources) {
      const code = stripComments(readFileSync(resolve(here, f), 'utf8'));
      // No `.wc-*` class tokens leaked from the Cadence reference CSS.
      expect(code).not.toMatch(/\bwc-[a-z]/);
      // No second stylesheet imported (the one token-var stylesheet is theme.css).
      expect(code).not.toMatch(/import\s+['"][^'"]*\.css['"]/);
    }
  });
});
