/**
 * ST.7a — contract-typed, persona-spanning demo fixtures (standalone-only).
 *
 * Every value here is typed as its Appendix-B DTO (via `dtos.ts` + the api-slice
 * DTOs), so TypeScript compile-enforces wire-contract fidelity — the mock is a
 * faithful double, not a drifting shape. The data spans the full styling range
 * the ST.7 QA needs: one IC persona per lifecycle state (DRAFT/LOCKED/
 * RECONCILING/RECONCILED), commitments across every chess-field value (+ unlinked
 * + carried-forward), a manager whose command-center/heatmap = those four ICs as
 * direct reports (one coherent demo org), a FAILED sync record, and a comment
 * thread. `allowedActions` follow the authoritative backend contract (per-state
 * plan-level arrays; per-commitment CARRY_FORWARD only on owned RECONCILING
 * non-carried commitments; manager-reader of a report's plan sees []).
 *
 * NOT exhaustive combinatorics — one rich, realistic scenario per persona.
 */
import type { MeDto } from '../../features/me/meApi';
import type { RcdoTreeDto } from '../../features/rcdo/rcdoApi';
import type {
  AllowedAction,
  CommentDto,
  HeatmapCellDto,
  HeatmapDrilldownDto,
  HeatmapResponseDto,
  ManagerCommandCenterRowDto,
  ManagerReviewDto,
  OutlookSyncRecordDto,
  PageEnvelope,
  PlanState,
  RcdoBreadcrumbDto,
  RiskBadge,
  WeeklyCommitmentDto,
  WeeklyPlanDto,
} from '../../shared/lib/dtos';

// ── Persona ids (must keep the existing ic-1 / mgr-1 ids — referenced by tests) ─
export const IC_1 = 'demo-employee-ic-1';
export const IC_2 = 'demo-employee-ic-2';
export const IC_3 = 'demo-employee-ic-3';
export const IC_4 = 'demo-employee-ic-4';
export const MGR_1 = 'demo-employee-mgr-1';

const WEEK_START = '2026-06-01';
const WEEK_END = '2026-06-07';
const MGR_ID = MGR_1;

// ── RCDO (read-only reference tree) ──────────────────────────────────────────
const RC_ID = 'rc-1';
const DO_1 = 'do-1';
const DO_2 = 'do-2';
const DO_3 = 'do-3';
const SO_1_1 = 'so-1-1';
const SO_1_2 = 'so-1-2';
const SO_2_1 = 'so-2-1';
const SO_2_2 = 'so-2-2';
const SO_3_1 = 'so-3-1';
const SO_3_2 = 'so-3-2';

const RC_TITLE =
  'Become the system of record every execution-driven team trusts.';

interface SoMeta {
  id: string;
  doId: string;
  doTitle: string;
  title: string;
}
const SO_META: Record<string, SoMeta> = {
  [SO_1_1]: {
    id: SO_1_1,
    doId: DO_1,
    doTitle: 'Grow customer activation & expansion',
    title: 'Cut time-to-first-value to under a week',
  },
  [SO_1_2]: {
    id: SO_1_2,
    doId: DO_1,
    doTitle: 'Grow customer activation & expansion',
    title: 'Lift activation-to-paid conversion',
  },
  [SO_2_1]: {
    id: SO_2_1,
    doId: DO_2,
    doTitle: 'Operational excellence in delivery',
    title: 'Ship the weekly release train on time',
  },
  [SO_2_2]: {
    id: SO_2_2,
    doId: DO_2,
    doTitle: 'Operational excellence in delivery',
    title: 'Drive incident MTTR below an hour',
  },
  [SO_3_1]: {
    id: SO_3_1,
    doId: DO_3,
    doTitle: 'Platform reliability & trust',
    title: 'Sustain 99.95% API availability',
  },
  [SO_3_2]: {
    id: SO_3_2,
    doId: DO_3,
    doTitle: 'Platform reliability & trust',
    title: 'Close the SOC 2 control gaps',
  },
};

