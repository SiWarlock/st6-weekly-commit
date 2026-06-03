import { baseApi } from '../../app/baseApi';
import { parseProblemDetail } from '../../shared/lib/problemDetails';
import type { OutlookSyncRecordDto } from '../../shared/lib/dtos';

/**
 * `syncApi` — the IC's Outlook-sync visibility (§10). `getSyncRecords` (E22) reads
 * a plan's sync records as a **plain bounded array** (NOT paginated); `retrySync`
 * (E23) re-publishes a `FAILED` record (no body). Both tagged `sync`; `retrySync`
 * invalidates `['sync']` **success-only** (`error ? [] : ['sync']`, LESSONS §10) so
 * the badge transitions toward `RETRY_REQUESTED`/`QUEUED` on the refetch — no
 * optimistic flip. The sync surface NEVER blocks the core lifecycle (rule #4); a
 * failure surfaces only the `safeMessage` (rule #7).
 */
export const syncApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    getSyncRecords: build.query<OutlookSyncRecordDto[], string>({
      query: (planId) => ({ url: '/api/outlook-sync', params: { planId } }),
      providesTags: ['sync'],
      transformErrorResponse: (response) => parseProblemDetail(response.data),
    }),
    retrySync: build.mutation<OutlookSyncRecordDto, { syncRecordId: string }>({
      query: ({ syncRecordId }) => ({
        url: `/api/outlook-sync/${syncRecordId}/retry`,
        method: 'POST',
      }),
      invalidatesTags: (_result, error) => (error ? [] : ['sync']),
      transformErrorResponse: (response) => parseProblemDetail(response.data),
    }),
  }),
});

export const { useGetSyncRecordsQuery, useRetrySyncMutation } = syncApi;
