import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { DisputeResolveAction } from './DisputeResolveAction';
import { useResolveDisputeMutation } from './disputesApi';
import type { AlignmentDisputeDto } from '../../shared/lib/dtos';

vi.mock('./disputesApi');

function dispute(
  overrides: Partial<AlignmentDisputeDto> = {},
): AlignmentDisputeDto {
  return {
    id: 'd-1',
    commitmentId: 'c-1',
    managerEmployeeId: 'mgr-1',
    status: 'IC_RESPONDED',
    flagType: 'MISALIGNED',
    managerNote: 'Off-strategy.',
    icResponse: 'Re-scoped.',
    allowedActions: ['RESOLVE_DISPUTE'],
    version: 1,
    ...overrides,
  };
}

function mockResolve(impl?: ReturnType<typeof vi.fn>) {
  vi.mocked(useResolveDisputeMutation).mockReturnValue([
    impl ?? vi.fn(() => ({ unwrap: () => Promise.resolve({}) })),
    { isLoading: false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useResolveDisputeMutation>);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('DisputeResolveAction (manager resolve — gated, server-error surfacing)', () => {
  it('absent_without_resolve_capability: RESOLVE_DISPUTE not in dispute.allowedActions → renders nothing (§11)', () => {
    mockResolve();
    const { container } = render(
      <DisputeResolveAction
        dispute={dispute({ allowedActions: [] })}
        planId="plan-1"
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('resolve_invokes_e19: clicking resolve calls resolveDispute with the dispute id + planId + body', async () => {
    const resolveFn = vi.fn(() => ({ unwrap: () => Promise.resolve({}) }));
    mockResolve(resolveFn);
    render(<DisputeResolveAction dispute={dispute()} planId="plan-1" />);

    await userEvent.click(screen.getByRole('button', { name: /resolve/i }));

    expect(resolveFn).toHaveBeenCalledWith(
      expect.objectContaining({ disputeId: 'd-1', planId: 'plan-1' }),
    );
  });

  it('ic_cannot_resolve_safe_message_surfaces: a 403 IC_CANNOT_RESOLVE_DISPUTE rejection renders the server safeMessage (Cypress-assertable)', async () => {
    const resolveFn = vi.fn(() => ({
      unwrap: () =>
        Promise.reject({
          safeMessage: 'Only the manager can resolve this dispute.',
          code: 'IC_CANNOT_RESOLVE_DISPUTE',
          fieldErrors: [],
        }),
    }));
    mockResolve(resolveFn);
    render(<DisputeResolveAction dispute={dispute()} planId="plan-1" />);

    await userEvent.click(screen.getByRole('button', { name: /resolve/i }));

    expect(
      await screen.findByText('Only the manager can resolve this dispute.'),
    ).toBeInTheDocument();
  });
});
