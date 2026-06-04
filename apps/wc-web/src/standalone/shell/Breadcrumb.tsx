import { useLocation } from 'react-router-dom';
import { HiChevronRight } from 'react-icons/hi';
import { WEEK_LABEL, surfaceForPath, subForPath } from './navModel';

/**
 * ST.8a — route-driven breadcrumb (standalone demo chrome). Derives entirely from
 * `useLocation` (not local state): "My Team › Command Center" / "My Team › Heatmap"
 * / "My Weekly Commit › <week>".
 */
export function Breadcrumb() {
  const { pathname } = useLocation();
  const onTeam = surfaceForPath(pathname) === 'team';
  const [root, leaf] = onTeam
    ? ['My Team', subForPath(pathname) === 'heatmap' ? 'Heatmap' : 'Command Center']
    : ['My Weekly Commit', WEEK_LABEL];

  return (
    <nav
      aria-label="Breadcrumb"
      data-testid="breadcrumb"
      className="flex items-center gap-1 border-b border-border bg-surface-app px-6 py-2 text-meta text-ink-muted"
    >
      <span>{root}</span>
      <HiChevronRight aria-hidden className="h-3 w-3" />
      <span className="text-ink-secondary">{leaf}</span>
    </nav>
  );
}
