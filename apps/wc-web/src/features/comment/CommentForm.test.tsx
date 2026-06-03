import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { CommentForm } from './CommentForm';
import { useCreateCommentMutation } from './commentsApi';
import type { CommentDto } from '../../shared/lib/dtos';

vi.mock('./commentsApi');

function created(): CommentDto {
  return {
    id: 'cm-new',
    targetType: 'COMMITMENT',
    targetId: 'c-1',
    authorEmployeeId: 'emp-1',
    authorDisplayName: 'Ivy Chen',
    parentCommentId: null,
    depth: 0,
    body: 'Great progress',
    createdAt: '2026-06-02T11:00:00Z',
  };
}

function mockCreate(trigger: ReturnType<typeof vi.fn>) {
  vi.mocked(useCreateCommentMutation).mockReturnValue([
    trigger,
    { isLoading: false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useCreateCommentMutation>);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('CommentForm (E21 post — server-authoritative, no optimistic write)', () => {
  it('posts_nonempty_body_and_refetches: an empty body is a UX-only block (submit disabled); a non-empty body invokes createComment({targetType,targetId,body}) and clears on success (relies on list refetch, no optimistic insert)', async () => {
    const user = userEvent.setup();
    const trigger = vi.fn(() => ({ unwrap: () => Promise.resolve(created()) }));
    mockCreate(trigger);

    render(<CommentForm targetType="COMMITMENT" targetId="c-1" />);

    const submit = screen.getByRole('button', { name: /comment/i });
    // Empty body → blocked (UX-only; the server remains authoritative).
    expect(submit).toBeDisabled();

    await user.type(screen.getByRole('textbox'), 'Great progress');
    expect(submit).toBeEnabled();
    await user.click(submit);

    expect(trigger).toHaveBeenCalledWith({
      targetType: 'COMMITMENT',
      targetId: 'c-1',
      body: 'Great progress',
    });
    // No optimistic write — the input clears on success and the thread refetches.
    await waitFor(() => expect(screen.getByRole('textbox')).toHaveValue(''));
  });

  it('surfaces_server_safeMessage: a server 400/404 rejection renders the parsed safeMessage verbatim and does NOT clear the input', async () => {
    const user = userEvent.setup();
    const trigger = vi.fn(() => ({
      unwrap: () =>
        Promise.reject({
          safeMessage: 'That item is not available.',
          code: 'NOT_FOUND',
          fieldErrors: [],
        }),
    }));
    mockCreate(trigger);

    render(<CommentForm targetType="PLAN" targetId="plan-1" />);

    await user.type(screen.getByRole('textbox'), 'hello');
    await user.click(screen.getByRole('button', { name: /comment/i }));

    expect(trigger).toHaveBeenCalledWith({
      targetType: 'PLAN',
      targetId: 'plan-1',
      body: 'hello',
    });
    expect(
      await screen.findByText('That item is not available.'),
    ).toBeInTheDocument();
    // Body retained so the author can retry without retyping.
    expect(screen.getByRole('textbox')).toHaveValue('hello');
  });
});
