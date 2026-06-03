import { baseApi } from '../../app/baseApi';
import { parseProblemDetail } from '../../shared/lib/problemDetails';
import type { TagType } from '../../app/tags';
import type {
  WeeklyCommitmentDto,
  CreateCommitmentRequest,
  CreateUnplannedCommitmentRequest,
  PatchCommitmentRequest,
} from '../../shared/lib/dtos';

/**
 * Tags every commitment mutation invalidates: the affected plan (per-id) + the
 * current-plan sentinel + the manager projections. Per §9 the command-center
 * (`manager_plan_summary`) and heatmap (`manager_heatmap_cell`) projections
 * always co-change on the same triggers, so a single `manager` tag covers both
 * (no `heatmap` tag). Each mutation guards `error ? [] : planTags(...)` so a
 * FAILED mutation invalidates nothing (RTK Query otherwise applies the callback's
 * returned tags on error too) — the plan view never refetches on a 4xx/5xx.
 */
function planTags(planId: string): (TagType | { type: TagType; id: string })[] {
  return [
    { type: 'plans', id: planId },
    { type: 'plans', id: 'CURRENT' },
    // General `manager` tag (no id) → invalidates ALL manager queries regardless
    // of the id they provide (forward-compatible: a no-op until 9.9/9.10 add the
    // command-center/heatmap queries, which §9 says always co-change with these).
    'manager',
  ];
}

/**
 * `commitmentsApi` — the mutation (invalidatesTags) side of the cache-invalidation
 * contract. Every mutation refetches the affected plan via tag invalidation —
 * **no optimistic updates** (no `onQueryStarted`/`updateQueryData`); the view
 * changes only after the refetch resolves (§7). RFC-7807 errors (e.g.
 * `409 LOCKED_BASELINE_EDIT`) surface via the parsed `safeMessage` (B.21).
 */
export const commitmentsApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    createCommitment: build.mutation<
      WeeklyCommitmentDto,
      { planId: string; body: CreateCommitmentRequest }
    >({
      query: ({ planId, body }) => ({
        url: `/api/plans/${planId}/commitments`,
        method: 'POST',
        body,
      }),
      invalidatesTags: (_result, error, { planId }) =>
        error ? [] : planTags(planId),
      transformErrorResponse: (response) => parseProblemDetail(response.data),
    }),
    addUnplannedCommitment: build.mutation<
      WeeklyCommitmentDto,
      { planId: string; body: CreateUnplannedCommitmentRequest }
    >({
      query: ({ planId, body }) => ({
        url: `/api/plans/${planId}/unplanned-commitments`,
        method: 'POST',
        body,
      }),
      invalidatesTags: (_result, error, { planId }) =>
        error ? [] : planTags(planId),
      transformErrorResponse: (response) => parseProblemDetail(response.data),
    }),
    updateCommitment: build.mutation<
      WeeklyCommitmentDto,
      { id: string; planId: string; patch: PatchCommitmentRequest }
    >({
      query: ({ id, patch }) => ({
        url: `/api/commitments/${id}`,
        method: 'PATCH',
        body: patch,
      }),
      invalidatesTags: (_result, error, { planId }) =>
        error ? [] : planTags(planId),
      transformErrorResponse: (response) => parseProblemDetail(response.data),
    }),
    deleteCommitment: build.mutation<void, { id: string; planId: string }>({
      query: ({ id }) => ({
        url: `/api/commitments/${id}`,
        method: 'DELETE',
      }),
      invalidatesTags: (_result, error, { planId }) =>
        error ? [] : planTags(planId),
      transformErrorResponse: (response) => parseProblemDetail(response.data),
    }),
  }),
});

export const {
  useCreateCommitmentMutation,
  useAddUnplannedCommitmentMutation,
  useUpdateCommitmentMutation,
  useDeleteCommitmentMutation,
} = commitmentsApi;
