import { baseApi } from '../../app/baseApi';
import { parseProblemDetail } from '../../shared/lib/problemDetails';

/**
 * Current authenticated identity (Appendix B.3, E1 `GET /api/me`). Mirrors the
 * contract verbatim — render/consume only, no field added or renamed.
 */
export interface MeDto {
  employeeId: string;
  email: string;
  displayName: string;
  role: 'IC' | 'MANAGER';
  /** Active demo persona key; in auth0 mode = email. */
  persona: string;
  /** True iff the actor has ≥1 active direct report (gates manager surfaces). */
  isManager: boolean;
  timezone?: string;
}

/**
 * `meApi` — the read foundation for route gating + the persona-aware default
 * route. Tagged `me` and (per MVP) never invalidated: identity is effectively
 * per-session-static (no mutation lists `me` in `invalidatesTags`).
 */
export const meApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    getMe: build.query<MeDto, void>({
      query: () => '/api/me',
      providesTags: ['me'],
      transformErrorResponse: (response) => parseProblemDetail(response.data),
    }),
  }),
});

export const { useGetMeQuery } = meApi;
