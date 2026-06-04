import { HiExclamationCircle } from 'react-icons/hi';
import type { RcdoBreadcrumbDto } from '../../shared/lib/dtos';

/**
 * Read-only RC→DO→SO breadcrumb on a commitment card (§5). Renders **DO › SO**
 * (the org-wide Rally Cry is intentionally omitted from the per-card chip — it's
 * constant for everyone → noise; Cadence `atoms.jsx:142-149`). The SO **title**
 * is emphasized (our `supportingOutcomeId` is a UUID, so the id is never shown).
 * Titles render React-escaped. When unlinked, renders the missing-SO warning
 * (rule #1 → only pre-lock: DRAFT / unplanned). Token-native (no `.wc-*` CSS).
 */
export function RcdoBreadcrumb({
  breadcrumb,
}: {
  // Explicit `| undefined` so a `RcdoBreadcrumbDto | undefined` source (the
  // commitment's optional breadcrumb) is assignable under exactOptionalPropertyTypes.
  breadcrumb?: RcdoBreadcrumbDto | undefined;
}) {
  if (!breadcrumb) {
    return (
      <div
        data-cy="rcdo-breadcrumb-missing"
        className="inline-flex items-center gap-1.5 rounded-md border border-tone-warning-border bg-tone-warning-bg px-2 py-1 text-meta text-tone-warning-fg"
      >
        <HiExclamationCircle aria-hidden className="h-3.5 w-3.5 flex-none" />
        <span>No Supporting Outcome linked</span>
      </div>
    );
  }
  return (
    <div
      data-cy="rcdo-breadcrumb"
      className="inline-flex flex-wrap items-center gap-1 rounded-md border border-border bg-surface-sunken px-2 py-1 text-meta text-ink-secondary"
    >
      <span>{breadcrumb.definingObjectiveTitle}</span>
      <span aria-hidden className="text-ink-muted">
        ›
      </span>
      <span data-cy="rcdo-so" className="font-medium text-ink-primary">
        {breadcrumb.supportingOutcomeTitle}
      </span>
    </div>
  );
}
