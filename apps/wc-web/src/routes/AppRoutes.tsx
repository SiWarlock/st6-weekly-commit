import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { LoadingState } from '../shared/components/LoadingState';
import { RouteErrorBoundary } from './RouteErrorBoundary';
import { useIsManager } from './isManager';

// Each route element is its own dynamic import() → a separate code-split chunk,
// so the initial render does not pull the full app (REQ-NF-005, sub-second
// initial-render intent). The '/' default reuses the existing App shell as the
// interim persona-aware landing (the real role-based redirect lands in 9.5);
// the other four are 9.x placeholders replaced by their real views.
const HomePage = lazy(() => import('../App'));
const WeeklyCommitPage = lazy(() => import('./pages/WeeklyCommitPage'));
const PlanHistoryPage = lazy(() => import('./pages/PlanHistoryPage'));
const CommandCenterPage = lazy(() => import('./pages/CommandCenterPage'));
const HeatmapPage = lazy(() => import('./pages/HeatmapPage'));

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
          <Route path="/" element={<HomePage />} />
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
