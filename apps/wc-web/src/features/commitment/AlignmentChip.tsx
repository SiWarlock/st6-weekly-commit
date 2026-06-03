import { ALIGNMENT_TAXONOMY, type Tone } from '../../shared/lib/statusTaxonomy';
import type { AlignmentStatus } from '../../shared/lib/dtos';

// Full literal token-utility strings so Tailwind's JIT includes them.
const DOT_CLASSES: Record<Tone, string> = {
  neutral: 'bg-tone-neutral-solid',
  info: 'bg-tone-info-solid',
  success: 'bg-tone-success-solid',
  warning: 'bg-tone-warning-solid',
  failure: 'bg-tone-failure-solid',
  accent: 'bg-tone-accent-solid',
};

const TEXT_CLASSES: Record<Tone, string> = {
  neutral: 'text-tone-neutral-fg',
  info: 'text-tone-info-fg',
  success: 'text-tone-success-fg',
  warning: 'text-tone-warning-fg',
  failure: 'text-tone-failure-fg',
  accent: 'text-tone-accent-fg',
};

/**
 * Alignment chess atom — a tone dot + label (Cadence ALIGNMENT map: ALIGNED
 * success, NEEDS_REVIEW warning, MISALIGNED failure). The dot is the indicator,
 * the label the text (never color alone). Unknown value → renders nothing
 * (LESSONS §7). Token-native (no `.wc-*` CSS).
 */
export function AlignmentChip({ value }: { value: AlignmentStatus }) {
  const entry = ALIGNMENT_TAXONOMY[value];
  if (!entry) {
    return null;
  }
  return (
    <span
      data-cy="alignment-chip"
      data-tone={entry.tone}
      className={`inline-flex items-center gap-1.5 text-label font-medium ${TEXT_CLASSES[entry.tone]}`}
    >
      <span
        data-cy="alignment-dot"
        aria-hidden
        className={`h-2 w-2 flex-none rounded-full ${DOT_CLASSES[entry.tone]}`}
      />
      {entry.label}
    </span>
  );
}
