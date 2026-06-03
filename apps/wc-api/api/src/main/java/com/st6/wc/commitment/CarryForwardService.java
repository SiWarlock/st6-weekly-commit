package com.st6.wc.commitment;

import com.st6.wc.audit.AuditService;
import com.st6.wc.auth.DomainAuthorizationService;
import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.commitment.dto.WeeklyCommitmentDto;
import com.st6.wc.commitment.mapper.CommitmentMapper;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.common.OrgTimeConfig;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.ReconciliationOutcome;
import com.st6.wc.enums.WorkType;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.ProjectionService;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.web.IllegalStateTransitionException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carry-forward (task 4.4, E12 — §3 single-outcome / §5 / §6 rule #3 / §8 / §9 / REQ-D-006 /
 * REQ-F-027/028 / REQ-E-005). The ONLY path that sets {@code
 * reconciliation_outcome=CARRIED_FORWARD} (a direct PATCH→CARRIED_FORWARD stays rejected, 4.1).
 * {@link #carryForward} authorizes the <strong>commitment</strong> owner-only ({@link
 * DomainAuthorizationService#authorizeCommitmentMutation} — the chokepoint, first statement),
 * requires the parent plan {@code RECONCILING}, then in ONE {@code @Version}-guarded transaction:
 * is <strong>idempotent per source</strong> (an existing successor keyed on {@code
 * carry_forward_source_commitment_id} is returned untouched — no second shell, source not
 * re-touched); else sets the source outcome to {@code CARRIED_FORWARD} (overwriting any prior
 * completion outcome — the explicit carry action), creates a linked successor in the next Mon–Sun
 * plan (the DRAFT shell created only if absent, reused on the {@code unique(employee_id,
 * week_start_date)} conflict), recomputes the <strong>source</strong> plan's projection (§9
 * lockstep — the carried-IN {@code carry_forward_count} materializes at the next-week lock, not
 * here), and emits an IC audit. No Outlook sync record (§10 has no carry-forward trigger). The
 * successor starts unlinked-DRAFT (REQ-E-005 — the locked prior-week baseline is never touched;
 * only the source's outcome changes).
 */
@Service
public class CarryForwardService {

  private final DomainAuthorizationService authz;
  private final WeeklyPlanRepository plans;
  private final WeeklyCommitmentRepository commitments;
  private final ManagerRelationshipRepository relationships;
  private final ManagerReviewRepository reviews;
  private final ProjectionService projectionService;
  private final AuditService auditService;
  private final CommitmentMapper commitmentMapper;
  private final OrgTimeConfig orgTimeConfig;

  public CarryForwardService(
      DomainAuthorizationService authz,
      WeeklyPlanRepository plans,
      WeeklyCommitmentRepository commitments,
      ManagerRelationshipRepository relationships,
      ManagerReviewRepository reviews,
      ProjectionService projectionService,
      AuditService auditService,
      CommitmentMapper commitmentMapper,
      OrgTimeConfig orgTimeConfig) {
    this.authz = authz;
    this.plans = plans;
    this.commitments = commitments;
    this.relationships = relationships;
    this.reviews = reviews;
    this.projectionService = projectionService;
    this.auditService = auditService;
    this.commitmentMapper = commitmentMapper;
    this.orgTimeConfig = orgTimeConfig;
  }

  @Transactional
  public WeeklyCommitmentDto carryForward(UserPrincipal actor, UUID commitmentId) {
    authz.authorizeCommitmentMutation(actor, commitmentId); // chokepoint: 404/403 (+ audit)
    WeeklyCommitment source =
        commitments
            .findById(commitmentId)
            .orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
    WeeklyPlan sourcePlan =
        plans
            .findById(source.getWeeklyPlanId())
            .orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
    if (sourcePlan.getState() != PlanState.RECONCILING) {
      throw new IllegalStateTransitionException(); // carry-forward requires RECONCILING
    }

    // idempotent per source: an existing successor is returned untouched (no second shell, no
    // source re-touch). A concurrent double-carry is serialized by the source @Version below.
    Optional<WeeklyCommitment> existing =
        commitments.findByCarryForwardSourceCommitmentId(commitmentId);
    if (existing.isPresent()) {
      return commitmentMapper.toDto(existing.get());
    }

    source.setReconciliationOutcome(ReconciliationOutcome.CARRIED_FORWARD); // overwrites any prior
    commitments.save(source); // @Version-guarded: a concurrent double-carry → 409

    LocalDate nextMonday = sourcePlan.getWeekStartDate().plusDays(7); // stored start is a Monday
    WeeklyPlan nextPlan =
        plans
            .findByEmployeeIdAndWeekStartDate(sourcePlan.getEmployeeId(), nextMonday)
            .orElseGet(() -> createNextWeekShell(sourcePlan.getEmployeeId(), nextMonday));

    WeeklyCommitment successor = new WeeklyCommitment();
    successor.setId(UUID.randomUUID());
    successor.setWeeklyPlanId(nextPlan.getId());
    successor.setCommitmentKind(
        CommitmentKind.PLANNED); // the successor is next week's planned work
    successor.setTitle(source.getTitle());
    successor.setDescription(source.getDescription());
    successor.setSupportingOutcomeId(null); // starts unlinked-DRAFT; IC re-links next week (R5)
    successor.setPriority(source.getPriority());
    successor.setWorkType(plannedWorkType(source.getWorkType()));
    successor.setConfidence(source.getConfidence());
    successor.setAlignmentStatus(AlignmentStatus.NEEDS_REVIEW); // fresh draft
    successor.setCarryForwardSourceCommitmentId(source.getId()); // REQ-D-006 self-link
    commitments.save(successor);

    recomputeProjection(
        sourcePlan); // §9 source-plan lockstep (count unchanged — carried-IN reading)
    auditService.record(
        "COMMITMENT_CARRIED_FORWARD",
        "WeeklyCommitment",
        source.getId(),
        actor.employeeId(),
        "Commitment carried forward",
        "{}");
    return commitmentMapper.toDto(successor);
  }

  /**
   * Create the next Mon–Sun DRAFT shell (mirrors {@code PlanShellGenerator}); returns the shell
   * object itself (not {@code save}'s return) so the successor links to its id. The {@code
   * unique(employee_id, week_start_date)} (V1) is the DB backstop against a concurrent create.
   */
  private WeeklyPlan createNextWeekShell(UUID employeeId, LocalDate nextMonday) {
    WeeklyPlan shell = new WeeklyPlan();
    shell.setId(UUID.randomUUID());
    shell.setEmployeeId(employeeId);
    shell.setWeekStartDate(nextMonday);
    shell.setWeekEndDate(orgTimeConfig.weekEndDate(nextMonday));
    shell.setState(PlanState.DRAFT);
    plans.save(shell);
    return shell;
  }

  /**
   * The successor is always a PLANNED commitment, so an UNPLANNED source's {@code work_type} can't
   * be copied verbatim (a PLANNED commitment never carries {@code work_type=UNPLANNED} — the E5
   * invariant); a carried-forward unplanned item becomes planned work next week → default {@code
   * STRATEGIC}. A planned source's work type is copied as-is.
   */
  private static WorkType plannedWorkType(WorkType sourceWorkType) {
    return sourceWorkType == WorkType.UNPLANNED ? WorkType.STRATEGIC : sourceWorkType;
  }

  /**
   * Synchronously recompute the source plan's manager projection (§9 lockstep), reusing the 4.1/4.2
   * pattern: skipped when the IC has no active manager or no review row (nothing to project). The
   * source-plan {@code carry_forward_count} does not change here (the source is not a successor —
   * carried-IN reading); the count materializes when the next-week plan locks.
   */
  private void recomputeProjection(WeeklyPlan plan) {
    UUID managerId =
        relationships
            .findByDirectReportEmployeeIdAndActiveTrue(plan.getEmployeeId())
            .map(ManagerRelationship::getManagerEmployeeId)
            .orElse(null);
    if (managerId == null) {
      return;
    }
    final UUID resolvedManagerId = managerId;
    reviews
        .findByWeeklyPlanId(plan.getId())
        .ifPresent(
            review -> {
              List<WeeklyCommitment> all = commitments.findByWeeklyPlanIdOrderByIdAsc(plan.getId());
              projectionService.recompute(plan, resolvedManagerId, all, review);
            });
  }
}
