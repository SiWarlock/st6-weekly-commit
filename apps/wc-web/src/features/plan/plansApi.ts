import { baseApi } from '../../app/baseApi';
import { parseProblemDetail } from '../../shared/lib/problemDetails';
import { planTags } from '../../app/tags';
import type { WeeklyPlanDto } from '../../shared/lib/dtos';

/**
 * `plansApi` — the read (providesTags) side of the plan cache-invalidation
 * contract. Tags are **per-id** so a commitment mutation invalidates exactly the
 * affected plan + the current-plan query, not every plan query globally:
 *   getPlanById(id)    → [{type:'plans', id}]
 *   getCurrentPlan()   → [{type:'plans', id: plan.id}, {type:'plans', id:'CURRENT'}]
 * `transformErrorResponse` surfaces the RFC-7807 safe message (B.21).
 */
export const plansApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    getCurrentPlan: build.query<WeeklyPlanDto, void>({
      query: () => '/api/plans/current',
      providesTags: (result) =>
        result
          ? [
              { type: 'plans', id: result.id },
              { type: 'plans', id: 'CURRENT' },
            ]
          : [{ type: 'plans', id: 'CURRENT' }],
      transformErrorResponse: (response) => parseProblemDetail(response.data),
    }),
    getPlanById: build.query<WeeklyPlanDto, string>({
      query: (id) => `/api/plans/${id}`,
      providesTags: (_result, _error, id) => [{ type: 'plans', id }],
      transformErrorResponse: (response) => parseProblemDetail(response.data),
    }),
    /**
     * E8 lock (rule #1, server-enforced). No body. On success the plan tag is
     * invalidated so the view refetches into `LOCKED` — **no optimistic flip**.
     * A blocked lock (`409 UNLINKED_PLANNED_COMMITMENT`/`EMPTY_PLAN_LOCK`) surfaces
     * the parsed `safeMessage` + `fieldErrors[]`. Lifecycle transitions live here;
     * 9.8 adds start/close-reconciliation alongside.
     */
    lockPlan: build.mutation<WeeklyPlanDto, string>({
      query: (id) => ({ url: `/api/plans/${id}/lock`, method: 'POST' }),
      invalidatesTags: (_result, error, id) => (error ? [] : planTags(id)),
      transformErrorResponse: (response) => parseProblemDetail(response.data),
    }),
    /**
     * E9 start-reconciliation (LOCKED→RECONCILING). No body, no `If-Match`
     * (consistent with `lockPlan`; an optimistic-lock conflict surfaces as a
     * server `409` rendered verbatim). Success invalidates the plan tag so the
     * view refetches into `RECONCILING` — **no optimistic flip**.
     */
    startReconciliation: build.mutation<WeeklyPlanDto, string>({
      query: (id) => ({
        url: `/api/plans/${id}/start-reconciliation`,
        method: 'POST',
      }),
      invalidatesTags: (_result, error, id) => (error ? [] : planTags(id)),
      transformErrorResponse: (response) => parseProblemDetail(response.data),
    }),
    /**
     * E10 close-reconciliation (RECONCILING→RECONCILED). No body. Success
     * invalidates the plan tag → refetch into `RECONCILED`. A blocked close
     * (`422 UNPLANNED_MISSING_LINK_AT_CLOSE`) surfaces the parsed `safeMessage`.
     */
    closeReconciliation: build.mutation<WeeklyPlanDto, string>({
      query: (id) => ({
        url: `/api/plans/${id}/close-reconciliation`,
        method: 'POST',
      }),
      invalidatesTags: (_result, error, id) => (error ? [] : planTags(id)),
      transformErrorResponse: (response) => parseProblemDetail(response.data),
    }),
  }),
});

export const {
  useGetCurrentPlanQuery,
  useGetPlanByIdQuery,
  useLockPlanMutation,
  useStartReconciliationMutation,
  useCloseReconciliationMutation,
} = plansApi;
