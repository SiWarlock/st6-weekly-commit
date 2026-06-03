import { Badge } from '../../shared/components/Badge';
import { WORKTYPE_TAXONOMY } from '../../shared/lib/statusTaxonomy';
import type { WorkType } from '../../shared/lib/dtos';

/**
 * WorkType chess atom — the Cadence WORKTYPE map (icon+label+tone) rendered via
 * the shared `Badge` primitive (glyph+text+color), exactly like `RiskBadge`.
 * Unknown value → renders nothing (LESSONS §7).
 */
export function WorkTypeTag({ value }: { value: WorkType }) {
  const entry = WORKTYPE_TAXONOMY[value];
  if (!entry) {
    return null;
  }
  return (
    <Badge
      tone={entry.tone}
      icon={entry.icon}
      label={entry.label}
      size="xs"
      dataCy="worktype-tag"
    />
  );
}
