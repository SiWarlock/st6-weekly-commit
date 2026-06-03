import { Badge } from '../../shared/components/Badge';
import { RECONCILIATION_OUTCOME_TAXONOMY } from '../../shared/lib/statusTaxonomy';
import type { ReconciliationOutcome } from '../../shared/lib/dtos';

/**
 * Reconciliation-outcome pill (§3) — the Cadence OUTCOME map (icon+label+tone)
 * rendered via the shared `Badge` (glyph+text+color), like `WorkTypeTag`. Shown
 * on the read-only RECONCILED card. Unknown value → renders nothing (LESSONS §7).
 */
export function OutcomePill({ value }: { value: ReconciliationOutcome }) {
  const entry = RECONCILIATION_OUTCOME_TAXONOMY[value];
  if (!entry) {
    return null;
  }
  return (
    <Badge
      tone={entry.tone}
      icon={entry.icon}
      label={entry.label}
      size="xs"
      dataCy="outcome-pill"
    />
  );
}
