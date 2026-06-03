import { baseApi } from '../../app/baseApi';
import { parseProblemDetail } from '../../shared/lib/problemDetails';
import type {
  ManagerCommandCenterRowDto,
  PageEnvelope,
  PlanState,
  ReviewStatus,
  Priority,
  WorkType,
  AlignmentStatus,
  HeatmapResponseDto,
  HeatmapDrilldownDto,
} from '../../shared/lib/dtos';

/**
 * E13 query params (typed; one object → a clean RTK Query cache key). `weekStart`
 * is required; `reviewState` includes the derived `OVERDUE` filter (NOT a
 * `ReviewStatus` enum value — it filters server-side on `isReviewOverdue`).
 */
export interface CommandCenterParams {
  weekStart: string;
  // Optional fields carry an explicit `| undefined` so a filter can be CLEARED
  // by patching it to undefined (the project runs `exactOptionalPropertyTypes`).
  page?: number | undefined;
  size?: number | undefined;
  sort?: string[] | undefined;
  employeeId?: string | undefined;
  planState?: PlanState | undefined;
  reviewState?: (ReviewStatus | 'OVERDUE') | undefined;
  definingObjectiveId?: string | undefined;
  priority?: Priority | undefined;
  workType?: WorkType | undefined;
  alignmentStatus?: AlignmentStatus | undefined;
}

/** F.5 default sort for the command center (applied when the client sends none). */
const DEFAULT_SORT = ['weekStartDate,desc', 'employeeDisplayName,asc'];

/**
 * `managerApi` — the manager direct-report command center (E13, read-only).
 * Returns the B.20 paginated envelope of B.11 rows; tagged `manager` (the single
 * tag covering both the command-center + heatmap projections, §9). The server
 * paginates (`Pageable`) — the client never loads all rows (REQ-NF-002). An IDOR
 * `403`/`404` surfaces `safeMessage` only (§6).
 */
export const managerApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    getCommandCenter: build.query<
      PageEnvelope<ManagerCommandCenterRowDto>,
      CommandCenterParams
    >({
      query: (params) => {
        const qs = new URLSearchParams();
        qs.set('weekStart', params.weekStart);
        qs.set('page', String(params.page ?? 0));
        qs.set('size', String(params.size ?? 25));
        (params.sort ?? DEFAULT_SORT).forEach((s) => qs.append('sort', s));
        const filters: [string, string | undefined][] = [
          ['employeeId', params.employeeId],
          ['planState', params.planState],
          ['reviewState', params.reviewState],
          ['definingObjectiveId', params.definingObjectiveId],
          ['priority', params.priority],
          ['workType', params.workType],
          ['alignmentStatus', params.alignmentStatus],
        ];
        for (const [key, v] of filters) {
          if (v !== undefined && v !== '') {
            qs.append(key, v);
          }
        }
        return `/api/manager/command-center?${qs.toString()}`;
      },
      providesTags: ['manager'],
      transformErrorResponse: (response) => parseProblemDetail(response.data),
    }),
    /**
     * E14 heatmap — report × Defining-Objective cells + enumerated `riskBadges[]`.
     * **Not paginated** (bounded by reports × DOs, §14). Tagged `manager` (no
     * heatmap tag — §9); IDOR `403`/`404` surfaces `safeMessage` only (§6).
     */
    getHeatmap: build.query<
      HeatmapResponseDto,
      {
        weekStart: string;
        definingObjectiveId?: string;
        supportingOutcomeId?: string;
      }
    >({
      query: ({ weekStart, definingObjectiveId, supportingOutcomeId }) => {
        const qs = new URLSearchParams();
        qs.set('weekStart', weekStart);
        if (definingObjectiveId) {
          qs.append('definingObjectiveId', definingObjectiveId);
        }
        if (supportingOutcomeId) {
          qs.append('supportingOutcomeId', supportingOutcomeId);
        }
        return `/api/manager/heatmap?${qs.toString()}`;
      },
      providesTags: ['manager'],
      transformErrorResponse: (response) => parseProblemDetail(response.data),
    }),
    /**
     * E15 drilldown — the SO→commitment breakdown for one cell (own-cell-only;
     * a `404` on a non-own cell is IDOR-safe, §6). Commitments are bounded +
     * paginated (B.20, §14); a single `page`/`size` paginates every group.
     */
    getHeatmapDrilldown: build.query<
      HeatmapDrilldownDto,
      { cellId: string; page?: number; size?: number }
    >({
      query: ({ cellId, page, size }) => {
        const qs = new URLSearchParams();
        qs.set('page', String(page ?? 0));
        qs.set('size', String(size ?? 25));
        return `/api/manager/heatmap/${cellId}/drilldown?${qs.toString()}`;
      },
      providesTags: ['manager'],
      transformErrorResponse: (response) => parseProblemDetail(response.data),
    }),
  }),
});

export const {
  useGetCommandCenterQuery,
  useGetHeatmapQuery,
  useGetHeatmapDrilldownQuery,
} = managerApi;
