import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { CommitmentForm } from './CommitmentForm';
import {
  useCreateCommitmentMutation,
  useAddUnplannedCommitmentMutation,
  useUpdateCommitmentMutation,
} from './commitmentsApi';
import type { WeeklyCommitmentDto } from '../../shared/lib/dtos';

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
  // CommitmentForm calls all three mutation hooks unconditionally (the picked
  // trigger depends on `kind`/`commitment`); give the others a benign default.
  vi.mocked(useAddUnplannedCommitmentMutation).mockReturnValue([
    vi.fn(),
    { isLoading: false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useAddUnplannedCommitmentMutation>);
  vi.mocked(useUpdateCommitmentMutation).mockReturnValue([
    vi.fn(),
    { isLoading: false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useUpdateCommitmentMutation>);
}

function mockAddUnplanned(trigger: ReturnType<typeof vi.fn>) {
  vi.mocked(useAddUnplannedCommitmentMutation).mockReturnValue([
    trigger,
    { isLoading: false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useAddUnplannedCommitmentMutation>);
}

function mockUpdate(trigger: ReturnType<typeof vi.fn>) {
  vi.mocked(useUpdateCommitmentMutation).mockReturnValue([
    trigger,
    { isLoading: false, reset: vi.fn() },
  ] as unknown as ReturnType<typeof useUpdateCommitmentMutation>);
}

function editable(
  overrides: Partial<WeeklyCommitmentDto> = {},
): WeeklyCommitmentDto {
  return {
    id: 'c-1',
    weeklyPlanId: 'plan-1',
    commitmentKind: 'PLANNED',
    title: 'Ship onboarding',
    description: 'the onboarding flow',
    supportingOutcomeId: 'so-1',
    priority: 'P0',
    workType: 'STRATEGIC',
    confidence: 'HIGH',
    alignmentStatus: 'NEEDS_REVIEW',
    allowedActions: [],
    version: 0,
    ...overrides,
  };
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

  it('unplanned_mode_submits_via_addUnplannedCommitment: kind="UNPLANNED" hides the WorkType field and submits via E11 with a CreateUnplannedCommitmentRequest (no workType); supportingOutcomeId optional', async () => {
    const user = userEvent.setup();
    const addTrigger = vi.fn(() => ({
      unwrap: () => Promise.resolve({ id: 'u-1' }),
    }));
    mockCreate(vi.fn());
    mockAddUnplanned(addTrigger);

    render(
      <CommitmentForm
        planId="plan-1"
        planState="RECONCILING"
        kind="UNPLANNED"
      />,
    );

    // Unplanned mode hides the work-type field (server forces workType=UNPLANNED).
    expect(screen.queryByLabelText(/work type/i)).toBeNull();

    await user.type(screen.getByLabelText(/title/i), 'Unplanned firefight');
    await user.selectOptions(screen.getByLabelText(/priority/i), 'P0');
    await user.selectOptions(screen.getByLabelText(/confidence/i), 'MEDIUM');
    await user.click(screen.getByRole('button', { name: /add unplanned/i }));

    expect(addTrigger).toHaveBeenCalledTimes(1);
    expect(addTrigger).toHaveBeenCalledWith({
      planId: 'plan-1',
      body: expect.objectContaining({
        title: 'Unplanned firefight',
        priority: 'P0',
        confidence: 'MEDIUM',
      }),
    });
    // The E11 request body must NOT carry workType (the server forces UNPLANNED).
    const firstCallArgs = addTrigger.mock.calls[0] as unknown as [
      { body: Record<string, unknown> },
    ];
    expect(firstCallArgs[0].body).not.toHaveProperty('workType');
  });
});

describe('CommitmentForm (edit mode → updateCommitment E6, 9.7b)', () => {
  it('edit_mode_prefills_and_patches_via_updateCommitment: given a DRAFT commitment, fields pre-fill; submit dispatches updateCommitment({id, planId, patch}) (E6), NOT createCommitment', async () => {
    const user = userEvent.setup();
    const updateTrigger = vi.fn(() => ({
      unwrap: () => Promise.resolve({ id: 'c-1' }),
    }));
    const createTrigger = vi.fn();
    mockCreate(createTrigger);
    mockUpdate(updateTrigger);

    render(
      <CommitmentForm
        planId="plan-1"
        planState="DRAFT"
        commitment={editable()}
      />,
    );

    // Pre-filled from the existing commitment baseline.
    expect(screen.getByLabelText(/title/i)).toHaveValue('Ship onboarding');
    expect(screen.getByLabelText(/priority/i)).toHaveValue('P0');

    await user.clear(screen.getByLabelText(/title/i));
    await user.type(screen.getByLabelText(/title/i), 'Ship onboarding v2');
    await user.click(screen.getByRole('button', { name: /save changes/i }));

    expect(updateTrigger).toHaveBeenCalledTimes(1);
    expect(updateTrigger).toHaveBeenCalledWith({
      id: 'c-1',
      planId: 'plan-1',
      patch: expect.objectContaining({
        title: 'Ship onboarding v2',
        supportingOutcomeId: 'so-1',
        priority: 'P0',
      }),
    });
    expect(createTrigger).not.toHaveBeenCalled();
  });

  it('create_mode_unchanged_when_no_target: absent edit target → createCommitment (E5); updateCommitment NOT called (regression guard)', async () => {
    const user = userEvent.setup();
    const createTrigger = vi.fn(() => ({
      unwrap: () => Promise.resolve({ id: 'c-new' }),
    }));
    const updateTrigger = vi.fn();
    mockCreate(createTrigger);
    mockUpdate(updateTrigger);

    render(<CommitmentForm planId="plan-1" planState="DRAFT" />);
    await user.type(screen.getByLabelText(/title/i), 'A fresh commitment');
    await user.click(
      screen.getByRole('button', { name: /create commitment/i }),
    );

    expect(createTrigger).toHaveBeenCalledTimes(1);
    expect(updateTrigger).not.toHaveBeenCalled();
  });

  it('edit_post_lock_renders_LOCKED_BASELINE_EDIT_verbatim: a 409 LOCKED_BASELINE_EDIT from an edit submit renders the server safeMessage verbatim (server-authoritative, rule #2, LESSONS §11)', async () => {
    const user = userEvent.setup();
    const updateTrigger = vi.fn(() => ({
      unwrap: () =>
        Promise.reject({
          safeMessage: 'This field is locked after the week is committed.',
          code: 'LOCKED_BASELINE_EDIT',
          fieldErrors: [],
        }),
    }));
    mockCreate(vi.fn());
    mockUpdate(updateTrigger);

    render(
      <CommitmentForm
        planId="plan-1"
        planState="DRAFT"
        commitment={editable()}
      />,
    );
    await user.click(screen.getByRole('button', { name: /save changes/i }));

    expect(
      await screen.findByText(
        'This field is locked after the week is committed.',
      ),
    ).toBeInTheDocument();
  });
});
