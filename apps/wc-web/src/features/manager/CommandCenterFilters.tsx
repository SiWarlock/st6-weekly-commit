import type {
  PlanState,
  ReviewStatus,
  Priority,
  WorkType,
  AlignmentStatus,
} from '../../shared/lib/dtos';
import type { CommandCenterParams } from './managerApi';

const PLAN_STATES: PlanState[] = [
  'DRAFT',
  'LOCKED',
  'RECONCILING',
  'RECONCILED',
];
// Includes the derived OVERDUE filter (server-side on isReviewOverdue, §9).
const REVIEW_STATES: (ReviewStatus | 'OVERDUE')[] = [
  'NOT_REVIEWED',
  'REVIEWED_WITH_DISPUTES',
  'REVIEWED',
  'OVERDUE',
];
const PRIORITIES: Priority[] = ['P0', 'P1', 'P2'];
const WORK_TYPES: WorkType[] = [
  'STRATEGIC',
  'MAINTENANCE',
  'BLOCKER',
  'UNPLANNED',
];
const ALIGNMENTS: AlignmentStatus[] = ['ALIGNED', 'NEEDS_REVIEW', 'MISALIGNED'];

/**
 * The chip-able (removable) filter keys (ST.6b). `weekStart` is required + the
 * default the Filters component doesn't own → never a chip, never cleared.
 * `page`/`size`/`sort` are pagination, not user filters.
 */
const CHIP_FILTERS: { key: keyof CommandCenterParams; label: string }[] = [
  { key: 'employeeId', label: 'Person' },
  { key: 'planState', label: 'Plan state' },
  { key: 'reviewState', label: 'Review state' },
  { key: 'definingObjectiveId', label: 'Defining objective' },
  { key: 'priority', label: 'Priority' },
  { key: 'workType', label: 'Work type' },
  { key: 'alignmentStatus', label: 'Alignment status' },
];

const CLEAR_ALL_PATCH: Partial<CommandCenterParams> = {
  employeeId: undefined,
  planState: undefined,
  reviewState: undefined,
  definingObjectiveId: undefined,
  priority: undefined,
  workType: undefined,
  alignmentStatus: undefined,
};

function FilterSelect({
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
        <option value="">All</option>
        {options.map((opt) => (
          <option key={opt} value={opt}>
            {opt}
          </option>
        ))}
      </select>
    </label>
  );
}

/**
 * The E13 command-center filter controls (REQ-F-023). Controlled: reads the
 * current `CommandCenterParams` and emits a patch on each change. `weekStart` is
 * required (UX-only; the server is authoritative). `reviewState=OVERDUE` passes
 * through verbatim — the client never re-derives overdue (server filters on the
 * derived `isReviewOverdue`). Visual polish (layout/grouping) is the ST.6 pass.
 */
export function CommandCenterFilters({
  value,
  onChange,
}: {
  value: CommandCenterParams;
  onChange: (patch: Partial<CommandCenterParams>) => void;
}) {
  const activeChips = CHIP_FILTERS.filter(({ key }) => {
    const v = value[key];
    return v !== undefined && v !== '';
  });
  return (
    <div data-cy="command-center-filters" className="space-y-3">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <label htmlFor="cc-week-of" className="flex flex-col gap-1">
          <span className="text-label text-ink-secondary">Week of</span>
          <input
            id="cc-week-of"
            type="date"
            required
            value={value.weekStart}
            onChange={(e) => onChange({ weekStart: e.target.value })}
            className="rounded-md border border-border bg-surface-raised px-3 py-2 text-body text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
          />
        </label>

        <label htmlFor="cc-employee" className="flex flex-col gap-1">
          <span className="text-label text-ink-secondary">Person</span>
          <input
            id="cc-employee"
            type="text"
            value={value.employeeId ?? ''}
            onChange={(e) =>
              onChange({ employeeId: e.target.value || undefined })
            }
            className="rounded-md border border-border bg-surface-raised px-3 py-2 text-body text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
          />
        </label>

        <FilterSelect
          id="cc-plan-state"
          label="Plan state"
          value={value.planState ?? ''}
          options={PLAN_STATES}
          onChange={(v) =>
            onChange({ planState: (v || undefined) as PlanState | undefined })
          }
        />
        <FilterSelect
          id="cc-review-state"
          label="Review state"
          value={value.reviewState ?? ''}
          options={REVIEW_STATES}
          onChange={(v) =>
            onChange({
              reviewState: (v || undefined) as
                | ReviewStatus
                | 'OVERDUE'
                | undefined,
            })
          }
        />

        <label htmlFor="cc-do" className="flex flex-col gap-1">
          <span className="text-label text-ink-secondary">
            Defining objective
          </span>
          <input
            id="cc-do"
            type="text"
            value={value.definingObjectiveId ?? ''}
            onChange={(e) =>
              onChange({ definingObjectiveId: e.target.value || undefined })
            }
            className="rounded-md border border-border bg-surface-raised px-3 py-2 text-body text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
          />
        </label>

        <FilterSelect
          id="cc-priority"
          label="Priority"
          value={value.priority ?? ''}
          options={PRIORITIES}
          onChange={(v) =>
            onChange({ priority: (v || undefined) as Priority | undefined })
          }
        />
        <FilterSelect
          id="cc-work-type"
          label="Work type"
          value={value.workType ?? ''}
          options={WORK_TYPES}
          onChange={(v) =>
            onChange({ workType: (v || undefined) as WorkType | undefined })
          }
        />
        <FilterSelect
          id="cc-alignment"
          label="Alignment status"
          value={value.alignmentStatus ?? ''}
          options={ALIGNMENTS}
          onChange={(v) =>
            onChange({
              alignmentStatus: (v || undefined) as AlignmentStatus | undefined,
            })
          }
        />
      </div>

      {activeChips.length > 0 ? (
        <div
          data-cy="active-filter-chips"
          className="flex flex-wrap items-center gap-2"
        >
          {activeChips.map(({ key, label }) => (
            <span
              key={key}
              data-cy={`filter-chip-${key}`}
              className="inline-flex items-center gap-1 rounded-full bg-brand-soft px-2 py-0.5 text-meta text-brand-400"
            >
              <span>
                {label}: {String(value[key])}
              </span>
              <button
                type="button"
                aria-label={`Remove ${label} filter`}
                onClick={() =>
                  onChange({ [key]: undefined } as Partial<CommandCenterParams>)
                }
                className="rounded-full px-1 leading-none hover:text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
              >
                ×
              </button>
            </span>
          ))}
          <button
            type="button"
            data-cy="clear-all-filters"
            onClick={() => onChange(CLEAR_ALL_PATCH)}
            className="text-meta text-ink-secondary underline hover:text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
          >
            Clear all
          </button>
        </div>
      ) : null}
    </div>
  );
}
