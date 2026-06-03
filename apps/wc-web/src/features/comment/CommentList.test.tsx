import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { CommentList } from './CommentList';
import { useGetCommentsQuery } from './commentsApi';
import type { CommentDto, PageEnvelope } from '../../shared/lib/dtos';

vi.mock('./commentsApi');

function comment(overrides: Partial<CommentDto> & { id: string }): CommentDto {
  return {
    targetType: 'COMMITMENT',
    targetId: 'c-1',
    authorEmployeeId: 'emp-1',
    authorDisplayName: 'Ivy Chen',
    parentCommentId: null,
    depth: 0,
    body: `Comment ${overrides.id}`,
    createdAt: '2026-06-02T10:00:00Z',
    ...overrides,
  };
}

function env(
  comments: CommentDto[],
  page: Partial<PageEnvelope<CommentDto>['page']> = {},
): PageEnvelope<CommentDto> {
  return {
    content: comments,
    page: {
      number: 0,
      size: 25,
      totalElements: comments.length,
      totalPages: 1,
      ...page,
    },
    sort: [{ property: 'createdAt', direction: 'ASC' }],
  };
}

function mockQuery(value: {
  data?: PageEnvelope<CommentDto>;
  isLoading?: boolean;
  isError?: boolean;
  error?: unknown;
}) {
  vi.mocked(useGetCommentsQuery).mockReturnValue({
    data: value.data,
    isLoading: value.isLoading ?? false,
    isError: value.isError ?? false,
    error: value.error,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useGetCommentsQuery>);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('CommentList (flat one-level thread, B.9 — §11)', () => {
  it('renders_flat_comments_with_author_and_time: each CommentDto shows authorDisplayName + body + a time element; a comment carrying a non-null parentCommentId / depth>0 still renders as a FLAT sibling (no nesting/indent logic, §11)', () => {
    mockQuery({
      data: env([
        comment({ id: 'a', authorDisplayName: 'Ivy Chen', body: 'First' }),
        // A (hypothetical) reply — MVP keeps it flat: same row treatment, no indent.
        comment({
          id: 'b',
          authorDisplayName: 'Sam Lee',
          body: 'Second',
          parentCommentId: 'a',
          depth: 1,
        }),
      ]),
    });

    const { container } = render(
      <CommentList targetType="COMMITMENT" targetId="c-1" />,
    );

    const rows = container.querySelectorAll('[data-cy="comment-row"]');
    expect(rows).toHaveLength(2);
    // Both rows are flat siblings — no comment-row is nested inside another.
    rows.forEach((r) =>
      expect(r.querySelector('[data-cy="comment-row"]')).toBeNull(),
    );
    expect(screen.getByText('Ivy Chen')).toBeInTheDocument();
    expect(screen.getByText('First')).toBeInTheDocument();
    expect(screen.getByText('Sam Lee')).toBeInTheDocument();
    expect(screen.getByText('Second')).toBeInTheDocument();
    // createdAt is surfaced per row.
    expect(rows[0]!.querySelector('[data-cy="comment-time"]')).not.toBeNull();
  });

  it('renders_loading_empty_error_states_and_paginates: loading→role=status skeleton; zero content→EmptyState; an error→ErrorState(safeMessage); a multi-page B.20 envelope renders the server-side pager (§7)', () => {
    // Loading (mock established before the first render).
    mockQuery({ isLoading: true });
    const { rerender } = render(
      <CommentList targetType="COMMITMENT" targetId="c-1" />,
    );
    expect(screen.getByRole('status')).toBeInTheDocument();

    // Empty.
    mockQuery({ data: env([]) });
    rerender(<CommentList targetType="COMMITMENT" targetId="c-1" />);
    expect(document.querySelector('[data-cy="empty-state"]')).not.toBeNull();

    // Error — IDOR-safe safeMessage only.
    mockQuery({
      isError: true,
      error: { safeMessage: 'That item is not available.' },
    });
    rerender(<CommentList targetType="COMMITMENT" targetId="c-1" />);
    expect(document.querySelector('[data-cy="error-state"]')).not.toBeNull();
    expect(screen.getByText('That item is not available.')).toBeInTheDocument();

    // Paginates — server-side B.20 envelope drives the control (≥2 pages).
    mockQuery({
      data: env([comment({ id: 'a' })], { totalPages: 3, totalElements: 60 }),
    });
    rerender(<CommentList targetType="COMMITMENT" targetId="c-1" />);
    expect(document.querySelector('[data-cy="pagination"]')).not.toBeNull();
    expect(
      document.querySelector('[data-cy="page-indicator"]'),
    ).toHaveTextContent('Page 1 of 3');
  });

  it('xss_probe_body_renders_escaped: a body of <img src=x onerror=alert(1)> renders as inert escaped TEXT — the literal string is in the DOM, no <img> element is created (REQ-S-005, §16, forbidden #4)', () => {
    const xss = '<img src=x onerror=alert(1)>';
    mockQuery({ data: env([comment({ id: 'x', body: xss })]) });

    const { container } = render(
      <CommentList targetType="COMMITMENT" targetId="c-1" />,
    );

    // The payload is present as text, NOT as a parsed element.
    expect(screen.getByText(xss)).toBeInTheDocument();
    expect(container.querySelector('img')).toBeNull();
  });
});
