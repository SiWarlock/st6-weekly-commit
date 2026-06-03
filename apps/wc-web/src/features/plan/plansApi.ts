import { baseApi } from '../../app/baseApi';
import { parseProblemDetail } from '../../shared/lib/problemDetails';
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
  }),
});

export const { useGetCurrentPlanQuery, useGetPlanByIdQuery } = plansApi;
