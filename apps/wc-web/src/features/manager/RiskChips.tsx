import { Badge } from '../../shared/components/Badge';
import { CC_RISK_CHIP_TAXONOMY } from '../../shared/lib/statusTaxonomy';
import type { ManagerCommandCenterRowDto } from '../../shared/lib/dtos';

/**
 * ST.8b — the per-report risk chips (§B.3): the always-shown `N P` / `N U`
 * planned/unplanned count pills + the labeled, HIDE-ZERO risk chips. Tone/icon/
 * label come from `CC_RISK_CHIP_TAXONOMY` (the single visual-truth map; never
 * re-mapped inline, §7/LESSONS-7). B.11 carries the 5 renderable counts
 * (misaligned/needs-review/blocked/carry-fwd/dispute); `resolved`/`unlinked` have
 * no DTO field yet (a flagged backend follow-up), so they don't render.
 */
const RISK_CHIP_FIELDS: {
  kind: string;
  key: keyof ManagerCommandCenterRowDto;
}[] = [
  { kind: 'misaligned', key: 'misalignedCount' },
  { kind: 'needsReview', key: 'needsReviewCount' },
  { kind: 'blocked', key: 'blockedCount' },
  { kind: 'carryForward', key: 'carryForwardCount' },
  { kind: 'dispute', key: 'unresolvedDisputeCount' },
];

/** Planned/unplanned count pill — always shown (the §B.3 P/U legend keys). */
function CountPill({ n, letter }: { n: number; letter: 'P' | 'U' }) {
  return (
    <span className="inline-flex items-center rounded-md border border-border bg-surface px-1.5 py-0.5 font-mono text-meta text-ink-secondary">
      {`${n} ${letter}`}
    </span>
  );
}

export function RiskChips({ row }: { row: ManagerCommandCenterRowDto }) {
  return (
    <div data-cy="risk-chips" className="flex flex-wrap items-center gap-1.5">
      <CountPill n={row.plannedCount} letter="P" />
      <CountPill n={row.unplannedCount} letter="U" />
      {RISK_CHIP_FIELDS.map(({ kind, key }) => {
        const n = row[key] as number;
        const entry = CC_RISK_CHIP_TAXONOMY[kind];
        if (n <= 0 || !entry) {
          return null;
        }
        return (
          <Badge
            key={kind}
            tone={entry.tone}
            icon={entry.icon}
            label={`${n} ${entry.label}`}
            ring={entry.ring ?? false}
            size="xs"
            dataCy={`risk-${kind}`}
          />
        );
      })}
    </div>
  );
}
