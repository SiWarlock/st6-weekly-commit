/**
 * Typed API contract — the TypeScript mirror of ARCHITECTURE.md Appendix B
 * (§7: "RTK Query slice types are the API contract"). DTOs cross the boundary,
 * never entities; field names are camelCase and authoritative against Appendix A.
 * A field change here requires the matching Appendix B + §5 edit in the same
 * round (cross-doc invariant). Enum unions mirror Appendix B.1 verbatim.
 */

// ── B.1 enum vocabulary ──────────────────────────────────────────────────────
export type PlanState = 'DRAFT' | 'LOCKED' | 'RECONCILING' | 'RECONCILED';
export type CommitmentKind = 'PLANNED' | 'UNPLANNED';
export type Priority = 'P0' | 'P1' | 'P2';
export type WorkType = 'STRATEGIC' | 'MAINTENANCE' | 'BLOCKER' | 'UNPLANNED';
export type Confidence = 'HIGH' | 'MEDIUM' | 'LOW';
export type AlignmentStatus = 'ALIGNED' | 'NEEDS_REVIEW' | 'MISALIGNED';
export type ReconciliationOutcome =
  | 'COMPLETED'
  | 'PARTIALLY_COMPLETED'
  | 'BLOCKED'
  | 'CANCELED'
  | 'CARRIED_FORWARD';
export type ReviewStatus =
  | 'NOT_REVIEWED'
  | 'REVIEWED_WITH_DISPUTES'
  | 'REVIEWED';
export type AllowedAction =
  | 'LOCK'
  | 'START_RECONCILIATION'
  | 'CLOSE_RECONCILIATION'
  | 'ADD_UNPLANNED'
  | 'CARRY_FORWARD'
  | 'MARK_REVIEWED'
  | 'OPEN_DISPUTE'
  | 'RESPOND_DISPUTE'
  | 'RESOLVE_DISPUTE'
  | 'COMMENT'
  | 'RETRY_SYNC';

// ── B.6 — WeeklyCommitmentDto (nested in B.5) ────────────────────────────────
export interface RcdoBreadcrumbDto {
  rallyCryId: string;
  rallyCryTitle: string;
  definingObjectiveId: string;
  definingObjectiveTitle: string;
  supportingOutcomeId: string;
  supportingOutcomeTitle: string;
}

export interface WeeklyCommitmentDto {
  id: string;
  weeklyPlanId: string;
  commitmentKind: CommitmentKind;
  title: string;
  description?: string;
  supportingOutcomeId?: string;
  supportingOutcomeBreadcrumb?: RcdoBreadcrumbDto;
  priority: Priority;
  workType: WorkType;
  confidence: Confidence;
  alignmentStatus: AlignmentStatus;
  managerAlignmentNote?: string;
  reconciliationOutcome?: ReconciliationOutcome;
  outcomeNote?: string;
  carryForwardSourceCommitmentId?: string;
  hasUnresolvedDispute: boolean;
  allowedActions: AllowedAction[];
  version: number;
}

// ── B.7 — ManagerReviewDto (nested in B.5) ───────────────────────────────────
export interface ManagerReviewDto {
  id: string;
  weeklyPlanId: string;
  managerEmployeeId: string;
  status: ReviewStatus;
  reviewDueAt: string;
  isOverdue: boolean;
  reviewedAt?: string;
  summaryNote?: string;
  unresolvedDisputeCount: number;
  allowedActions: AllowedAction[];
  version: number;
}

// ── B.5 — WeeklyPlanDto (E3/E4/E8/E9/E10/E11 response) ────────────────────────
export interface WeeklyPlanDto {
  id: string;
  employeeId: string;
  employeeDisplayName: string;
  weekStartDate: string;
  weekEndDate: string;
  state: PlanState;
  generatedAt?: string;
  lockedAt?: string;
  reconciliationStartedAt?: string;
  reconciledAt?: string;
  plannedCount: number;
  unplannedCount: number;
  commitments: WeeklyCommitmentDto[];
  managerReview: ManagerReviewDto | null;
  allowedActions: AllowedAction[];
  version: number;
}

// ── Request DTOs (E5/E6/E11) ─────────────────────────────────────────────────
/** E5 `CreateCommitmentRequest` (POST /api/plans/{id}/commitments). */
export interface CreateCommitmentRequest {
  title: string;
  description?: string;
  supportingOutcomeId?: string;
  priority: Priority;
  workType: WorkType;
  confidence: Confidence;
  alignmentStatus?: AlignmentStatus;
}

/**
 * E11 `CreateUnplannedCommitmentRequest` (POST /api/plans/{id}/unplanned-commitments).
 * Same shape minus `workType` — the server forces `commitmentKind=UNPLANNED`,
 * `workType=UNPLANNED`; `supportingOutcomeId` is optional at creation.
 */
export type CreateUnplannedCommitmentRequest = Omit<
  CreateCommitmentRequest,
  'workType'
>;

/**
 * E6 `PatchCommitmentRequest` (PATCH /api/commitments/{id}) — all optional; the
 * server applies field-level authorization + the state gate (post-lock baseline
 * edits → 409 LOCKED_BASELINE_EDIT). No `version` in the body (optimistic-lock is
 * the If-Match header on the lifecycle endpoints E8–E10).
 */
export interface PatchCommitmentRequest {
  title?: string;
  description?: string;
  supportingOutcomeId?: string;
  priority?: Priority;
  workType?: WorkType;
  confidence?: Confidence;
  alignmentStatus?: AlignmentStatus;
  managerAlignmentNote?: string;
  reconciliationOutcome?: ReconciliationOutcome;
  outcomeNote?: string;
}

// ── B.11 — ManagerCommandCenterRowDto (E13, mirrors `manager_plan_summary` §9) ─
/**
 * One direct-report roll-up row for the manager command center. Carries the
 * at-a-glance alignment signal (state + counts) WITHOUT a `reviewId`/
 * `allowedActions[]` — acting on a review (E16) goes through the report's plan
 * (`WeeklyPlanDto.managerReview`, B.7), not the row.
 */
export interface ManagerCommandCenterRowDto {
  managerEmployeeId: string;
  employeeId: string;
  employeeDisplayName: string;
  weeklyPlanId?: string;
  weekStartDate: string;
  planState: PlanState;
  reviewStatus?: ReviewStatus;
  reviewDueAt?: string;
  isReviewOverdue: boolean;
  plannedCount: number;
  unplannedCount: number;
  misalignedCount: number;
  needsReviewCount: number;
  blockedCount: number;
  carryForwardCount: number;
  unresolvedDisputeCount: number;
  updatedAt: string;
}

// ── B.20 — Pageable response envelope (command-center, comments, drill-down) ──
/** Spring Data `Page<T>` serialization (pinned shape, B.20). */
export interface PageEnvelope<T> {
  content: T[];
  page: {
    number: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
  sort: { property: string; direction: 'ASC' | 'DESC' }[];
}

// ── B.7 — MarkReviewedRequest (E16 request) ──────────────────────────────────
/**
 * E16 `POST /api/manager/reviews/{reviewId}/mark-reviewed`. Carries only the
 * optional note — the server DERIVES `REVIEWED` vs `REVIEWED_WITH_DISPUTES` from
 * the plan's unresolved-dispute count; the status is never client-supplied (§3).
 */
export interface MarkReviewedRequest {
  summaryNote?: string;
}
