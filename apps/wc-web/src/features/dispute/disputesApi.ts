import { baseApi } from '../../app/baseApi';
import { planTags } from '../../app/tags';
import { parseProblemDetail } from '../../shared/lib/problemDetails';
import type {
  AlignmentDisputeDto,
  OpenDisputeRequest,
  RespondDisputeRequest,
  ResolveDisputeRequest,
} from '../../shared/lib/dtos';

/**
 * `disputesApi` — alignment-dispute mutations (E17 open / E18 respond / E19
 * resolve, §3). Disputes have **no dedicated read path** — they ride the E3/E4
 * plan read (`WeeklyCommitmentDto.dispute`, B.6), so this slice is mutation-only
 * and provides no tags. Each mutation invalidates the parent plan via the shared
 * `planTags(planId)` = `[{plans,id},{plans,'CURRENT'},'manager']` — which refetches
 * the plan + the manager command-center AND heatmap (both ride the `manager` tag,
 * §9; there is no separate `heatmap` tag) so the dispute appears/clears + the
 * `REVIEWED_WITH_DISPUTES↔REVIEWED` review status + counts re-derive. **Success-only**
 * (`error ? [] : tags`, LESSONS §10) — a FAILED mutation invalidates nothing. No
 * optimistic update (invalidate→refetch only). Errors → `safeMessage` via the
 * RFC-7807 parser (§16) — `SECOND_OPEN_DISPUTE`, validation, `IC_CANNOT_RESOLVE_DISPUTE`.
 */
export const disputesApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    openDispute: build.mutation<
      AlignmentDisputeDto,
      { commitmentId: string; planId: string; body: OpenDisputeRequest }
    >({
      query: ({ commitmentId, body }) => ({
        url: `/api/commitments/${commitmentId}/disputes`,
        method: 'POST',
        body,
      }),
      invalidatesTags: (_result, error, { planId }) =>
        error ? [] : planTags(planId),
      transformErrorResponse: (response) => parseProblemDetail(response.data),
    }),
    respondDispute: build.mutation<
      AlignmentDisputeDto,
      { disputeId: string; planId: string; body: RespondDisputeRequest }
    >({
      query: ({ disputeId, body }) => ({
        url: `/api/disputes/${disputeId}/respond`,
        method: 'POST',
        body,
      }),
      invalidatesTags: (_result, error, { planId }) =>
        error ? [] : planTags(planId),
      transformErrorResponse: (response) => parseProblemDetail(response.data),
    }),
    resolveDispute: build.mutation<
      AlignmentDisputeDto,
      { disputeId: string; planId: string; body: ResolveDisputeRequest }
    >({
      query: ({ disputeId, body }) => ({
        url: `/api/disputes/${disputeId}/resolve`,
        method: 'POST',
        body,
      }),
      invalidatesTags: (_result, error, { planId }) =>
        error ? [] : planTags(planId),
      transformErrorResponse: (response) => parseProblemDetail(response.data),
    }),
  }),
});

export const {
  useOpenDisputeMutation,
  useRespondDisputeMutation,
  useResolveDisputeMutation,
} = disputesApi;
