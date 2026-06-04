import type { Tone } from './statusTaxonomy';

/**
 * ST.8b — the avatar's pure presentation logic (kept out of the `Avatar.tsx`
 * component file so it stays component-only; the repo keeps pure logic in `.ts`).
 */

/** Up-to-two uppercase initials from a display name. */
export function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase();
}

const TONES: Tone[] = [
  'neutral',
  'info',
  'success',
  'warning',
  'failure',
  'accent',
];

/**
 * A DETERMINISTIC tone from a name (stable per person, so the same report is
 * always the same color). A small string hash → one of the six tone tokens — no
 * hex, no randomness (deterministic for tests + a stable demo).
 */
export function toneForName(name: string): Tone {
  let hash = 0;
  for (let i = 0; i < name.length; i += 1) {
    hash = (hash * 31 + name.charCodeAt(i)) >>> 0;
  }
  return TONES[hash % TONES.length]!;
}
