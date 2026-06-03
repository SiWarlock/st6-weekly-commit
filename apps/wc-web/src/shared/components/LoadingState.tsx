import { useEffect, useState } from 'react';

export type LoadingVariant = 'table' | 'cards' | 'grid' | 'inline';

export interface LoadingStateProps {
  variant?: LoadingVariant;
  /** Delay before showing the skeleton (avoids a flash on fast loads). */
  delayMs?: number;
  label?: string;
}

const VARIANT_SKELETON: Record<LoadingVariant, string> = {
  table: 'h-44 w-full',
  cards: 'h-32 w-full',
  grid: 'h-24 w-full',
  inline: 'h-5 w-40',
};

/**
 * Loading view-state (§7). Renders a token-skinned `animate-pulse` skeleton
 * after a short delay; reduced-motion is honored declaratively in `theme.css`
 * (ST.2), so no per-component motion logic is needed.
 */
export function LoadingState({
  variant = 'inline',
  delayMs = 150,
  label = 'Loading…',
}: LoadingStateProps) {
  const [show, setShow] = useState(delayMs === 0);

  useEffect(() => {
    if (delayMs === 0) {
      return;
    }
    const timer = setTimeout(() => setShow(true), delayMs);
    return () => clearTimeout(timer);
  }, [delayMs]);

  if (!show) {
    return null;
  }

  return (
    <div
      role="status"
      aria-live="polite"
      aria-busy="true"
      data-cy="loading-state"
      data-variant={variant}
    >
      <span className="sr-only">{label}</span>
      <div
        aria-hidden
        className={`animate-pulse rounded-lg bg-surface-hover ${VARIANT_SKELETON[variant]}`}
      />
    </div>
  );
}
