import { Alert } from 'flowbite-react';
import { HiExclamationCircle } from 'react-icons/hi';

export interface ErrorStateProps {
  /** The parsed RFC-7807 `safeMessage` (B.21) — rendered as plain escaped text. */
  message: string;
  traceId?: string;
  onRetry?: () => void;
}

/**
 * Error view-state (§7) — a Cadence-skinned Flowbite `Alert` (failure tone)
 * rendering `safeMessage` as plain, Cypress-assertable text. NEVER
 * `dangerouslySetInnerHTML`: React default-escaping makes any payload inert
 * (REQ-S-005 / forbidden #4).
 */
export function ErrorState({ message, traceId, onRetry }: ErrorStateProps) {
  return (
    <Alert
      color="failure"
      data-cy="error-state"
      additionalContent={
        traceId !== undefined || onRetry ? (
          <div className="mt-2 flex items-center gap-3">
            {onRetry ? (
              <button
                type="button"
                onClick={onRetry}
                className="rounded-md border border-border-strong bg-surface-raised px-3 py-1 text-label text-ink-primary hover:bg-surface-hover focus:outline-none focus:ring-2 focus:ring-brand-ring"
              >
                Retry
              </button>
            ) : null}
            {traceId !== undefined ? (
              <span className="font-mono text-meta text-ink-muted">
                Trace: {traceId}
              </span>
            ) : null}
          </div>
        ) : undefined
      }
    >
      <span className="flex items-center gap-2">
        <HiExclamationCircle aria-hidden className="h-5 w-5 flex-none" />
        <span data-cy="error-message">{message}</span>
      </span>
    </Alert>
  );
}
