import { useGetRcdoQuery } from '../rcdo/rcdoApi';
import { FilterDropdown, type FilterOption } from './FilterDropdown';
import {
  PLAN_STATE_TAXONOMY,
  REVIEW_STATUS_TAXONOMY,
  PRIORITY_TAXONOMY,
  WORKTYPE_TAXONOMY,
  ALIGNMENT_TAXONOMY,
} from '../../shared/lib/statusTaxonomy';
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

/** Map enum values → {value,label} options via the taxonomy label (no inline re-map). */
function opts(
  values: string[],
  labelMap: Record<string, { label: string }>,
): FilterOption[] {
  return values.map((v) => ({ value: v, label: labelMap[v]?.label ?? v }));
}

/** All chip-able filter keys (in mockup order). `weekStart`/`page`/`size`/`sort`
 * are not user filters → never chips (the week affordance lives in the app-bar). */
interface FilterDef {
  key: keyof CommandCenterParams;
  label: string;
  options: FilterOption[];
}

const CLEAR_ALL_PATCH: Partial<CommandCenterParams> = {
  employeeId: undefined,
  planState: undefined,
  reviewState: undefined,
  definingObjectiveId: undefined,
  priority: undefined,
  workType: undefined,
  alignmentStatus: undefined,
};

/**
 * The E13 command-center filter controls (REQ-F-023), rebuilt (ST.8b-2) into the
 * compact dropdown-chip row (mockup §B.2): a `Filters` label + a hand-rolled
 * `FilterDropdown` per filter + removable active-filter chips + Clear-all + a
 * "Showing N of M reports" result count. Controlled — reads the current
 * `CommandCenterParams` and emits a patch on each change (the SAME plumbing as the
 * `<select>` grid; presentation-only). `reviewState=OVERDUE` passes through
 * verbatim (the client never re-derives overdue). The Person + Defining-objective
 * options are dynamic (the loaded reports + the RCDO read); the rest are enum
 * filters labeled via the taxonomy. `weekStart` is the required query default,
 * supplied by the parent — never a chip here (the week affordance is the app-bar/pager).
 */
export function CommandCenterFilters({
  value,
  onChange,
  reports = [],
  shown,
  total,
}: {
  value: CommandCenterParams;
  onChange: (patch: Partial<CommandCenterParams>) => void;
  reports?: { id: string; name: string }[];
  shown?: number;
  total?: number;
}) {
  const { data: rcdo } = useGetRcdoQuery();
  const doOptions: FilterOption[] = (rcdo?.rallyCries ?? [])
    .flatMap((rc) => rc.definingObjectives)
    .map((d) => ({ value: d.id, label: d.title }));
  const personOptions: FilterOption[] = reports.map((r) => ({
    value: r.id,
    label: r.name,
  }));

  const filters: FilterDef[] = [
    { key: 'employeeId', label: 'Person', options: personOptions },
    {
      key: 'planState',
      label: 'Plan state',
      options: opts(PLAN_STATES, PLAN_STATE_TAXONOMY),
    },
    {
      key: 'reviewState',
      label: 'Review state',
      options: opts(REVIEW_STATES, REVIEW_STATUS_TAXONOMY),
    },
    {
      key: 'definingObjectiveId',
      label: 'Defining objective',
      options: doOptions,
    },
    {
      key: 'priority',
      label: 'Priority',
      options: opts(PRIORITIES, PRIORITY_TAXONOMY),
    },
    {
      key: 'workType',
      label: 'Work type',
      options: opts(WORK_TYPES, WORKTYPE_TAXONOMY),
    },
    {
      key: 'alignmentStatus',
      label: 'Alignment status',
      options: opts(ALIGNMENTS, ALIGNMENT_TAXONOMY),
    },
  ];

  const activeChips = filters.filter(({ key }) => {
    const v = value[key];
    return v !== undefined && v !== '';
  });

  return (
    <div data-cy="command-center-filters" className="space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-label text-ink-secondary">Filters</span>
        {filters.map((f) => (
          <FilterDropdown
            key={f.key}
            label={f.label}
            options={f.options}
            onSelect={(v) =>
              onChange({ [f.key]: v } as Partial<CommandCenterParams>)
            }
          />
        ))}
        {activeChips.length > 0 ? (
          <button
            type="button"
            data-cy="clear-all-filters"
            onClick={() => onChange(CLEAR_ALL_PATCH)}
            className="text-meta text-ink-secondary underline hover:text-ink-primary focus:outline-none focus:ring-2 focus:ring-brand-ring"
          >
            Clear all
          </button>
        ) : null}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        {activeChips.map(({ key, label, options }) => {
          const raw = String(value[key]);
          const display = options.find((o) => o.value === raw)?.label ?? raw;
          return (
            <span
              key={key}
              data-cy={`filter-chip-${key}`}
              className="inline-flex items-center gap-1 rounded-full bg-brand-soft px-2 py-0.5 text-meta text-brand-ink"
            >
              <span>
                {label}: {display}
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
          );
        })}
        {shown !== undefined && total !== undefined ? (
          <span data-cy="cc-result-count" className="text-meta text-ink-muted">
            {`Showing ${shown} of ${total} reports`}
          </span>
        ) : null}
      </div>
    </div>
  );
}
