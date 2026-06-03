import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { LoadingState } from './LoadingState';
import { EmptyState } from './EmptyState';
import { ErrorState } from './ErrorState';
import { PartialState } from './PartialState';
import { stripComments } from '../../test/util';

const here = dirname(fileURLToPath(import.meta.url));

describe('view-state primitives (§7 view-state contract)', () => {
  it('view_states_render_their_slot: each view-state renders its distinctive content', () => {
    const { rerender } = render(<LoadingState delayMs={0} />);
    expect(screen.getByRole('status')).toBeInTheDocument();

    rerender(
      <EmptyState title="No plans yet" message="Create your first plan." />,
    );
    expect(screen.getByText('No plans yet')).toBeInTheDocument();
    expect(screen.getByText('Create your first plan.')).toBeInTheDocument();

    rerender(<ErrorState message="Could not load plans." />);
    expect(screen.getByText('Could not load plans.')).toBeInTheDocument();

    rerender(
      <PartialState warning={{ message: 'Outlook sync failed.' }}>
        <div>Plan content</div>
      </PartialState>,
    );
    expect(screen.getByText('Plan content')).toBeInTheDocument();
    expect(screen.getByText('Outlook sync failed.')).toBeInTheDocument();
  });

  it('error_state_renders_safeMessage_escaped: safeMessage + traceId render as escaped text; no dangerouslySetInnerHTML', () => {
    const xss = '<img src=x onerror=alert(1)>';
    const { container } = render(
      <ErrorState message={xss} traceId="0af7-trace" />,
    );

    // The payload renders as literal text, never as a DOM element (React escaping).
    expect(container.querySelector('img')).toBeNull();
    expect(screen.getByText(xss)).toBeInTheDocument();
    expect(screen.getByText(/0af7-trace/)).toBeInTheDocument();

    // Fail-closed: NO shared component uses dangerouslySetInnerHTML (forbidden #4).
    const dir = here;
    for (const file of readdirSync(dir)) {
      if (!file.endsWith('.tsx') || file.endsWith('.test.tsx')) continue;
      const code = stripComments(readFileSync(join(dir, file), 'utf8'));
      expect(code).not.toMatch(/dangerouslySetInnerHTML/);
    }
  });

  it('partial_state_shows_warning_without_blocking: success content + dismissible warning + retry; never blocks', async () => {
    const onRetry = vi.fn();
    const user = userEvent.setup();
    render(
      <PartialState warning={{ message: 'Outlook sync failed.', onRetry }}>
        <div>Plan loaded</div>
      </PartialState>,
    );
    // Success content always renders (non-blocking).
    expect(screen.getByText('Plan loaded')).toBeInTheDocument();
    expect(screen.getByText('Outlook sync failed.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /retry/i }));
    expect(onRetry).toHaveBeenCalledTimes(1);

    // Dismiss hides the warning but keeps the success content.
    await user.click(screen.getByRole('button', { name: /dismiss/i }));
    expect(screen.queryByText('Outlook sync failed.')).toBeNull();
    expect(screen.getByText('Plan loaded')).toBeInTheDocument();
  });
});
