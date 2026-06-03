import type {
  Priority,
  WorkType,
  Confidence,
  AlignmentStatus,
  PlanState,
} from '../../shared/lib/dtos';

export interface ChessValue {
  priority: Priority;
  workType: WorkType;
  confidence: Confidence;
  alignmentStatus: AlignmentStatus;
}

const PRIORITIES: Priority[] = ['P0', 'P1', 'P2'];
// Planned-only: UNPLANNED is omitted — the server rejects it on this endpoint.
const PLANNED_WORK_TYPES: WorkType[] = ['STRATEGIC', 'MAINTENANCE', 'BLOCKER'];
const CONFIDENCES: Confidence[] = ['HIGH', 'MEDIUM', 'LOW'];
const ALIGNMENTS: AlignmentStatus[] = ['ALIGNED', 'NEEDS_REVIEW', 'MISALIGNED'];

const LABELS: Record<string, string> = {
  P0: 'P0 — top',
  P1: 'P1',
  P2: 'P2',
  STRATEGIC: 'Strategic',
  MAINTENANCE: 'Maintenance',
  BLOCKER: 'Blocker',
  HIGH: 'High',
  MEDIUM: 'Medium',
  LOW: 'Low',
  ALIGNED: 'Aligned',
  NEEDS_REVIEW: 'Needs review',
  MISALIGNED: 'Misaligned',
};

function Select({
  id,
  label,
  value,
  options,
  onChange,
}: {
  id: string;
  label: string;
  value: string;
  options: string[];
  onChange: (next: string) => void;
}) {
  return (
    <label htmlFor={id} className="flex flex-col gap-1">
      <span className="text-label text-ink-secondary">{label}</span>
      <select
        id={id}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="rounded-md border border-border bg-surface-raised px-3 py-2 text-body text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
      >
        {options.map((opt) => (
          <option key={opt} value={opt}>
            {LABELS[opt] ?? opt}
          </option>
        ))}
      </select>
    </label>
  );
}

/**
 * The chess layer (§3) — priority / workType / confidence / alignmentStatus.
 * `alignmentStatus` is the IC self-assessment: editable while DRAFT, but
 * **read-only static text once the plan is past DRAFT** (baseline-immutable, §3) —
 * rendered as labelled text, not a disabled input, so it reads clearly as frozen.
 */
export function ChessLayerFields({
  value,
  onChange,
  planState,
  hideWorkType = false,
}: {
  value: ChessValue;
  onChange: (patch: Partial<ChessValue>) => void;
  planState: PlanState;
  /**
   * Unplanned mode (E11): omit the WorkType field — the server forces
   * `workType=UNPLANNED`, so there is nothing to choose.
   */
  hideWorkType?: boolean;
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <Select
        id="commitment-priority"
        label="Priority"
        value={value.priority}
        options={PRIORITIES}
        onChange={(v) => onChange({ priority: v as Priority })}
      />
      {hideWorkType ? null : (
        <Select
          id="commitment-work-type"
          label="Work type"
          value={value.workType}
          options={PLANNED_WORK_TYPES}
          onChange={(v) => onChange({ workType: v as WorkType })}
        />
      )}
      <Select
        id="commitment-confidence"
        label="Confidence"
        value={value.confidence}
        options={CONFIDENCES}
        onChange={(v) => onChange({ confidence: v as Confidence })}
      />
      {planState === 'DRAFT' ? (
        <Select
          id="commitment-alignment"
          label="Alignment"
          value={value.alignmentStatus}
          options={ALIGNMENTS}
          onChange={(v) => onChange({ alignmentStatus: v as AlignmentStatus })}
        />
      ) : (
        <div className="flex flex-col gap-1">
          <span className="text-label text-ink-secondary">Alignment</span>
          <p
            data-cy="alignment-readonly"
            className="py-2 text-body text-ink-primary"
          >
            {LABELS[value.alignmentStatus] ?? value.alignmentStatus}
          </p>
        </div>
      )}
    </div>
  );
}
