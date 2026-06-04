import type { Tone } from '../lib/statusTaxonomy';
import { initialsOf, toneForName } from '../lib/avatar';

const TONE_AVATAR: Record<Tone, string> = {
  neutral: 'bg-tone-neutral-bg text-tone-neutral-fg',
  info: 'bg-tone-info-bg text-tone-info-fg',
  success: 'bg-tone-success-bg text-tone-success-fg',
  warning: 'bg-tone-warning-bg text-tone-warning-fg',
  failure: 'bg-tone-failure-bg text-tone-failure-fg',
  accent: 'bg-tone-accent-bg text-tone-accent-fg',
};

const SIZE: Record<'sm' | 'md', string> = {
  sm: 'h-6 w-6 text-meta',
  md: 'h-8 w-8 text-label',
};

/**
 * Initials avatar (ST.8b) — a colored circle with the person's initials, the
 * tone derived deterministically from the name (see `lib/avatar`). Token-native
 * (tone tokens, no hex). Reused by the command-center rows + the standalone
 * identity slot.
 */
export function Avatar({
  name,
  size = 'sm',
}: {
  name: string;
  size?: 'sm' | 'md';
}) {
  const tone = toneForName(name);
  return (
    <span
      aria-hidden
      data-tone={tone}
      className={`inline-flex flex-none items-center justify-center rounded-full font-semibold ${SIZE[size]} ${TONE_AVATAR[tone]}`}
    >
      {initialsOf(name)}
    </span>
  );
}
