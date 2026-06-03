import { HeatmapGrid } from '../../features/manager/HeatmapGrid';

/**
 * `/manager/heatmap` route module — the manager alignment heatmap grid +
 * per-cell drilldown (9.10). Renders the real `HeatmapGrid` (replaces the 9.4
 * placeholder). The route is manager-gated in `AppRoutes` (REQ-UX-005).
 */
export default function HeatmapPage() {
  return <HeatmapGrid />;
}
