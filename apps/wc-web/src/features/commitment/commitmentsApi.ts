import { baseApi } from '../../app/baseApi';
import { parseProblemDetail } from '../../shared/lib/problemDetails';
import { planTags } from '../../app/tags';
import type {
  WeeklyCommitmentDto,
  CreateCommitmentRequest,
  CreateUnplannedCommitmentRequest,
  PatchCommitmentRequest,
} from '../../shared/lib/dtos';

/**
 * Every commitment mutation guards `error ? [] : planTags(...)` (shared helper in
 * app/tags.ts) so a FAILED mutation invalidates nothing — LESSONS §10.
 *
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
    /**
     * E12 carry-forward (the ONLY path that sets `CARRIED_FORWARD`, §3 — a direct
     * PATCH of that outcome is server-rejected). No body; arg `{id, planId}` where
     * `planId` is the SOURCE plan (the invalidation key). Returns the next-week
     * successor `WeeklyCommitmentDto`; success invalidates the source plan tag so
     * the row reflects the carried-forward state on refetch. Idempotent server-side
     * (a re-invoke returns the existing successor). No optimistic write.
     */
    carryForward: build.mutation<
      WeeklyCommitmentDto,
      { id: string; planId: string }
    >({
      query: ({ id }) => ({
        url: `/api/commitments/${id}/carry-forward`,
        method: 'POST',
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
  useCarryForwardMutation,
} = commitmentsApi;
