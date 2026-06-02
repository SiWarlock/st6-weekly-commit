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
