import { useLocation, useNavigate } from 'react-router-dom';
import { HiCalendar } from 'react-icons/hi';
import { useIsManager } from '../../routes/isManager';
import {
  ROUTES,
  currentWeekLabel,
  surfaceForPath,
  subForPath,
} from './navModel';

/**
 * ST.8a — the WC sub-nav (standalone demo chrome; tree-shaken from the remote,
 * REQ-I-008). Route-driven: the visible segments are persona-gated by the existing
 * `useIsManager`, and the active tab derives from `useLocation` (not local state)
 * so it can't drift from the URL. Tabs `navigate()` to the existing AppRoutes
 * paths — no new routes. A static week-picker affordance sits to the right (single
 * demo week — no functional range change; the CC week pager is a separate ST.8b item).
 */
function SegButton({
  label,
  active,
  onClick,
}: {
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-current={active ? 'page' : undefined}
      className={`rounded-sm px-3 py-1 text-label transition-colors duration-base ${
        active
          ? 'bg-surface-raised text-ink-primary shadow-hairline'
          : 'text-ink-secondary hover:text-ink-primary'
      }`}
    >
      {label}
    </button>
  );
}

export function PrimaryNav() {
  const isManager = useIsManager();
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const surface = surfaceForPath(pathname);
  const sub = subForPath(pathname);

  return (
    <nav
      aria-label="Primary"
      className="flex items-center gap-3 border-b border-border bg-surface px-6 py-2"
    >
      {isManager ? (
        <div className="inline-flex items-center gap-1 rounded-md bg-surface-sunken p-1">
          <SegButton
            label="My Weekly Commit"
            active={surface === 'mine'}
            onClick={() => navigate(ROUTES.weeklyCommit)}
          />
          <SegButton
            label="My Team"
            active={surface === 'team'}
            onClick={() => navigate(ROUTES.commandCenter)}
          />
        </div>
      ) : (
        <span className="px-3 py-1 text-label font-medium text-ink-primary">
          My Weekly Commit
        </span>
      )}

      {isManager && surface === 'team' && (
        <div className="inline-flex items-center gap-1 rounded-md bg-surface-sunken p-1">
          <SegButton
            label="Command Center"
            active={sub === 'command'}
            onClick={() => navigate(ROUTES.commandCenter)}
          />
          <SegButton
            label="Heatmap"
            active={sub === 'heatmap'}
            onClick={() => navigate(ROUTES.heatmap)}
          />
        </div>
      )}

      {/* Static week INDICATOR (not a control) — this demo is a single week, so
          there's no week navigation. Rendered as a non-interactive labeled chip
          (no chevron, no button semantics) + a tooltip so it doesn't read as a
          live dropdown. The real multi-week pager is a separate ST.8b item. */}
      <span
        title="Current week"
        aria-label="Current week"
        className="ml-auto inline-flex items-center gap-1 rounded-md border border-border px-3 py-1 text-label text-ink-secondary"
      >
        <HiCalendar aria-hidden className="h-4 w-4" />
        {currentWeekLabel(new Date())}
      </span>
    </nav>
  );
}
