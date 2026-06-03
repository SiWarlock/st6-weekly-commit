import { RISK_TAXONOMY } from '../lib/statusTaxonomy';
import { Badge } from './Badge';

export interface RiskBadgeProps {
  /** A `RiskBadge` wire value (B.1). Unknown/absent renders nothing (no throw). */
  value: string;
  size?: 'xs' | 'sm';
}

/**
 * Renders ONLY the six B.1 RiskBadge values per the §4.2 map; `BLOCKED` and
 * `CARRY_FORWARD` (the 2nd of each same-color pair) use the ring variant.
 */
export function RiskBadge({ value, size = 'xs' }: RiskBadgeProps) {
  const entry = RISK_TAXONOMY[value];
  if (!entry) {
    return null;
  }
  return (
    <Badge
      tone={entry.tone}
      icon={entry.icon}
      label={entry.label}
      ring={entry.ring ?? false}
      size={size}
      dataCy="risk-badge"
    />
  );
}
