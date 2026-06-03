import { Component, type ErrorInfo, type ReactNode } from 'react';
import { ErrorState } from '../shared/components/ErrorState';

interface RouteErrorBoundaryProps {
  children: ReactNode;
}

interface RouteErrorBoundaryState {
  hasError: boolean;
}

/**
 * Catches errors thrown while rendering the lazy route tree — including a
 * rejected dynamic `import()` surfaced past `Suspense` — and renders the shared
 * `ErrorState` instead of a blank screen (§7 view-state contract).
 *
 * The user-facing message is a FIXED, generic string; the raw error is never
 * shown to the user (safety rule #7 — no leak of internal/chunk detail). The
 * raw error is logged as a debug breadcrumb only.
 */
export class RouteErrorBoundary extends Component<
  RouteErrorBoundaryProps,
  RouteErrorBoundaryState
> {
  state: RouteErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): RouteErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // Debug breadcrumb only — never surfaced to the user (safety rule #7).
    console.error('RouteErrorBoundary caught a render error', error, info);
  }

  render(): ReactNode {
    if (this.state.hasError) {
      return (
        <ErrorState message="Something went wrong loading this view. Please refresh to try again." />
      );
    }
    return this.props.children;
  }
}
