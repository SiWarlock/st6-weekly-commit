import { PRIORITY_TAXONOMY, type Tone } from '../../shared/lib/statusTaxonomy';
import type { Priority } from '../../shared/lib/dtos';

// Full literal token-utility strings so Tailwind's JIT includes them.
const TONE_CLASSES: Record<Tone, string> = {
  neutral: 'bg-tone-neutral-bg text-tone-neutral-fg border-tone-neutral-border',
  info: 'bg-tone-info-bg text-tone-info-fg border-tone-info-border',
  success: 'bg-tone-success-bg text-tone-success-fg border-tone-success-border',
  warning: 'bg-tone-warning-bg text-tone-warning-fg border-tone-warning-border',
  failure: 'bg-tone-failure-bg text-tone-failure-fg border-tone-failure-border',
  accent: 'bg-tone-accent-bg text-tone-accent-fg border-tone-accent-border',
};

/**
 * Priority chess atom — the literal `P0`/`P1`/`P2` in a tone-skinned mono tag
 * (descending urgency: P0 failure → P1 warning → P2 neutral). The literal text
 * IS the color-independent signal (no icon). Unknown value → renders nothing
 * (LESSONS §7). Token-native (no `.wc-*` CSS).
 */
export function PriorityTag({ value }: { value: Priority }) {
  const entry = PRIORITY_TAXONOMY[value];
  if (!entry) {
    return null;
  }
  return (
    <span
      data-cy="priority-tag"
      data-tone={entry.tone}
      className={`inline-flex items-center rounded-md border px-1.5 py-0.5 font-mono text-[11px] font-medium ${TONE_CLASSES[entry.tone]}`}
    >
      {entry.label}
    </span>
  );
}
