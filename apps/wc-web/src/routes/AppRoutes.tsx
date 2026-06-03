import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { LoadingState } from '../shared/components/LoadingState';
import { ErrorState } from '../shared/components/ErrorState';
import { RouteErrorBoundary } from './RouteErrorBoundary';
import { useIsManager } from './isManager';
import { useCurrentUser } from '../features/me/useCurrentUser';

// Each route element is its own dynamic import() → a separate code-split chunk,
// so the initial render does not pull the full app (REQ-NF-005, sub-second
// initial-render intent). The four feature routes are 9.x placeholders replaced
// by their real views; the '/' default is a persona-aware redirect (below).
const WeeklyCommitPage = lazy(() => import('./pages/WeeklyCommitPage'));
const PlanHistoryPage = lazy(() => import('./pages/PlanHistoryPage'));
const CommandCenterPage = lazy(() => import('./pages/CommandCenterPage'));
const HeatmapPage = lazy(() => import('./pages/HeatmapPage'));

/**
 * Persona-aware default route (§7). Redirects managers to the command center and
 * ICs to their weekly workspace once the identity is confirmed; renders the
 * shared LoadingState while `getMe` is pending and the shared ErrorState on
 * failure (never a blank screen or a redirect loop). Reads the eager gating
 * query, so the remote requires a host-provided Redux store (9.13 contract).
 */
function RootRedirect() {
  const { isManager, isLoading, isError } = useCurrentUser();
  if (isLoading) {
    return <LoadingState delayMs={0} variant="cards" />;
  }
  if (isError) {
    return (
      <ErrorState message="Something went wrong loading your workspace. Please refresh to try again." />
    );
  }
  return (
    <Navigate
      to={isManager ? '/manager/command-center' : '/weekly-commit'}
      replace
    />
  );
}

/**
 * Lazy-loaded route tree mounted inside the host-provided router. Creates NO
 * `BrowserRouter` of its own (REQ-I-008 — the remote consumes the host router).
 *
 * One `Suspense` renders the shared `LoadingState` while a route chunk loads;
 * one `RouteErrorBoundary` renders the shared `ErrorState` if a chunk fails —
 * never a blank screen (§7 view-state contract). Manager routes are gated by
 * `useIsManager()` so an IC has no `/manager/*` entry point in the UI
 * (REQ-UX-005); an IC hitting a manager URL falls through to the default route.
 */
export function AppRoutes() {
  const isManager = useIsManager();
  return (
    <RouteErrorBoundary>
      <Suspense fallback={<LoadingState delayMs={0} variant="cards" />}>
        <Routes>
          <Route path="/" element={<RootRedirect />} />
          <Route path="/weekly-commit" element={<WeeklyCommitPage />} />
          <Route
            path="/weekly-commit/history/:planId"
            element={<PlanHistoryPage />}
          />
          {isManager && (
            <Route
              path="/manager/command-center"
              element={<CommandCenterPage />}
            />
          )}
          {isManager && (
            <Route path="/manager/heatmap" element={<HeatmapPage />} />
          )}
          {/* Unknown path (incl. a manager URL for an IC) → persona-aware default. */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </RouteErrorBoundary>
  );
}
