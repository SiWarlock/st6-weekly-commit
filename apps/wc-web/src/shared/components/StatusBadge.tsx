import {
  PLAN_STATE_TAXONOMY,
  REVIEW_STATUS_TAXONOMY,
} from '../lib/statusTaxonomy';
import { Badge } from './Badge';

export interface StatusBadgeProps {
  /** Which enum family to render. */
  kind: 'plan' | 'review';
  /** The wire enum value (B.1) — rendered verbatim via the §4.2 taxonomy. */
  value: string;
  /**
   * Review-only: render the derived OVERDUE overlay. OVERDUE is read-time
   * derived (now > reviewDueAt AND NOT_REVIEWED, §3) — never a stored status.
   */
  derivedOverdue?: boolean;
  size?: 'xs' | 'sm';
}

/**
 * Renders a `PlanState` or `ReviewStatus` value per the §4.2 tone+icon map.
 * Unknown/absent value renders nothing (no throw).
 */
export function StatusBadge({
  kind,
  value,
  derivedOverdue = false,
  size = 'sm',
}: StatusBadgeProps) {
  const entry =
    kind === 'review' && derivedOverdue
      ? REVIEW_STATUS_TAXONOMY.OVERDUE
      : kind === 'plan'
        ? PLAN_STATE_TAXONOMY[value]
        : REVIEW_STATUS_TAXONOMY[value];

  if (!entry) {
    return null;
  }

  return (
    <Badge
      tone={entry.tone}
      icon={entry.icon}
      label={entry.label}
      size={size}
      dataCy="status-badge"
    />
  );
}
