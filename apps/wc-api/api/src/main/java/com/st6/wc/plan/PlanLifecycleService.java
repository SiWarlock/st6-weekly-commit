package com.st6.wc.plan;

import com.st6.wc.audit.AuditService;
import com.st6.wc.auth.DomainAuthorizationService;
import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.dto.WeeklyPlanDto;
import com.st6.wc.plan.mapper.PlanMapper;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.ProjectionRefresher;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.ManagerReview;
import com.st6.wc.review.ReviewSlaService;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.sns.SnsLifecyclePublisher;
import com.st6.wc.sync.SyncRecordService;
import com.st6.wc.web.EmptyPlanLockException;
import com.st6.wc.web.IllegalStateTransitionException;
import com.st6.wc.web.UnlinkedPlannedCommitmentException;
import com.st6.wc.web.UnplannedMissingLinkAtCloseException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Owns the {@code DRAFT → LOCKED} transition (task 3.5, §3/§5/§6/§9/§10 — THE safety culmination,
 * rules #1/#2/#4) — the transition lives in the SERVICE, never the controller (forbidden-pattern
 * #4). {@link #lock} authorizes IC-owner-only (the chokepoint), validates the lock precondition by
 * reusing the SAME {@link AllowedActionResolver#canLock} predicate the {@code allowedActions[]}
 * affordance emits (§15 single-source) — diagnosing the specific {@code EMPTY_PLAN_LOCK} / {@code
 * UNLINKED_PLANNED_COMMITMENT} / {@code ILLEGAL_STATE_TRANSITION} code only when it rejects — then
 * in ONE {@code @Version}-guarded transaction: sets {@code LOCKED}+{@code lockedAt}; creates the
 * {@code manager_review} (NOT_REVIEWED + weekday-only SLA, when the IC has a manager);
 * synchronously upserts the manager projections; writes the {@code PLAN_LOCKED} audit + the {@code
 * IC_PLANNING} sync record + the idempotent per-manager/week {@code MANAGER_REVIEW_BLOCK}. The SNS
 * pointer publish runs <strong>after the commit</strong> (a {@code TransactionSynchronization}
 * afterCommit hook) and is strictly non-blocking (rule #4). The post-lock baseline immutability
 * (rule #2) is enforced by the 3.4b gates firing on the now-LOCKED state — this slice just sets it.
 */
@Service
public class PlanLifecycleService {

  private final DomainAuthorizationService authz;
  private final WeeklyPlanRepository plans;
  private final WeeklyCommitmentRepository commitments;
  private final EmployeeRepository employees;
  private final ManagerRelationshipRepository relationships;
  private final AllowedActionResolver allowedActionResolver;
  private final ReviewSlaService reviewSlaService;
  private final ManagerReviewRepository reviews;
  private final ProjectionRefresher projectionRefresher;
  private final SyncRecordService syncRecordService;
  private final SnsLifecyclePublisher snsLifecyclePublisher;
  private final AuditService auditService;
  private final PlanMapper planMapper;
  private final Clock clock;

  public PlanLifecycleService(
      DomainAuthorizationService authz,
      WeeklyPlanRepository plans,
      WeeklyCommitmentRepository commitments,
      EmployeeRepository employees,
      ManagerRelationshipRepository relationships,
      AllowedActionResolver allowedActionResolver,
      ReviewSlaService reviewSlaService,
      ManagerReviewRepository reviews,
      ProjectionRefresher projectionRefresher,
      SyncRecordService syncRecordService,
      SnsLifecyclePublisher snsLifecyclePublisher,
      AuditService auditService,
      PlanMapper planMapper,
      Clock clock) {
    this.authz = authz;
    this.plans = plans;
    this.commitments = commitments;
    this.employees = employees;
    this.relationships = relationships;
    this.allowedActionResolver = allowedActionResolver;
    this.reviewSlaService = reviewSlaService;
    this.reviews = reviews;
    this.projectionRefresher = projectionRefresher;
    this.syncRecordService = syncRecordService;
    this.snsLifecyclePublisher = snsLifecyclePublisher;
    this.auditService = auditService;
    this.planMapper = planMapper;
    this.clock = clock;
  }

  @Transactional
  public WeeklyPlanDto lock(UserPrincipal actor, UUID planId) {
    authz.authorizePlanMutation(actor, planId); // chokepoint: IC-owner-only (404/403 + audit)
    WeeklyPlan plan =
        plans.findById(planId).orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
    List<WeeklyCommitment> planCommitments = commitments.findByWeeklyPlanIdOrderByIdAsc(planId);

    // rule #1 — the lock precondition is the SAME predicate the affordance emits (§15); diagnose
    // the
    // specific code only on rejection (fail-closed final throw if canLock ever grows a new reason).
    if (!allowedActionResolver.canLock(actor.employeeId(), plan, planCommitments)) {
      if (plan.getState() != PlanState.DRAFT) {
        throw new IllegalStateTransitionException();
      }
      List<WeeklyCommitment> planned =
          planCommitments.stream()
              .filter(c -> c.getCommitmentKind() == CommitmentKind.PLANNED)
              .toList();
      if (planned.isEmpty()) {
        throw new EmptyPlanLockException();
      }
      Map<String, String> unlinked = new LinkedHashMap<>();
      planned.stream()
          .filter(c -> c.getSupportingOutcomeId() == null)
          .forEach(c -> unlinked.put(c.getId().toString(), "must link a Supporting Outcome"));
      if (!unlinked.isEmpty()) {
        throw new UnlinkedPlannedCommitmentException(unlinked);
      }
      throw new IllegalStateTransitionException(); // fail-closed: rejected for an unknown reason
    }

    Instant lockedAt = clock.instant();
    plan.setState(PlanState.LOCKED);
    plan.setLockedAt(lockedAt);
    plans.save(plan); // @Version-guarded: a concurrent double-lock → ObjectOptimisticLockingFailure

    String traceId = UUID.randomUUID().toString();
    List<UUID> toPublish = new ArrayList<>();
    // §10 — IC lock. Idempotent: a pre-existing IC_PLANNING record (re-lock / orphan) is a no-op
    // (empty), so the lock can't be rolled back by a uq_sync_owner_related_kind 23505.
    syncRecordService
        .createIcPlanningRecord(plan, traceId)
        .ifPresent(record -> toPublish.add(record.getId()));

    UUID managerId =
        relationships
            .findByDirectReportEmployeeIdAndActiveTrue(actor.employeeId())
            .map(ManagerRelationship::getManagerEmployeeId)
            .orElse(null);
    if (managerId != null) {
      ManagerReview review = new ManagerReview();
      review.setId(UUID.randomUUID());
      review.setWeeklyPlanId(planId);
      review.setManagerEmployeeId(managerId);
      review.setStatus(ReviewStatus.NOT_REVIEWED);
      review.setReviewDueAt(reviewSlaService.reviewDueAt(lockedAt));
      reviews.save(review);

      projectionRefresher.recomputeForPlan(plan); // §9 — review just saved, so the helper finds it

      syncRecordService
          .upsertManagerReviewBlock(managerId, plan.getWeekStartDate(), traceId)
          .ifPresent(block -> toPublish.add(block.getId()));
    }

    auditService.record(
        "PLAN_LOCKED", "WeeklyPlan", planId, actor.employeeId(), "Plan locked", "{}");

    publishAfterCommit(toPublish);

    String displayName =
        employees
            .findById(plan.getEmployeeId())
            .orElseThrow(ResourceNotFoundOrUnauthorizedException::new)
            .getDisplayName();
    return planMapper.toWeeklyPlanDto(plan, displayName, planCommitments, actor.employeeId());
  }

  /**
   * Owns the forward-only {@code LOCKED → RECONCILING} transition (task 4.2, E9, §3/§5/§6/§9/§10) —
   * mirrors {@link #lock} (LESSONS §28). Authorizes IC-owner-only (the chokepoint), guards the
   * source state ({@code LOCKED}-only, else {@code ILLEGAL_STATE_TRANSITION} —
   * illegal/backward/self), then in ONE {@code @Version}-guarded transaction: sets {@code
   * RECONCILING} + {@code reconciliation_started_at}; synchronously refreshes the manager
   * projection's {@code plan_state} (§9, when the IC has a manager + review); writes the {@code
   * RECONCILIATION_STARTED} audit; creates the {@code IC_RECONCILIATION} sync record. The pointer
   * publish runs after commit, strictly non-blocking (rule #4). A concurrent double-start → {@code
   * 409} via {@code @Version}.
   */
  @Transactional
  public WeeklyPlanDto startReconciliation(UserPrincipal actor, UUID planId) {
    authz.authorizePlanMutation(actor, planId); // chokepoint: IC-owner-only (404/403 + audit)
    WeeklyPlan plan =
        plans.findById(planId).orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
    if (plan.getState() != PlanState.LOCKED) {
      throw new IllegalStateTransitionException(); // forward-only: only LOCKED → RECONCILING
    }
    List<WeeklyCommitment> planCommitments = commitments.findByWeeklyPlanIdOrderByIdAsc(planId);

    plan.setState(PlanState.RECONCILING);
    plan.setReconciliationStartedAt(clock.instant());
    plans.save(
        plan); // @Version-guarded: a concurrent double-start → ObjectOptimisticLockingFailure

    String traceId = UUID.randomUUID().toString();
    List<UUID> toPublish = new ArrayList<>();
    // §10 — IC start. Idempotent on the V1 sync grain (re-start / orphan → no-op, no 23505).
    syncRecordService
        .createIcReconciliationRecord(plan, traceId)
        .ifPresent(record -> toPublish.add(record.getId()));

    projectionRefresher.recomputeForPlan(
        plan); // §9 plan_state refresh (no-op if no manager/review)

    auditService.record(
        "RECONCILIATION_STARTED",
        "WeeklyPlan",
        planId,
        actor.employeeId(),
        "Reconciliation started",
        "{}");

    publishAfterCommit(toPublish);

    String displayName =
        employees
            .findById(plan.getEmployeeId())
            .orElseThrow(ResourceNotFoundOrUnauthorizedException::new)
            .getDisplayName();
    return planMapper.toWeeklyPlanDto(plan, displayName, planCommitments, actor.employeeId());
  }

  /**
   * Owns the forward-only {@code RECONCILING → RECONCILED} transition (task 4.5, E10, §3/§5/§9) —
   * the symmetric close to {@link #startReconciliation}. Authorizes IC-owner-only (the chokepoint),
   * guards the source state ({@code RECONCILING}-only, else {@code ILLEGAL_STATE_TRANSITION}),
   * validates the <strong>completeness precondition</strong> (every PLANNED has a {@code
   * reconciliationOutcome} AND every UNPLANNED has both outcome AND a Supporting-Outcome link —
   * else {@code 422 UNPLANNED_MISSING_LINK_AT_CLOSE} with per-commitment {@code fieldErrors}; a
   * {@code CARRIED_FORWARD} is a non-null outcome so it counts, §30), then in ONE {@code @Version}
   * txn: sets {@code RECONCILED} + {@code reconciledAt}; refreshes the manager projection's {@code
   * plan_state} (§9, when the IC has a manager + review); writes the {@code PLAN_RECONCILED} audit.
   * <strong>No sync record</strong> (§10 has no close trigger — unlike start's {@code
   * IC_RECONCILIATION}). Close is non-blocking on manager review (REQ-F-024).
   */
  @Transactional
  public WeeklyPlanDto closeReconciliation(UserPrincipal actor, UUID planId) {
    authz.authorizePlanMutation(actor, planId); // chokepoint: IC-owner-only (404/403 + audit)
    WeeklyPlan plan =
        plans.findById(planId).orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
    if (plan.getState() != PlanState.RECONCILING) {
      throw new IllegalStateTransitionException(); // forward-only: only RECONCILING → RECONCILED
    }
    List<WeeklyCommitment> planCommitments = commitments.findByWeeklyPlanIdOrderByIdAsc(planId);

    Map<String, String> fieldErrors = new LinkedHashMap<>();
    for (WeeklyCommitment c : planCommitments) {
      boolean unplanned = c.getCommitmentKind() == CommitmentKind.UNPLANNED;
      if (c.getReconciliationOutcome() == null) {
        fieldErrors.put(
            "commitments[" + c.getId() + "].reconciliationOutcome",
            unplanned ? "unplanned_missing_outcome" : "planned_missing_outcome");
      }
      if (unplanned && c.getSupportingOutcomeId() == null) {
        fieldErrors.put(
            "commitments[" + c.getId() + "].supportingOutcomeId",
            "unplanned_missing_supporting_outcome");
      }
    }
    if (!fieldErrors.isEmpty()) {
      throw new UnplannedMissingLinkAtCloseException(fieldErrors);
    }

    plan.setState(PlanState.RECONCILED);
    plan.setReconciledAt(clock.instant());
    plans.save(
        plan); // @Version-guarded: a concurrent double-close → ObjectOptimisticLockingFailure

    projectionRefresher.recomputeForPlan(
        plan); // §9 plan_state refresh (no-op if no manager/review)

    auditService.record(
        "PLAN_RECONCILED", "WeeklyPlan", planId, actor.employeeId(), "Reconciliation closed", "{}");
    // No Outlook sync record (§10 has no close trigger — unlike start's IC_RECONCILIATION).

    String displayName =
        employees
            .findById(plan.getEmployeeId())
            .orElseThrow(ResourceNotFoundOrUnauthorizedException::new)
            .getDisplayName();
    return planMapper.toWeeklyPlanDto(plan, displayName, planCommitments, actor.employeeId());
  }

  /**
   * Schedule the pointer publish to run <strong>after</strong> the lock commits (rule #4 — a
   * publish failure can never roll back the committed lock). When no transaction synchronization is
   * active (e.g. a unit test with no ambient transaction), publish immediately.
   */
  private void publishAfterCommit(List<UUID> syncRecordIds) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              syncRecordIds.forEach(snsLifecyclePublisher::publish);
            }
          });
    } else {
      syncRecordIds.forEach(snsLifecyclePublisher::publish);
    }
  }
}
