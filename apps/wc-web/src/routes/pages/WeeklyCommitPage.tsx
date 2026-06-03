import { WeeklyPlanView } from '../../features/plan/WeeklyPlanView';

/**
 * `/weekly-commit` route module — the IC weekly workspace (9.7). Renders the
 * lazy chunk's `WeeklyPlanView` (replaces the 9.4 placeholder).
 */
export default function WeeklyCommitPage() {
  return <WeeklyPlanView />;
}
