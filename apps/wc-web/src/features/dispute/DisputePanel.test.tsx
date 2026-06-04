import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { DisputePanel } from './DisputePanel';
import { useOpenDisputeMutation } from './disputesApi';
import { useRespondDisputeMutation } from './disputesApi';
import { useResolveDisputeMutation } from './disputesApi';
import type {
  AlignmentDisputeDto,
  WeeklyCommitmentDto,
} from '../../shared/lib/dtos';

vi.mock('./disputesApi');
vi.mock('../rcdo/SupportingOutcomePicker', () => ({
  SupportingOutcomePicker: () => <div data-cy="so-picker" />,
}));

function commitment(
  overrides: Partial<WeeklyCommitmentDto> = {},
): WeeklyCommitmentDto {
  return {
    id: 'c-1',
    weeklyPlanId: 'plan-1',
    commitmentKind: 'PLANNED',
    title: 'Ship the release train',
    priority: 'P1',
    workType: 'STRATEGIC',
    confidence: 'HIGH',
    alignmentStatus: 'NEEDS_REVIEW',
    allowedActions: [],
    version: 0,
    ...overrides,
  };
}

function dispute(
  overrides: Partial<AlignmentDisputeDto> = {},
): AlignmentDisputeDto {
  return {
    id: 'd-1',
    commitmentId: 'c-1',
    managerEmployeeId: 'mgr-1',
    status: 'OPEN',
    flagType: 'MISALIGNED',
    managerNote: 'This drifts from the activation goal.',
    allowedActions: [],
    version: 0,
    ...overrides,
  };
}

function mockMutations(open?: ReturnType<typeof vi.fn>) {
  vi.mocked(useOpenDisputeMutation).mockReturnValue([
    open ?? vi.fn(() => ({ unwrap: () => Promise.resolve({}) })),
    { isLoading: false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useOpenDisputeMutation>);
  vi.mocked(useRespondDisputeMutation).mockReturnValue([
    vi.fn(),
    { isLoading: false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useRespondDisputeMutation>);
  vi.mocked(useResolveDisputeMutation).mockReturnValue([
    vi.fn(),
    { isLoading: false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useResolveDisputeMutation>);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('DisputePanel (open-form XOR lifecycle stepper + display, server-gated)', () => {
  it('open_form_renders_when_no_dispute_and_open_allowed: no dispute + OPEN_DISPUTE ∈ allowedActions → the manager open form (flagType select + managerNote)', () => {
    mockMutations();
    render(
      <DisputePanel
        commitment={commitment({ allowedActions: ['OPEN_DISPUTE'] })}
      />,
    );
    expect(
      document.querySelector('[data-cy="dispute-open-form"]'),
    ).not.toBeNull();
    expect(
      screen.getByRole('button', { name: /open dispute/i }),
    ).toBeInTheDocument();
  });

  it('open_form_absent_without_capability: no dispute + OPEN_DISPUTE NOT allowed → no open form (server-authoritative, §11 — never re-derived)', () => {
    mockMutations();
    render(<DisputePanel commitment={commitment({ allowedActions: [] })} />);
    expect(document.querySelector('[data-cy="dispute-open-form"]')).toBeNull();
    expect(document.querySelector('[data-cy="dispute-panel"]')).toBeNull();
  });

  it('dispute_renders_as_three_node_stepper: a present dispute → OPEN→IC_RESPONDED→RESOLVED stepper with data-status done/active/pending derived from dispute.status', () => {
    mockMutations();
    render(
      <DisputePanel
        commitment={commitment({
          dispute: dispute({ status: 'IC_RESPONDED' }),
        })}
      />,
    );
    const stepper = document.querySelector(
      '[data-cy="dispute-stepper"]',
    ) as HTMLElement;
    expect(stepper).not.toBeNull();
    const statusOf = (s: string) =>
      stepper
        .querySelector(`[data-cy="dispute-step"][data-step="${s}"]`)
        ?.getAttribute('data-status');
    expect(statusOf('OPEN')).toBe('done');
    expect(statusOf('IC_RESPONDED')).toBe('active');
    expect(statusOf('RESOLVED')).toBe('pending');
  });

  it('dispute_display_shows_status_flagtype_note_response_escaped: the status pill + flagType + managerNote + icResponse render, user text React-escaped (REQ-S-005)', () => {
    mockMutations();
    const xss = '<img src=x onerror=alert(1)>';
    render(
      <DisputePanel
        commitment={commitment({
          dispute: dispute({
            status: 'IC_RESPONDED',
            managerNote: xss,
            icResponse: 'Re-scoped to the runbook.',
          }),
        })}
      />,
    );
    const panel = document.querySelector(
      '[data-cy="dispute-panel"]',
    ) as HTMLElement;
    // managerNote rendered as text (escaped — no injected <img> element).
    expect(within(panel).getByText(xss)).toBeInTheDocument();
    expect(panel.querySelector('img')).toBeNull();
    expect(
      within(panel).getByText('Re-scoped to the runbook.'),
    ).toBeInTheDocument();
    // The status pill conveys glyph+text (not color alone, §7/REQ-S-005).
    expect(panel.querySelector('[data-cy="dispute-status"]')).not.toBeNull();
  });

  it('open_form_submit_invokes_e17: filling flagType + managerNote and submitting calls openDispute({commitmentId, planId from weeklyPlanId, body})', async () => {
    const open = vi.fn(() => ({ unwrap: () => Promise.resolve({}) }));
    mockMutations(open);
    render(
      <DisputePanel
        commitment={commitment({ allowedActions: ['OPEN_DISPUTE'] })}
      />,
    );

    await userEvent.selectOptions(
      screen.getByRole('combobox', { name: /flag/i }),
      'MISALIGNED',
    );
    await userEvent.type(
      screen.getByRole('textbox', { name: /note/i }),
      'Off-strategy.',
    );
    await userEvent.click(
      screen.getByRole('button', { name: /open dispute/i }),
    );

    expect(open).toHaveBeenCalledWith({
      commitmentId: 'c-1',
      planId: 'plan-1',
      body: { flagType: 'MISALIGNED', managerNote: 'Off-strategy.' },
    });
  });

  it('open_error_surfaces_safe_message: an empty managerNote is blocked client-side; a 409 SECOND_OPEN_DISPUTE renders its safeMessage (rule #6 named error)', async () => {
    const open = vi.fn(() => ({
      unwrap: () =>
        Promise.reject({
          safeMessage: 'A dispute is already open on this commitment.',
          code: 'SECOND_OPEN_DISPUTE',
          fieldErrors: [],
        }),
    }));
    mockMutations(open);
    render(
      <DisputePanel
        commitment={commitment({ allowedActions: ['OPEN_DISPUTE'] })}
      />,
    );

    // Empty managerNote → required-field client block; openDispute not called.
    await userEvent.click(
      screen.getByRole('button', { name: /open dispute/i }),
    );
    expect(open).not.toHaveBeenCalled();

    // With a note, the server 409 safeMessage surfaces (Cypress-assertable).
    await userEvent.type(
      screen.getByRole('textbox', { name: /note/i }),
      'Off-strategy.',
    );
    await userEvent.click(
      screen.getByRole('button', { name: /open dispute/i }),
    );
    expect(
      await screen.findByText('A dispute is already open on this commitment.'),
    ).toBeInTheDocument();
  });
});
