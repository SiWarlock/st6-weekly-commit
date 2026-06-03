/**
 * PLACEHOLDER route module for `/manager/heatmap` so the lazy chunk resolves.
 * Replaced by the real manager heatmap grid + drilldown in 9.10.
 */
export default function HeatmapPage() {
  return (
    <section data-cy="page-heatmap" className="p-6">
      <h1 className="text-display font-semibold text-ink-primary">
        Alignment heatmap
      </h1>
      <p className="mt-2 text-body text-ink-secondary">
        The manager alignment heatmap is coming in 9.10.
      </p>
    </section>
  );
}
