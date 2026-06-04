/**
 * ST.8a — pure route→nav-state mapping for the standalone demo app-shell
 * (standalone-only; the production host owns this chrome, so it's tree-shaken from
 * the remote, REQ-I-008). The sub-nav + breadcrumb derive their state from the URL
 * (`useLocation`), never local component state, so the active tab can't drift from
 * the route. No DOM/React here — just the mapping, independently sane + testable.
 */

/** The single demo week (no multi-week fixtures — the picker is a static affordance). */
export const WEEK_LABEL = 'Week of Jun 1–7, 2026';

/** Top-level surface (mockup `surface`): the IC's own plan vs the manager's team. */
export type NavSurface = 'mine' | 'team';
/** Team sub-surface (mockup `sub`): the command center vs the heatmap. */
export type TeamSub = 'command' | 'heatmap';

/** Route targets the nav drives — the existing AppRoutes paths (no new routes). */
export const ROUTES = {
  weeklyCommit: '/weekly-commit',
  commandCenter: '/manager/command-center',
  heatmap: '/manager/heatmap',
} as const;

/** Whether a path is on the manager/team surface. */
export function surfaceForPath(pathname: string): NavSurface {
  return pathname.startsWith('/manager') ? 'team' : 'mine';
}

/** Which team sub-surface a path is on (the command center is the team default). */
export function subForPath(pathname: string): TeamSub {
  return pathname.startsWith('/manager/heatmap') ? 'heatmap' : 'command';
}
