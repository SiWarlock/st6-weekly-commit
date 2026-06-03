import { baseApi } from '../../app/baseApi';
import { parseProblemDetail } from '../../shared/lib/problemDetails';
import { planTags } from '../../app/tags';
import type {
  ManagerReviewDto,
  MarkReviewedRequest,
} from '../../shared/lib/dtos';

/**
 * `reviewApi` — manager review actions. `markReviewed` (E16) posts only the
 * optional `summaryNote`; the server DERIVES the `REVIEWED`/`REVIEWED_WITH_DISPUTES`
 * status (never client-supplied, §3). On success it invalidates
 * `[...planTags(planId), 'review']` — the affected plan (per-id + CURRENT) + the
 * `manager` projection tag + the distinct `review` tag (NO `heatmap` tag; the
 * realized `app/tags.ts` folds heatmap into `manager`, §9). The guard
 * `error ? [] : tags` keeps a FAILED mark-reviewed from invalidating (LESSONS §10).
 * No optimistic flip — the view changes only after the refetch resolves.
 */
export const reviewApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    markReviewed: build.mutation<
      ManagerReviewDto,
      { reviewId: string; planId: string; body: MarkReviewedRequest }
    >({
      query: ({ reviewId, body }) => ({
        url: `/api/manager/reviews/${reviewId}/mark-reviewed`,
        method: 'POST',
        body,
      }),
      invalidatesTags: (_result, error, { planId }) =>
        error ? [] : [...planTags(planId), 'review'],
      transformErrorResponse: (response) => parseProblemDetail(response.data),
    }),
  }),
});

export const { useMarkReviewedMutation } = reviewApi;
