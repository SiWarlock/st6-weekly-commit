/**
 * ST.8a — pure route→nav-state mapping for the standalone demo app-shell
 * (standalone-only; the production host owns this chrome, so it's tree-shaken from
 * the remote, REQ-I-008). The sub-nav + breadcrumb derive their state from the URL
 * (`useLocation`), never local component state, so the active tab can't drift from
 * the route. No DOM/React here — just the mapping, independently sane + testable.
 */

import { formatWeekRange } from '../../shared/lib/formatWeek';

/**
 * ISO date (YYYY-MM-DD) of the Monday of the LOCAL week containing `today` (Monday-anchored,
 * mirroring the server's org-tz week derivation closely enough for the chrome label).
 */
function weekMondayIso(today: Date): string {
  const offsetToMonday = (today.getDay() + 6) % 7; // 0=Sun → 6, 1=Mon → 0, …
  const monday = new Date(
    today.getFullYear(),
    today.getMonth(),
    today.getDate() - offsetToMonday,
  );
  const month = String(monday.getMonth() + 1).padStart(2, '0');
  const day = String(monday.getDate()).padStart(2, '0');
  return `${monday.getFullYear()}-${month}-${day}`;
}

/**
 * The "Week of …" chrome label for the week containing `today`, derived from the live date so it
 * advances with the real week (the previously-hardcoded label was stuck on one demo week, so the
 * chrome showed a stale week once real time moved on). Uses the shared `formatWeek` — the same
 * formatter the plan card uses — so the chrome's week matches the plan's `weekStartDate` range.
 */
export function currentWeekLabel(today: Date): string {
  return `Week of ${formatWeekRange(weekMondayIso(today), 'explicit')}`;
}

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
