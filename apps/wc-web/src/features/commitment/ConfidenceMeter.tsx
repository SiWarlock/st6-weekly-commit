import {
  CONFIDENCE_TAXONOMY,
  type Tone,
} from '../../shared/lib/statusTaxonomy';
import type { Confidence } from '../../shared/lib/dtos';

// Full literal token-utility strings so Tailwind's JIT includes them.
const FILL_CLASSES: Record<Tone, string> = {
  neutral: 'bg-tone-neutral-solid',
  info: 'bg-tone-info-solid',
  success: 'bg-tone-success-solid',
  warning: 'bg-tone-warning-solid',
  failure: 'bg-tone-failure-solid',
  accent: 'bg-tone-accent-solid',
};

const SEGMENTS = [0, 1, 2];

/**
 * Confidence chess atom — a 3-segment meter (HIGH=3 filled, MEDIUM=2, LOW=1) +
 * a capitalized label + an accessible `title`; tone scales with level. The
 * filled segments are the indicator, the label the text (never color alone).
 * Unknown value → renders nothing (LESSONS §7). Token-native (no `.wc-*` CSS).
 */
export function ConfidenceMeter({ value }: { value: Confidence }) {
  const entry = CONFIDENCE_TAXONOMY[value];
  if (!entry) {
    return null;
  }
  return (
    <span
      data-cy="confidence-meter"
      data-tone={entry.tone}
      title={`Confidence: ${entry.label}`}
      className="inline-flex items-center gap-1.5"
    >
      <span className="inline-flex gap-0.5" aria-hidden>
        {SEGMENTS.map((i) => {
          const filled = i < entry.segments;
          return (
            <span
              key={i}
              data-cy="conf-seg"
              data-filled={filled ? 'true' : 'false'}
              // Empty segments carry a ring outline (ST.7c) so the 3-slot
              // structure always reads "N of 3" — fixes the near-invisible LOW
              // (1-filled) meter without changing the §7 tone (LOW stays neutral).
              className={`h-3 w-1.5 rounded-sm ${filled ? FILL_CLASSES[entry.tone] : 'bg-surface-hover ring-1 ring-border'}`}
            />
          );
        })}
      </span>
      <span className="text-meta text-ink-secondary">{entry.label}</span>
    </span>
  );
}
