import type { IconType } from 'react-icons';
import type { Tone } from '../lib/statusTaxonomy';

// Static token-utility maps (full literal strings so Tailwind's JIT includes
// them). Solid = translucent fill + bright fg + low-alpha colored border;
// ring = transparent fill + inset ring (distinguishes the 2nd same-color pair).
const SOLID_CLASSES: Record<Tone, string> = {
  neutral:
    'border bg-tone-neutral-bg text-tone-neutral-fg border-tone-neutral-border',
  info: 'border bg-tone-info-bg text-tone-info-fg border-tone-info-border',
  success:
    'border bg-tone-success-bg text-tone-success-fg border-tone-success-border',
  warning:
    'border bg-tone-warning-bg text-tone-warning-fg border-tone-warning-border',
  failure:
    'border bg-tone-failure-bg text-tone-failure-fg border-tone-failure-border',
  accent:
    'border bg-tone-accent-bg text-tone-accent-fg border-tone-accent-border',
};

const RING_CLASSES: Record<Tone, string> = {
  neutral:
    'border border-transparent bg-transparent text-tone-neutral-fg ring-1 ring-inset ring-tone-neutral-border',
  info: 'border border-transparent bg-transparent text-tone-info-fg ring-1 ring-inset ring-tone-info-border',
  success:
    'border border-transparent bg-transparent text-tone-success-fg ring-1 ring-inset ring-tone-success-border',
  warning:
    'border border-transparent bg-transparent text-tone-warning-fg ring-1 ring-inset ring-tone-warning-border',
  failure:
    'border border-transparent bg-transparent text-tone-failure-fg ring-1 ring-inset ring-tone-failure-border',
  accent:
    'border border-transparent bg-transparent text-tone-accent-fg ring-1 ring-inset ring-tone-accent-border',
};

export interface BadgeProps {
  tone: Tone;
  icon: IconType;
  label: string;
  ring?: boolean;
  size?: 'xs' | 'sm';
  dataCy?: string;
}

/**
 * Token-driven status/risk badge atom (approach A — Tailwind utilities, no
 * `.wc-*` CSS). Always carries glyph + text + color so meaning survives
 * grayscale/colorblind (REQ-S-005); `data-tone`/`data-ring` are stable test hooks.
 */
export function Badge({
  tone,
  icon: Icon,
  label,
  ring = false,
  size = 'sm',
  dataCy,
}: BadgeProps) {
  const sizeClasses =
    size === 'xs'
      ? 'gap-1 px-1.5 py-0.5 text-[11px]'
      : 'gap-1 px-2 py-0.5 text-label';
  const iconSize = size === 'xs' ? 'h-3 w-3' : 'h-3.5 w-3.5';

  return (
    <span
      data-cy={dataCy}
      data-tone={tone}
      data-ring={ring ? 'true' : undefined}
      className={`inline-flex items-center whitespace-nowrap rounded-md font-medium ${sizeClasses} ${ring ? RING_CLASSES[tone] : SOLID_CLASSES[tone]}`}
    >
      <Icon aria-hidden className={`${iconSize} flex-none`} />
      <span>{label}</span>
    </span>
  );
}
