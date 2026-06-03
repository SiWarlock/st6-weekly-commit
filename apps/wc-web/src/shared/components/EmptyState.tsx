import type { ReactNode } from 'react';
import type { IconType } from 'react-icons';
import { HiInformationCircle } from 'react-icons/hi';

export interface EmptyStateProps {
  icon?: IconType;
  title: string;
  message?: string;
  /** Optional CTA (e.g. a "Create plan" button). */
  action?: ReactNode;
}

/** Empty view-state (§7) — icon + headline + one-line guidance + optional CTA. */
export function EmptyState({
  icon: Icon = HiInformationCircle,
  title,
  message,
  action,
}: EmptyStateProps) {
  return (
    <div
      data-cy="empty-state"
      className="flex flex-col items-center gap-2 px-6 py-12 text-center"
    >
      <Icon aria-hidden className="h-8 w-8 text-ink-muted" />
      <h3 className="text-h3 font-semibold text-ink-primary">{title}</h3>
      {message ? (
        <p className="text-body text-ink-secondary">{message}</p>
      ) : null}
      {action ? <div className="mt-2">{action}</div> : null}
    </div>
  );
}