export const RCDO_TREE: RcdoTreeDto = {
  rallyCries: [
    {
      id: RC_ID,
      title: RC_TITLE,
      active: true,
      definingObjectives: [DO_1, DO_2, DO_3].map((doId) => {
        const sos = Object.values(SO_META).filter((s) => s.doId === doId);
        return {
          id: doId,
          rallyCryId: RC_ID,
          title: sos[0]?.doTitle ?? doId,
          active: true,
          supportingOutcomes: sos.map((s) => ({
            id: s.id,
            definingObjectiveId: doId,
            title: s.title,
            active: true,
          })),
        };
      }),
    },
  ],
};

function breadcrumb(soId: string): RcdoBreadcrumbDto {
  const so = SO_META[soId];
  if (!so) {
    throw new Error(`unknown SO ${soId}`);
  }
  return {
    rallyCryId: RC_ID,
    rallyCryTitle: RC_TITLE,
    definingObjectiveId: so.doId,
    definingObjectiveTitle: so.doTitle,
    supportingOutcomeId: so.id,
    supportingOutcomeTitle: so.title,
  };
}

// ── Identity ─────────────────────────────────────────────────────────────────
interface PersonaMeta {
  email: string;
  displayName: string;
  isManager: boolean;
}
const PERSON: Record<string, PersonaMeta> = {
  [IC_1]: {
    email: 'ivy.chen@st6demo.com',
    displayName: 'Ivy Chen',
    isManager: false,
  },
  [IC_2]: {
    email: 'ravi.patel@st6demo.com',
    displayName: 'Ravi Patel',
    isManager: false,
  },
  [IC_3]: {
    email: 'lena.ortiz@st6demo.com',
    displayName: 'Lena Ortiz',
    isManager: false,
  },
  [IC_4]: {
    email: 'tom.becker@st6demo.com',
    displayName: 'Tom Becker',
    isManager: false,
  },
  [MGR_1]: {
    email: 'morgan.lee@st6demo.com',
    displayName: 'Morgan Lee',
    isManager: true,
  },
};

export function isManagerPersona(personaId: string): boolean {
  return PERSON[personaId]?.isManager ?? false;
}

export function meForPersona(personaId: string): MeDto {
  const p = PERSON[personaId] ?? PERSON[IC_1]!;
  return {
    employeeId: personaId,
    email: p.email,
    displayName: p.displayName,
    role: p.isManager ? 'MANAGER' : 'IC',
    persona: personaId,
    isManager: p.isManager,
    timezone: 'America/Chicago',
  };
}

// ── Commitment builder ───────────────────────────────────────────────────────
let commitmentSeq = 0;
function commitment(
  planId: string,
  partial: Partial<WeeklyCommitmentDto> & Pick<WeeklyCommitmentDto, 'title'>,
): WeeklyCommitmentDto {
  commitmentSeq += 1;
  const soId = partial.supportingOutcomeId;
  return {
    id: partial.id ?? `commit-${planId}-${commitmentSeq}`,
    weeklyPlanId: planId,
    commitmentKind: partial.commitmentKind ?? 'PLANNED',
    title: partial.title,
    ...(partial.description !== undefined
      ? { description: partial.description }
      : {}),
    ...(soId !== undefined
      ? {
          supportingOutcomeId: soId,
          supportingOutcomeBreadcrumb: breadcrumb(soId),
        }
      : {}),
    priority: partial.priority ?? 'P1',
    workType: partial.workType ?? 'STRATEGIC',
    confidence: partial.confidence ?? 'MEDIUM',
    alignmentStatus: partial.alignmentStatus ?? 'ALIGNED',
    ...(partial.reconciliationOutcome !== undefined
      ? { reconciliationOutcome: partial.reconciliationOutcome }
      : {}),
    ...(partial.outcomeNote !== undefined
      ? { outcomeNote: partial.outcomeNote }
      : {}),
    ...(partial.carryForwardSourceCommitmentId !== undefined
      ? {
          carryForwardSourceCommitmentId:
            partial.carryForwardSourceCommitmentId,
        }
      : {}),
    ...(partial.dispute !== undefined ? { dispute: partial.dispute } : {}),
    allowedActions: partial.allowedActions ?? [],
    version: partial.version ?? 1,
  };
}

