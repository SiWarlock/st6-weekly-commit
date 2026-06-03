import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { CommitmentForm } from './CommitmentForm';
import { useCreateCommitmentMutation } from './commitmentsApi';

vi.mock('./commitmentsApi');
vi.mock('../rcdo/SupportingOutcomePicker', () => ({
  SupportingOutcomePicker: ({
    onChange,
  }: {
    onChange: (id: string) => void;
  }) => (
    <button type="button" onClick={() => onChange('so-1')}>
      pick outcome
    </button>
  ),
}));

function mockCreate(
  trigger: ReturnType<typeof vi.fn>,
  state: { isLoading?: boolean; error?: unknown } = {},
) {
  vi.mocked(useCreateCommitmentMutation).mockReturnValue([
    trigger,
    { isLoading: state.isLoading ?? false, error: state.error, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useCreateCommitmentMutation>);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('CommitmentForm (controlled create form → createCommitment E5)', () => {
  it('form_submits_valid_commitment_with_linked_SO_and_chess: submit dispatches createCommitment with the linked supportingOutcomeId + chess fields', async () => {
    const user = userEvent.setup();
    const trigger = vi.fn(() => ({
      unwrap: () => Promise.resolve({ id: 'c-new' }),
    }));
    mockCreate(trigger);

    render(<CommitmentForm planId="plan-1" planState="DRAFT" />);

    await user.type(screen.getByLabelText(/title/i), 'Ship onboarding');
    await user.click(screen.getByRole('button', { name: /pick outcome/i }));
    await user.selectOptions(screen.getByLabelText(/priority/i), 'P0');
    await user.selectOptions(screen.getByLabelText(/work type/i), 'BLOCKER');
    await user.selectOptions(screen.getByLabelText(/confidence/i), 'LOW');
    await user.click(
      screen.getByRole('button', { name: /create commitment/i }),
    );

    expect(trigger).toHaveBeenCalledTimes(1);
    expect(trigger).toHaveBeenCalledWith({
      planId: 'plan-1',
      body: expect.objectContaining({
        title: 'Ship onboarding',
        supportingOutcomeId: 'so-1',
        priority: 'P0',
        workType: 'BLOCKER',
        confidence: 'LOW',
        alignmentStatus: 'NEEDS_REVIEW',
      }),
    });
  });

  it('create_validation_409_surfaces_safeMessage_and_fieldErrors: a server 409 renders the parsed safeMessage + per-field fieldErrors as stable text', async () => {
    const user = userEvent.setup();
    const trigger = vi.fn(() => ({
      unwrap: () =>
        Promise.reject({
          safeMessage: 'Please fix the highlighted fields.',
          code: 'VALIDATION_ERROR',
          fieldErrors: [
            { field: 'title', code: 'NotBlank', message: 'Title is required.' },
          ],
        }),
    }));
    mockCreate(trigger);

    render(<CommitmentForm planId="plan-1" planState="DRAFT" />);
    await user.type(screen.getByLabelText(/title/i), 'x');
    await user.click(
      screen.getByRole('button', { name: /create commitment/i }),
    );

    expect(
      await screen.findByText('Please fix the highlighted fields.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Title is required.')).toBeInTheDocument();
  });
});
