/**
 * PLACEHOLDER route module for `/manager/command-center` so the lazy chunk
 * resolves. Replaced by the real manager command center (rows + filters +
 * mark-reviewed) in 9.9.
 */
export default function CommandCenterPage() {
  return (
    <section data-cy="page-command-center" className="p-6">
      <h1 className="text-display font-semibold text-ink-primary">
        Command center
      </h1>
      <p className="mt-2 text-body text-ink-secondary">
        The manager command center is coming in 9.9.
      </p>
    </section>
  );
}