// `[CARRY_FORWARD]` on an owned RECONCILING commitment whose outcome isn't
// already CARRIED_FORWARD (the authoritative per-commitment contract).
const CF: AllowedAction[] = ['CARRY_FORWARD'];

// ── Per-persona plans (one lifecycle state each) ─────────────────────────────
const PLAN_IC_1 = 'plan-ic-1';
const PLAN_IC_2 = 'plan-ic-2';
const PLAN_IC_3 = 'plan-ic-3';
const PLAN_IC_4 = 'plan-ic-4';

const REVIEW_IC_2 = 'review-ic-2';
const REVIEW_IC_3 = 'review-ic-3';
const REVIEW_IC_4 = 'review-ic-4';

function review(
  id: string,
  planId: string,
  partial: Partial<ManagerReviewDto>,
): ManagerReviewDto {
  return {
    id,
    weeklyPlanId: planId,
    managerEmployeeId: MGR_ID,
    status: partial.status ?? 'NOT_REVIEWED',
    reviewDueAt: partial.reviewDueAt ?? '2026-06-09T17:00:00Z',
    isOverdue: partial.isOverdue ?? false,
    ...(partial.reviewedAt !== undefined
      ? { reviewedAt: partial.reviewedAt }
      : {}),
    ...(partial.summaryNote !== undefined
      ? { summaryNote: partial.summaryNote }
      : {}),
    unresolvedDisputeCount: partial.unresolvedDisputeCount ?? 0,
    allowedActions: partial.allowedActions ?? ['MARK_REVIEWED'],
    version: partial.version ?? 1,
  };
}

// ic-1 — DRAFT: not yet lockable (one deliberately unlinked commitment).
const planIc1: WeeklyPlanDto = {
  id: PLAN_IC_1,
  employeeId: IC_1,
  employeeDisplayName: PERSON[IC_1]!.displayName,
  weekStartDate: WEEK_START,
  weekEndDate: WEEK_END,
  state: 'DRAFT',
  generatedAt: '2026-06-01T08:00:00Z',
  plannedCount: 3,
  unplannedCount: 0,
  commitments: [
    commitment(PLAN_IC_1, {
      title: 'Launch the guided-onboarding checklist',
      supportingOutcomeId: SO_1_1,
      priority: 'P0',
      workType: 'STRATEGIC',
      confidence: 'HIGH',
      alignmentStatus: 'ALIGNED',
    }),
    commitment(PLAN_IC_1, {
      title: 'Triage the activation funnel drop-off',
      supportingOutcomeId: SO_1_2,
      priority: 'P1',
      workType: 'MAINTENANCE',
      confidence: 'MEDIUM',
      alignmentStatus: 'NEEDS_REVIEW',
    }),
    commitment(PLAN_IC_1, {
      title: 'Spike: evaluate in-app product-tour vendors',
      priority: 'P2',
      workType: 'STRATEGIC',
      confidence: 'LOW',
      alignmentStatus: 'ALIGNED',
      // deliberately UNLINKED (no supportingOutcomeId) — lock would be blocked.
    }),
  ],
  managerReview: null,
  allowedActions: ['LOCK'],
  version: 2,
};

