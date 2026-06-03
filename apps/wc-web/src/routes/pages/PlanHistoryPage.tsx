/**
 * PLACEHOLDER route module for `/weekly-commit/history/:planId` so the lazy chunk
 * resolves. Replaced by the real plan-history / reconciliation view in 9.8.
 */
export default function PlanHistoryPage() {
  return (
    <section data-cy="page-plan-history" className="p-6">
      <h1 className="text-display font-semibold text-ink-primary">
        Plan history
      </h1>
      <p className="mt-2 text-body text-ink-secondary">
        The locked-plan history view is coming in 9.8.
      </p>
    </section>
  );
}
