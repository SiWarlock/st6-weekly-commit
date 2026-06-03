import { baseApi } from '../../app/baseApi';
import { parseProblemDetail } from '../../shared/lib/problemDetails';
import type {
  CommentDto,
  CommentTargetType,
  CreateCommentRequest,
  PageEnvelope,
} from '../../shared/lib/dtos';

/**
 * E20 query params (typed; one object → a clean RTK Query cache key). `targetType`
 * + `targetId` identify the thread; `page`/`size`/`sort` follow the Pageable
 * convention (B.20). The optional fields carry an explicit `| undefined` so the
 * project's `exactOptionalPropertyTypes` stays satisfied.
 */
export interface GetCommentsParams {
  targetType: CommentTargetType;
  targetId: string;
  page?: number | undefined;
  size?: number | undefined;
  sort?: string[] | undefined;
}

/** F.5 default sort for the comments thread (chronological) when none is sent. */
const DEFAULT_SORT = ['createdAt,asc'];

/**
 * Per-target cache tag (Q2): a comment on one target invalidates ONLY that
 * thread, never every thread. Single-slice, so it stays local (unlike the shared
 * `planTags` helper in app/tags.ts). The `comments` tag type is registered in
 * app/tags.ts.
 */
function commentTag(targetType: CommentTargetType, targetId: string) {
  return { type: 'comments' as const, id: `${targetType}:${targetId}` };
}

/**
 * `commentsApi` — flat one-level comments (§11). `getComments` reads a target's
 * B.20 paginated envelope (E20, default sort createdAt ASC); `createComment`
 * posts a new comment (E21) and invalidates ONLY the posted target's thread
 * **success-only** (`error ? [] : tags`, LESSONS §10) — no optimistic write (§7).
 * An unseeable/nonexistent target → `404` surfaces `safeMessage` only (§6/§16).
 */
export const commentsApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    getComments: build.query<PageEnvelope<CommentDto>, GetCommentsParams>({
      query: ({ targetType, targetId, page, size, sort }) => {
        const qs = new URLSearchParams();
        qs.set('targetType', targetType);
        qs.set('targetId', targetId);
        qs.set('page', String(page ?? 0));
        qs.set('size', String(size ?? 25));
        (sort ?? DEFAULT_SORT).forEach((s) => qs.append('sort', s));
        return `/api/comments?${qs.toString()}`;
      },
      providesTags: (_result, _error, { targetType, targetId }) => [
        commentTag(targetType, targetId),
      ],
      transformErrorResponse: (response) => parseProblemDetail(response.data),
    }),
    createComment: build.mutation<CommentDto, CreateCommentRequest>({
      query: (body) => ({
        url: '/api/comments',
        method: 'POST',
        body,
      }),
      invalidatesTags: (_result, error, { targetType, targetId }) =>
        error ? [] : [commentTag(targetType, targetId)],
      transformErrorResponse: (response) => parseProblemDetail(response.data),
    }),
  }),
});

export const { useGetCommentsQuery, useCreateCommentMutation } = commentsApi;
