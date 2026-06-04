import { Fragment, useState } from 'react';
import { Badge } from '../../shared/components/Badge';
import { DisputeRespondForm } from './DisputeRespondForm';
import { DisputeResolveAction } from './DisputeResolveAction';
import { useOpenDisputeMutation } from './disputesApi';
import { can } from '../../shared/lib/allowedActions';
import {
  DISPUTE_STATUS_TAXONOMY,
  FLAG_TYPE_LABEL,
} from '../../shared/lib/statusTaxonomy';
import type {
  DisputeStatus,
  FlagType,
  WeeklyCommitmentDto,
} from '../../shared/lib/dtos';
import type { ParsedProblem } from '../../shared/lib/problemDetails';

/** The dispute lifecycle in §3 order — the non-interactive 3-node stepper. */
type StepStatus = 'done' | 'active' | 'pending';
const DISPUTE_ORDER: DisputeStatus[] = ['OPEN', 'IC_RESPONDED', 'RESOLVED'];

function stepperNodes(
  status: DisputeStatus,
): { status: DisputeStatus; step: StepStatus }[] {
  const current = DISPUTE_ORDER.indexOf(status);
  return DISPUTE_ORDER.map((s, i) => ({
    status: s,
    step: i < current ? 'done' : i === current ? 'active' : 'pending',
  }));
}

const DOT_CLASS: Record<StepStatus, string> = {
  done: 'bg-brand-600 border-brand-600',
  active: 'border-brand-400 bg-surface ring-2 ring-brand-soft',
  pending: 'border-border-strong bg-surface',
};

/** Manager open form (E17) — shown when no dispute + OPEN_DISPUTE is allowed. */
function DisputeOpenForm({ commitment }: { commitment: WeeklyCommitmentDto }) {
  const [openDispute, { isLoading }] = useOpenDisputeMutation();
  const [flagType, setFlagType] = useState<FlagType>('NEEDS_REVISION');
  const [managerNote, setManagerNote] = useState('');
  const [problem, setProblem] = useState<ParsedProblem | null>(null);

  const hasNote = managerNote.trim().length > 0;

  async function handleOpen() {
    if (!hasNote) {
      return; // managerNote is required — client-block (server also validates)
    }
    setProblem(null);
    try {
      await openDispute({
        commitmentId: commitment.id,
        planId: commitment.weeklyPlanId,
        body: { flagType, managerNote: managerNote.trim() },
      }).unwrap();
    } catch (err) {
      setProblem(err as ParsedProblem);
    }
  }

  return (
    <div data-cy="dispute-open-form" className="flex flex-col gap-2">
      <label className="flex flex-col gap-1 text-meta text-ink-secondary">
        Flag type
        <select
          aria-label="Flag type"
          value={flagType}
          onChange={(e) => setFlagType(e.target.value as FlagType)}
          className="rounded-md border border-border-strong bg-surface px-2 py-1 text-body text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
        >
          <option value="NEEDS_REVISION">
            {FLAG_TYPE_LABEL.NEEDS_REVISION}
          </option>
          <option value="MISALIGNED">{FLAG_TYPE_LABEL.MISALIGNED}</option>
        </select>
      </label>
      <textarea
        aria-label="Manager note"
        value={managerNote}
        onChange={(e) => setManagerNote(e.target.value)}
        rows={2}
        placeholder="Why is this commitment flagged?"
        className="w-full rounded-md border border-border-strong bg-surface px-3 py-2 text-body text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
      />
      <div className="flex justify-end">
        <button
          type="button"
          disabled={isLoading || !hasNote}
          onClick={handleOpen}
          className="rounded-md bg-brand-600 px-4 py-2 text-label font-semibold text-white hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-ring disabled:cursor-not-allowed disabled:opacity-50"
        >
          Open dispute
        </button>
      </div>
      {problem ? (
        <p
          data-cy="dispute-open-error"
          role="alert"
          className="rounded-md border border-tone-failure-border bg-tone-failure-bg p-2 text-meta text-tone-failure-fg"
        >
          {problem.safeMessage}
        </p>
      ) : null}
    </div>
  );
}

/**
 * Per-commitment alignment-dispute surface (9.11a), mounted in `CommitmentList`.
 * Self-sources everything from the `WeeklyCommitmentDto` (planId =
 * `commitment.weeklyPlanId`, the dispute = `commitment.dispute`) so any surface
 * rendering a commitment mounts it as a one-liner (the 5.5b manager-plan-view).
 * No dispute → the manager open form (gated `OPEN_DISPUTE`, §11; dormant until
 * 5.5b). A dispute → the OPEN→IC_RESPONDED→RESOLVED lifecycle stepper + the
 * status/flagType/note/response display (React-escaped, REQ-S-005) + the gated
 * IC respond + manager resolve controls. A resolved dispute is `null` on the
 * wire (B.6) → reverts to the open-form branch on refetch.
 */
export function DisputePanel({
  commitment,
}: {
  commitment: WeeklyCommitmentDto;
}) {
  const dispute = commitment.dispute;

  if (!dispute) {
    return can('OPEN_DISPUTE', commitment.allowedActions) ? (
      <DisputeOpenForm commitment={commitment} />
    ) : null;
  }

  const statusMeta = DISPUTE_STATUS_TAXONOMY[dispute.status];

  return (
    <div
      data-cy="dispute-panel"
      className="flex flex-col gap-2 rounded-md border border-tone-failure-border bg-surface-sunken p-3"
    >
      {/* Forward-only dispute lifecycle stepper (reuses the ST.5a pattern). */}
      <div
        data-cy="dispute-stepper"
        role="group"
        aria-label="Dispute lifecycle"
        className="flex max-w-md items-center gap-1"
      >
        {stepperNodes(dispute.status).map((n, i, arr) => (
          <Fragment key={n.status}>
            <div
              data-cy="dispute-step"
              data-step={n.status}
              data-status={n.step}
              className="flex items-center gap-1"
            >
              <span
                aria-hidden
                className={`h-3 w-3 rounded-full border-2 ${DOT_CLASS[n.step]}`}
              />
              <span
                className={`text-meta ${n.step === 'pending' ? 'text-ink-muted' : 'text-ink-primary'}`}
              >
                {DISPUTE_STATUS_TAXONOMY[n.status]?.label ?? n.status}
              </span>
            </div>
            {i < arr.length - 1 ? (
              <span
                aria-hidden
                className={`h-0.5 flex-1 ${n.step === 'done' ? 'bg-brand-600' : 'bg-border-strong'}`}
              />
            ) : null}
          </Fragment>
        ))}
      </div>

      {/* Display — status pill + flag reason + manager note + IC response. */}
      <div className="flex flex-wrap items-center gap-2">
        {statusMeta ? (
          <Badge
            tone={statusMeta.tone}
            icon={statusMeta.icon}
            label={statusMeta.label}
            size="xs"
            dataCy="dispute-status"
          />
        ) : null}
        <span className="text-meta text-ink-secondary">
          {FLAG_TYPE_LABEL[dispute.flagType] ?? dispute.flagType}
        </span>
      </div>
      <p className="text-body text-ink-primary">{dispute.managerNote}</p>
      {dispute.icResponse ? (
        <p
          data-cy="dispute-ic-response"
          className="text-body text-ink-secondary"
        >
          {dispute.icResponse}
        </p>
      ) : null}

      {/* Gated controls (server-authoritative; dormant until 5.5b). */}
      <DisputeRespondForm dispute={dispute} planId={commitment.weeklyPlanId} />
      <DisputeResolveAction
        dispute={dispute}
        planId={commitment.weeklyPlanId}
      />
    </div>
  );
}