// ic-2 — LOCKED: review pending (not overdue); a BLOCKER + a MISALIGNED.
const planIc2: WeeklyPlanDto = {
  id: PLAN_IC_2,
  employeeId: IC_2,
  employeeDisplayName: PERSON[IC_2]!.displayName,
  weekStartDate: WEEK_START,
  weekEndDate: WEEK_END,
  state: 'LOCKED',
  generatedAt: '2026-06-01T08:00:00Z',
  lockedAt: '2026-06-01T15:00:00Z',
  plannedCount: 3,
  unplannedCount: 0,
  commitments: [
    commitment(PLAN_IC_2, {
      title: 'Stabilize the release-train cutover',
      supportingOutcomeId: SO_2_1,
      priority: 'P0',
      workType: 'STRATEGIC',
      confidence: 'HIGH',
      alignmentStatus: 'ALIGNED',
    }),
    commitment(PLAN_IC_2, {
      title: 'Unblock deploys stuck on flaky end-to-end tests',
      supportingOutcomeId: SO_2_2,
      priority: 'P1',
      workType: 'BLOCKER',
      confidence: 'MEDIUM',
      alignmentStatus: 'MISALIGNED',
    }),
    commitment(PLAN_IC_2, {
      title: 'Document the on-call rotation handbook',
      supportingOutcomeId: SO_2_2,
      priority: 'P2',
      workType: 'MAINTENANCE',
      confidence: 'HIGH',
      alignmentStatus: 'ALIGNED',
    }),
  ],
  managerReview: review(REVIEW_IC_2, PLAN_IC_2, {
    status: 'NOT_REVIEWED',
    reviewDueAt: '2026-06-09T17:00:00Z',
    isOverdue: false,
  }),
  allowedActions: ['START_RECONCILIATION', 'ADD_UNPLANNED'],
  version: 3,
};

// ic-3 — RECONCILING: overdue review + a dispute + carried-forward + unplanned.
const planIc3: WeeklyPlanDto = {
  id: PLAN_IC_3,
  employeeId: IC_3,
  employeeDisplayName: PERSON[IC_3]!.displayName,
  weekStartDate: WEEK_START,
  weekEndDate: WEEK_END,
  state: 'RECONCILING',
  generatedAt: '2026-06-01T08:00:00Z',
  lockedAt: '2026-06-01T15:00:00Z',
  reconciliationStartedAt: '2026-06-05T16:00:00Z',
  plannedCount: 3,
  unplannedCount: 1,
  commitments: [
    commitment(PLAN_IC_3, {
      title: 'Sustain 99.95% API availability through June',
      supportingOutcomeId: SO_3_1,
      priority: 'P0',
      workType: 'STRATEGIC',
      confidence: 'HIGH',
      alignmentStatus: 'ALIGNED',
      reconciliationOutcome: 'COMPLETED',
      allowedActions: CF,
    }),
    commitment(PLAN_IC_3, {
      title: 'Remediate the top SOC 2 control findings',
      supportingOutcomeId: SO_3_2,
      priority: 'P1',
      workType: 'STRATEGIC',
      confidence: 'MEDIUM',
      alignmentStatus: 'NEEDS_REVIEW',
      reconciliationOutcome: 'CARRIED_FORWARD',
      // An OPEN alignment dispute (9.11a) so the standalone demo shows the
      // disputed state (failure left-accent + the dispute panel). The dispute's
      // own allowedActions are empty (dormant until backend 5.5b emits them).
      dispute: {
        id: 'dispute-ic-3-soc2',
        commitmentId: 'commit-ic-3-soc2',
        managerEmployeeId: MGR_1,
        status: 'OPEN',
        flagType: 'MISALIGNED',
        managerNote:
          'This reads as a maintenance task — re-link it to the reliability outcome or re-scope.',
        allowedActions: [],
        version: 0,
      },
      // already carried out → no further CARRY_FORWARD affordance.
      allowedActions: [],
    }),
    commitment(PLAN_IC_3, {
      title: 'Hotfix the auth token-refresh regression',
      supportingOutcomeId: SO_3_1,
      commitmentKind: 'UNPLANNED',
      priority: 'P0',
      workType: 'UNPLANNED',
      confidence: 'HIGH',
      alignmentStatus: 'ALIGNED',
      reconciliationOutcome: 'BLOCKED',
      allowedActions: CF,
    }),
    commitment(PLAN_IC_3, {
      title: 'Finish the activation-onboarding runbook',
      supportingOutcomeId: SO_1_1,
      priority: 'P1',
      workType: 'MAINTENANCE',
      confidence: 'MEDIUM',
      alignmentStatus: 'ALIGNED',
      carryForwardSourceCommitmentId: 'commit-prior-week-runbook',
      allowedActions: CF,
    }),
  ],
  managerReview: review(REVIEW_IC_3, PLAN_IC_3, {
    status: 'NOT_REVIEWED',
    reviewDueAt: '2026-06-04T17:00:00Z',
    isOverdue: true,
    unresolvedDisputeCount: 1,
  }),
  allowedActions: ['CLOSE_RECONCILIATION', 'ADD_UNPLANNED'],
  version: 5,
};

