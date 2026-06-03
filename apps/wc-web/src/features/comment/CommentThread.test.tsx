import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { CommentThread } from './CommentThread';
import type { CommentTargetType } from '../../shared/lib/dtos';

// Stub the children so the thread test stays store-free — opening must NOT fire a
// real getComments query here (that is covered in CommentList's own test). Stubs
// use data-testid (the default test-id attribute) so getByTestId resolves them.
vi.mock('./CommentList', () => ({
  CommentList: ({
    targetType,
    targetId,
  }: {
    targetType: CommentTargetType;
    targetId: string;
  }) => (
    <div
      data-testid="comment-list-stub"
      data-target-type={targetType}
      data-target-id={targetId}
    />
  ),
}));
vi.mock('./CommentForm', () => ({
  CommentForm: ({
    targetType,
    targetId,
  }: {
    targetType: CommentTargetType;
    targetId: string;
  }) => (
    <div
      data-testid="comment-form-stub"
      data-target-type={targetType}
      data-target-id={targetId}
    />
  ),
}));

afterEach(() => {
  vi.restoreAllMocks();
});

describe('CommentThread (COMMENT-gated, lazy/collapsible wrapper — Q1)', () => {
  it('comment_affordance_gated_on_COMMENT_allowedAction: the affordance renders iff can(COMMENT, allowedActions); absent → nothing renders (server-authoritative, never re-derived — §6/§11)', () => {
    // COMMENT absent → nothing renders (commitment-shaped allowedActions set).
    const { rerender, container } = render(
      <CommentThread
        targetType="COMMITMENT"
        targetId="c-1"
        allowedActions={['CARRY_FORWARD']}
      />,
    );
    expect(container).toBeEmptyDOMElement();

    // COMMENT present → the toggle affordance renders (covers a plan target too).
    rerender(
      <CommentThread
        targetType="PLAN"
        targetId="plan-1"
        allowedActions={['COMMENT']}
      />,
    );
    expect(
      screen.getByRole('button', { name: /comments/i }),
    ).toBeInTheDocument();
  });

  it('lazy_mounts_list_and_form_only_when_opened: collapsed by default (children unmounted → no getComments query); opening reveals CommentList + CommentForm wired to the same target (Q1 lazy, Q3)', async () => {
    const user = userEvent.setup();
    render(
      <CommentThread
        targetType="COMMITMENT"
        targetId="c-1"
        allowedActions={['COMMENT']}
      />,
    );

    // Collapsed — neither child is mounted (avoids N queries per row).
    expect(screen.queryByTestId('comment-list-stub')).toBeNull();
    expect(screen.queryByTestId('comment-form-stub')).toBeNull();

    await user.click(screen.getByRole('button', { name: /comments/i }));

    const list = screen.getByTestId('comment-list-stub');
    const form = screen.getByTestId('comment-form-stub');
    expect(list).toHaveAttribute('data-target-type', 'COMMITMENT');
    expect(list).toHaveAttribute('data-target-id', 'c-1');
    expect(form).toHaveAttribute('data-target-type', 'COMMITMENT');
    expect(form).toHaveAttribute('data-target-id', 'c-1');
  });
});
