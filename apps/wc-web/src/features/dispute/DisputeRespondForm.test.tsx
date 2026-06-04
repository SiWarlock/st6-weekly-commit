import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { DisputeRespondForm } from './DisputeRespondForm';
import { useRespondDisputeMutation } from './disputesApi';
import type { AlignmentDisputeDto } from '../../shared/lib/dtos';

vi.mock('./disputesApi');
vi.mock('../rcdo/SupportingOutcomePicker', () => ({
  SupportingOutcomePicker: ({
    onChange,
  }: {
    value: string | null;
    onChange: (id: string) => void;
  }) => (
    <button type="button" data-cy="so-picker" onClick={() => onChange('so-9')}>
      pick outcome
    </button>
  ),
}));

function dispute(
  overrides: Partial<AlignmentDisputeDto> = {},
): AlignmentDisputeDto {
  return {
    id: 'd-1',
    commitmentId: 'c-1',
    managerEmployeeId: 'mgr-1',
    status: 'OPEN',
    flagType: 'MISALIGNED',
    managerNote: 'Off-strategy.',
    allowedActions: ['RESPOND_DISPUTE'],
    version: 0,
    ...overrides,
  };
}

function mockRespond(impl?: ReturnType<typeof vi.fn>) {
  vi.mocked(useRespondDisputeMutation).mockReturnValue([
    impl ?? vi.fn(() => ({ unwrap: () => Promise.resolve({}) })),
    { isLoading: false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useRespondDisputeMutation>);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('DisputeRespondForm (IC respond — gated, ≥1-field, server-error surfacing)', () => {
  it('absent_without_respond_capability: RESPOND_DISPUTE not in dispute.allowedActions → renders nothing (§11 server-authoritative)', () => {
    mockRespond();
    const { container } = render(
      <DisputeRespondForm
        dispute={dispute({ allowedActions: [] })}
        planId="plan-1"
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('submit_blocked_with_neither_field: with no icResponse and no SO picked, submit is blocked client-side (≥1 of the two required)', async () => {
    const respond = vi.fn(() => ({ unwrap: () => Promise.resolve({}) }));
    mockRespond(respond);
    render(<DisputeRespondForm dispute={dispute()} planId="plan-1" />);

    await userEvent.click(screen.getByRole('button', { name: /respond/i }));
    expect(respond).not.toHaveBeenCalled();
  });

  it('submit_with_icresponse_invokes_e18: typing a response + submitting calls respondDispute with the dispute id + planId + body', async () => {
    const respond = vi.fn(() => ({ unwrap: () => Promise.resolve({}) }));
    mockRespond(respond);
    render(<DisputeRespondForm dispute={dispute()} planId="plan-1" />);

    await userEvent.type(
      screen.getByRole('textbox', { name: /response/i }),
      'Re-scoped to the runbook.',
    );
    await userEvent.click(screen.getByRole('button', { name: /respond/i }));

    expect(respond).toHaveBeenCalledWith({
      disputeId: 'd-1',
      planId: 'plan-1',
      body: { icResponse: 'Re-scoped to the runbook.' },
    });
  });

  it('newSupportingOutcomeId_picker_wired: picking a new SO includes newSupportingOutcomeId in the E18 body', async () => {
    const respond = vi.fn(() => ({ unwrap: () => Promise.resolve({}) }));
    mockRespond(respond);
    render(<DisputeRespondForm dispute={dispute()} planId="plan-1" />);

    await userEvent.click(
      screen.getByRole('button', { name: /pick outcome/i }),
    );
    await userEvent.click(screen.getByRole('button', { name: /respond/i }));

    expect(respond).toHaveBeenCalledWith(
      expect.objectContaining({
        disputeId: 'd-1',
        planId: 'plan-1',
        body: expect.objectContaining({ newSupportingOutcomeId: 'so-9' }),
      }),
    );
  });

  it('server_error_surfaces_safe_message: a 400/422 rejection renders the server safeMessage verbatim (Cypress-assertable, §16)', async () => {
    const respond = vi.fn(() => ({
      unwrap: () =>
        Promise.reject({
          safeMessage: 'Provide a response or a new outcome.',
          code: 'VALIDATION_ERROR',
          fieldErrors: [],
        }),
    }));
    mockRespond(respond);
    render(<DisputeRespondForm dispute={dispute()} planId="plan-1" />);

    await userEvent.type(
      screen.getByRole('textbox', { name: /response/i }),
      'x',
    );
    await userEvent.click(screen.getByRole('button', { name: /respond/i }));

    expect(
      await screen.findByText('Provide a response or a new outcome.'),
    ).toBeInTheDocument();
  });
});