// ic-4 — RECONCILED: reviewed; full set of reconciliation outcomes.
const planIc4: WeeklyPlanDto = {
  id: PLAN_IC_4,
  employeeId: IC_4,
  employeeDisplayName: PERSON[IC_4]!.displayName,
  weekStartDate: WEEK_START,
  weekEndDate: WEEK_END,
  state: 'RECONCILED',
  generatedAt: '2026-06-01T08:00:00Z',
  lockedAt: '2026-06-01T15:00:00Z',
  reconciliationStartedAt: '2026-06-05T16:00:00Z',
  reconciledAt: '2026-06-06T18:00:00Z',
  plannedCount: 3,
  unplannedCount: 0,
  commitments: [
    commitment(PLAN_IC_4, {
      title: 'Cut time-to-first-value below one week',
      supportingOutcomeId: SO_1_1,
      priority: 'P1',
      workType: 'STRATEGIC',
      confidence: 'HIGH',
      alignmentStatus: 'ALIGNED',
      reconciliationOutcome: 'COMPLETED',
    }),
    commitment(PLAN_IC_4, {
      title: 'Lift activation-to-paid conversion by 3 points',
      supportingOutcomeId: SO_1_2,
      priority: 'P2',
      workType: 'STRATEGIC',
      confidence: 'MEDIUM',
      alignmentStatus: 'NEEDS_REVIEW',
      reconciliationOutcome: 'PARTIALLY_COMPLETED',
    }),
    commitment(PLAN_IC_4, {
      title: 'Migrate the legacy billing webhooks',
      supportingOutcomeId: SO_2_1,
      priority: 'P1',
      workType: 'MAINTENANCE',
      confidence: 'LOW',
      alignmentStatus: 'ALIGNED',
      reconciliationOutcome: 'CANCELED',
    }),
  ],
  managerReview: review(REVIEW_IC_4, PLAN_IC_4, {
    status: 'REVIEWED',
    reviewDueAt: '2026-06-09T17:00:00Z',
    isOverdue: false,
    reviewedAt: '2026-06-07T14:00:00Z',
    summaryNote: 'Strong week — solid follow-through on the activation goals.',
    allowedActions: [],
  }),
  allowedActions: [],
  version: 6,
};

/** Every scenario plan (one per lifecycle state) — the coverage surface. */
export const ALL_PLANS: WeeklyPlanDto[] = [planIc1, planIc2, planIc3, planIc4];

const PLAN_BY_PERSONA: Record<string, WeeklyPlanDto> = {
  [IC_1]: planIc1,
  [IC_2]: planIc2,
  [IC_3]: planIc3,
  [IC_4]: planIc4,
};
const PLAN_BY_ID: Record<string, WeeklyPlanDto> = {
  [PLAN_IC_1]: planIc1,
  [PLAN_IC_2]: planIc2,
  [PLAN_IC_3]: planIc3,
  [PLAN_IC_4]: planIc4,
};

/** The IC persona's own current plan (E3). Falls back to ic-1 for safety. */
export function planForPersona(personaId: string): WeeklyPlanDto {
  return PLAN_BY_PERSONA[personaId] ?? planIc1;
}

/**
 * A plan by id (E4). When the reader is NOT the owner (a manager reading a
 * report's plan via the review Drawer), the plan-level `allowedActions` are []
 * (owner-only contract); the nested `managerReview` is preserved.
 */
