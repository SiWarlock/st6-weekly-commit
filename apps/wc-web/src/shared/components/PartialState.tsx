import { useState, type ReactNode } from 'react';
import { Alert } from 'flowbite-react';
import { HiExclamationCircle } from 'react-icons/hi';

export interface PartialStateWarning {
  /** Parsed `safeMessage` for the non-blocking warning. */
  message: string;
  onRetry?: () => void;
}

export interface PartialStateProps {
  /** The success content — always rendered (the warning never blocks it). */
  children: ReactNode;
  warning: PartialStateWarning;
}

/**
 * Partial view-state (§7) — success content PLUS a dismissible warning `Alert`
 * (warning tone). Canonical case: plan loaded + Outlook sync `FAILED` (the sync
 * failure surfaces but never blocks the core view).
 */
export function PartialState({ children, warning }: PartialStateProps) {
  const [dismissed, setDismissed] = useState(false);

  return (
    <>
      {children}
      {dismissed ? null : (
        <Alert
          color="warning"
          data-cy="partial-warning"
          className="mt-3"
          additionalContent={
            <div className="mt-2 flex items-center gap-3">
              {warning.onRetry ? (
                <button
                  type="button"
                  onClick={warning.onRetry}
                  className="rounded-md border border-border-strong bg-surface-raised px-3 py-1 text-label text-ink-primary hover:bg-surface-hover focus:outline-none focus:ring-2 focus:ring-brand-ring"
                >
                  Retry
                </button>
              ) : null}
              <button
                type="button"
                aria-label="Dismiss warning"
                onClick={() => setDismissed(true)}
                className="rounded-md px-3 py-1 text-label text-ink-secondary hover:text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
              >
                Dismiss
              </button>
            </div>
          }
        >
          <span className="flex items-center gap-2">
            <HiExclamationCircle aria-hidden className="h-5 w-5 flex-none" />
            <span data-cy="partial-message">{warning.message}</span>
          </span>
        </Alert>
      )}
    </>
  );
}
