/**
 * RTK Query cache tag types — one per API domain (§7). Mutations in later slices
 * invalidate against these so dependent queries refetch. Keep in sync with the
 * domain api slices added in 9.5+.
 */
export const TAG_TYPES = [
  'me',
  'rcdo',
  'plans',
  'commitments',
  'review',
  'disputes',
  'manager',
  'comments',
  'sync',
] as const;

export type TagType = (typeof TAG_TYPES)[number];

/**
 * The tags a plan-affecting mutation (commitment CRUD, lock, reconciliation —
 * 9.6/9.7/9.8) invalidates: the affected plan (per-id) + the current-plan
 * sentinel + the general `manager` tag (§9: the command-center + heatmap
 * projections co-change on the same triggers, so one tag covers both). Callers
 * guard `error ? [] : planTags(id)` so a FAILED mutation invalidates nothing
 * (RTK Query otherwise applies the callback's tags on error too) — LESSONS §10.
 */
export function planTags(
  planId: string,
): (TagType | { type: TagType; id: string })[] {
  return [
    { type: 'plans', id: planId },
    { type: 'plans', id: 'CURRENT' },
    'manager',
  ];
}