export function planById(
  planId: string,
  readerPersonaId?: string,
): WeeklyPlanDto | undefined {
  const plan = PLAN_BY_ID[planId];
  if (!plan) {
    return undefined;
  }
  if (readerPersonaId && readerPersonaId !== plan.employeeId) {
    return { ...plan, allowedActions: [] };
  }
  return plan;
}

// ── Manager command center (the 4 ICs as Morgan's direct reports) ────────────
function row(
  partial: Partial<ManagerCommandCenterRowDto> & {
    employeeId: string;
    employeeDisplayName: string;
    weeklyPlanId: string;
    planState: PlanState;
  },
): ManagerCommandCenterRowDto {
  return {
    managerEmployeeId: MGR_ID,
    employeeId: partial.employeeId,
    employeeDisplayName: partial.employeeDisplayName,
    weeklyPlanId: partial.weeklyPlanId,
    weekStartDate: WEEK_START,
    planState: partial.planState,
    ...(partial.reviewStatus !== undefined
      ? { reviewStatus: partial.reviewStatus }
      : {}),
    ...(partial.reviewDueAt !== undefined
      ? { reviewDueAt: partial.reviewDueAt }
      : {}),
    isReviewOverdue: partial.isReviewOverdue ?? false,
    plannedCount: partial.plannedCount ?? 0,
    unplannedCount: partial.unplannedCount ?? 0,
    misalignedCount: partial.misalignedCount ?? 0,
    needsReviewCount: partial.needsReviewCount ?? 0,
    blockedCount: partial.blockedCount ?? 0,
    carryForwardCount: partial.carryForwardCount ?? 0,
    unresolvedDisputeCount: partial.unresolvedDisputeCount ?? 0,
    updatedAt: partial.updatedAt ?? '2026-06-06T18:00:00Z',
  };
}

const COMMAND_CENTER_ROWS: ManagerCommandCenterRowDto[] = [
  row({
    employeeId: IC_1,
    employeeDisplayName: PERSON[IC_1]!.displayName,
    weeklyPlanId: PLAN_IC_1,
    planState: 'DRAFT',
    plannedCount: 3,
    needsReviewCount: 1,
  }),
  row({
    employeeId: IC_2,
    employeeDisplayName: PERSON[IC_2]!.displayName,
    weeklyPlanId: PLAN_IC_2,
    planState: 'LOCKED',
    reviewStatus: 'NOT_REVIEWED',
    reviewDueAt: '2026-06-09T17:00:00Z',
    isReviewOverdue: false,
    plannedCount: 3,
    misalignedCount: 1,
    blockedCount: 1,
  }),
  row({
    employeeId: IC_3,
    employeeDisplayName: PERSON[IC_3]!.displayName,
    weeklyPlanId: PLAN_IC_3,
    planState: 'RECONCILING',
    reviewStatus: 'NOT_REVIEWED',
    reviewDueAt: '2026-06-04T17:00:00Z',
    isReviewOverdue: true,
    plannedCount: 3,
    unplannedCount: 1,
    needsReviewCount: 1,
    carryForwardCount: 1,
    unresolvedDisputeCount: 1,
  }),
  row({
    employeeId: IC_4,
    employeeDisplayName: PERSON[IC_4]!.displayName,
    weeklyPlanId: PLAN_IC_4,
    planState: 'RECONCILED',
    reviewStatus: 'REVIEWED',
    isReviewOverdue: false,
    plannedCount: 3,
    needsReviewCount: 1,
  }),
];

export function commandCenterPage(): PageEnvelope<ManagerCommandCenterRowDto> {
  return {
    content: COMMAND_CENTER_ROWS,
    page: {
      number: 0,
      size: 25,
      totalElements: COMMAND_CENTER_ROWS.length,
      totalPages: 1,
    },
    sort: [{ property: 'weekStartDate', direction: 'DESC' }],
  };
}

