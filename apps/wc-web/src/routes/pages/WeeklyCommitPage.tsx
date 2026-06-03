/**
 * PLACEHOLDER route module for `/weekly-commit` so the lazy chunk resolves.
 * Replaced by the real IC workspace (WeeklyPlanView + CommitmentForm + …) in 9.7.
 */
export default function WeeklyCommitPage() {
  return (
    <section data-cy="page-weekly-commit" className="p-6">
      <h1 className="text-display font-semibold text-ink-primary">
        Weekly workspace
      </h1>
      <p className="mt-2 text-body text-ink-secondary">
        The IC weekly-commit workspace is coming in 9.7.
      </p>
    </section>
  );
}
