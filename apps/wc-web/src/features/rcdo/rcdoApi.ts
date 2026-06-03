import { baseApi } from '../../app/baseApi';
import { parseProblemDetail } from '../../shared/lib/problemDetails';

/**
 * RCDO hierarchy nodes (Appendix B.4, E2 `GET /api/rcdo`). The response is an
 * object wrapper (`RcdoTreeDto`), NOT a bare array (§5 envelope convention).
 * Read-only seeded data — no mutation endpoint exists (REQ-D-003).
 */
export interface SupportingOutcomeNode {
  id: string;
  definingObjectiveId: string;
  title: string;
  description?: string;
  active: boolean;
}

export interface DefiningObjectiveNode {
  id: string;
  rallyCryId: string;
  title: string;
  description?: string;
  active: boolean;
  supportingOutcomes: SupportingOutcomeNode[];
}

export interface RallyCryNode {
  id: string;
  title: string;
  description?: string;
  active: boolean;
  definingObjectives: DefiningObjectiveNode[];
}

export interface RcdoTreeDto {
  rallyCries: RallyCryNode[];
}

/**
 * `rcdoApi` — the read-only RC→DO→SO hierarchy for the Supporting-Outcome
 * picker + browser. Tagged `rcdo` and **never invalidated**: the data is seeded
 * and static (REQ-D-003), so no mutation in this or any later slice lists `rcdo`
 * in `invalidatesTags`. This slice defines a query only — no mutation.
 */
export const rcdoApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    getRcdo: build.query<RcdoTreeDto, void>({
      query: () => '/api/rcdo',
      providesTags: ['rcdo'],
      transformErrorResponse: (response) => parseProblemDetail(response.data),
    }),
  }),
});

export const { useGetRcdoQuery } = rcdoApi;