// ── Manager heatmap (report × Defining-Objective; varied volume + badges) ─────
const CELL_DO: { doId: string; doTitle: string }[] = [
  { doId: DO_1, doTitle: SO_META[SO_1_1]!.doTitle },
  { doId: DO_2, doTitle: SO_META[SO_2_1]!.doTitle },
  { doId: DO_3, doTitle: SO_META[SO_3_1]!.doTitle },
];

function cell(
  employeeId: string,
  doId: string,
  doTitle: string,
  commitmentCount: number,
  riskBadges: RiskBadge[],
  counts: Partial<HeatmapCellDto> = {},
): HeatmapCellDto {
  return {
    cellId: `cell-${employeeId}-${doId}`,
    managerEmployeeId: MGR_ID,
    employeeId,
    employeeDisplayName: PERSON[employeeId]!.displayName,
    weekStartDate: WEEK_START,
    definingObjectiveId: doId,
    definingObjectiveTitle: doTitle,
    commitmentCount,
    plannedCount: counts.plannedCount ?? commitmentCount,
    unplannedCount: counts.unplannedCount ?? 0,
    misalignedCount: counts.misalignedCount ?? 0,
    needsReviewCount: counts.needsReviewCount ?? 0,
    blockedCount: counts.blockedCount ?? 0,
    carryForwardCount: counts.carryForwardCount ?? 0,
    unresolvedDisputeCount: counts.unresolvedDisputeCount ?? 0,
    riskBadges,
  };
}

// Volume spans none/light/normal/heavy; badges span all six RiskBadge values.
const HEATMAP_CELLS: HeatmapCellDto[] = [
  cell(IC_1, DO_1, CELL_DO[0]!.doTitle, 2, []),
  cell(IC_1, DO_2, CELL_DO[1]!.doTitle, 0, []),
  cell(IC_1, DO_3, CELL_DO[2]!.doTitle, 1, [], { needsReviewCount: 1 }),
  cell(IC_2, DO_1, CELL_DO[0]!.doTitle, 0, []),
  cell(IC_2, DO_2, CELL_DO[1]!.doTitle, 3, ['MISALIGNED', 'BLOCKED'], {
    misalignedCount: 1,
    blockedCount: 1,
  }),
  cell(IC_2, DO_3, CELL_DO[2]!.doTitle, 0, []),
  cell(IC_3, DO_1, CELL_DO[0]!.doTitle, 1, ['CARRY_FORWARD'], {
    carryForwardCount: 1,
  }),
  cell(IC_3, DO_2, CELL_DO[1]!.doTitle, 0, []),
  cell(IC_3, DO_3, CELL_DO[2]!.doTitle, 4, [
    'NEEDS_REVIEW',
    'OVERDUE_REVIEW',
    'UNREVIEWED',
  ]),
  cell(IC_4, DO_1, CELL_DO[0]!.doTitle, 2, ['NEEDS_REVIEW'], {
    needsReviewCount: 1,
  }),
  cell(IC_4, DO_2, CELL_DO[1]!.doTitle, 1, []),
  cell(IC_4, DO_3, CELL_DO[2]!.doTitle, 0, []),
];

export function heatmapResponse(): HeatmapResponseDto {
  return { weekStart: WEEK_START, cells: HEATMAP_CELLS };
}

/**
 * E15 drilldown for a cell — the SO→commitment breakdown for that cell's DO.
 * Derives the DO from the `cell-<employeeId>-<doId>` id; returns the DO's SO
 * groups, each populated from the report's matching commitments (or a sample).
 */
export function drilldownForCell(cellId: string): HeatmapDrilldownDto {
  const found = HEATMAP_CELLS.find((c) => c.cellId === cellId);
  const employeeId = found?.employeeId ?? IC_3;
  const doId = found?.definingObjectiveId ?? DO_3;
  const sos = Object.values(SO_META).filter((s) => s.doId === doId);
  const reportPlan = PLAN_BY_PERSONA[employeeId];
  return {
    cellId,
    employeeId,
    definingObjectiveId: doId,
    supportingOutcomes: sos.map((so) => {
      const commitments = (reportPlan?.commitments ?? []).filter(
        (c) => c.supportingOutcomeId === so.id,
      );
      const content: WeeklyCommitmentDto[] =
        commitments.length > 0
          ? commitments.map((c) => ({ ...c, allowedActions: [] }))
          : [
              commitment(reportPlan?.id ?? PLAN_IC_3, {
                title: `Work toward: ${so.title}`,
                supportingOutcomeId: so.id,
              }),
            ];
      return {
        supportingOutcomeId: so.id,
        supportingOutcomeTitle: so.title,
        commitments: {
          content,
          page: {
            number: 0,
            size: 25,
            totalElements: content.length,
            totalPages: 1,
          },
          sort: [{ property: 'priority', direction: 'ASC' }],
        },
      };
    }),
  };
}

// ── Outlook sync records (incl. a FAILED with retry affordance) ──────────────
const SYNC_BY_PLAN: Record<string, OutlookSyncRecordDto[]> = {
  [PLAN_IC_2]: [
    {
      id: 'sync-ic-2-failed',
      ownerEmployeeId: IC_2,
      relatedType: 'WEEKLY_PLAN',
      relatedId: PLAN_IC_2,
      eventKind: 'IC_PLANNING',
      weekStartDate: WEEK_START,
      status: 'FAILED',
      failureCode: 'GRAPH_FORBIDDEN',
      safeMessage: 'Calendar sync failed; you can retry.',
      retryCount: 1,
      allowedActions: ['RETRY_SYNC'],
      version: 2,
    },
  ],
  [PLAN_IC_3]: [
    {
      id: 'sync-ic-3-synced',
      ownerEmployeeId: IC_3,
      relatedType: 'WEEKLY_PLAN',
      relatedId: PLAN_IC_3,
      eventKind: 'IC_PLANNING',
      weekStartDate: WEEK_START,
      status: 'SYNCED',
      graphEventId: 'graph-evt-ic-3',
      retryCount: 0,
      allowedActions: [],
      version: 1,
    },
  ],
  [PLAN_IC_4]: [
    {
      id: 'sync-ic-4-synced',
      ownerEmployeeId: IC_4,
      relatedType: 'WEEKLY_PLAN',
      relatedId: PLAN_IC_4,
      eventKind: 'IC_PLANNING',
      weekStartDate: WEEK_START,
      status: 'SYNCED',
      graphEventId: 'graph-evt-ic-4',
      retryCount: 0,
      allowedActions: [],
      version: 1,
    },
  ],
};

export function syncRecordsForPlan(planId: string): OutlookSyncRecordDto[] {
  return SYNC_BY_PLAN[planId] ?? [];
}

// ── Comments (flat, one-level) ───────────────────────────────────────────────
const COMMENTS_IC_2: CommentDto[] = [
  {
    id: 'comment-1',
    targetType: 'PLAN',
    targetId: PLAN_IC_2,
    authorEmployeeId: MGR_1,
    authorDisplayName: PERSON[MGR_1]!.displayName,
    parentCommentId: null,
    depth: 0,
    body: 'Nice focus this week — keep an eye on the flaky-test blocker.',
    createdAt: '2026-06-02T16:30:00Z',
  },
  {
    id: 'comment-2',
    targetType: 'PLAN',
    targetId: PLAN_IC_2,
    authorEmployeeId: IC_2,
    authorDisplayName: PERSON[IC_2]!.displayName,
    parentCommentId: null,
    depth: 0,
    body: 'Thanks — pairing with platform to land the fix tomorrow.',
    createdAt: '2026-06-02T17:10:00Z',
  },
];

export function commentsForTarget(
  targetType: string,
  targetId: string,
): PageEnvelope<CommentDto> {
  const all =
    targetType === 'PLAN' && targetId === PLAN_IC_2 ? COMMENTS_IC_2 : [];
  return {
    content: all,
    page: { number: 0, size: 25, totalElements: all.length, totalPages: 1 },
    sort: [{ property: 'createdAt', direction: 'ASC' }],
  };
}
