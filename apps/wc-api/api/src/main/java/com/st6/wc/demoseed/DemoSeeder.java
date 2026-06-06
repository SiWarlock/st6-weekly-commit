package com.st6.wc.demoseed;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.st6.wc.audit.AuditService;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.common.AbstractAuditingEntity;
import com.st6.wc.common.OrgTimeConfig;
import com.st6.wc.dispute.AlignmentDispute;
import com.st6.wc.dispute.repo.AlignmentDisputeRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.DisputeStatus;
import com.st6.wc.enums.EventKind;
import com.st6.wc.enums.FlagType;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.ReconciliationOutcome;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.SyncRelatedType;
import com.st6.wc.enums.SyncStatus;
import com.st6.wc.enums.WorkType;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.ProjectionRefresher;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.ManagerReview;
import com.st6.wc.review.ReviewSlaService;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.sync.OutlookCalendarSyncRecord;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The week-parameterized demo-seed logic (brief 107, Appendix E Part 2, §3/§9/§10/§17) — the
 * runtime/ops sibling of the fixed-week V5/V6 Flyway fixture, parameterized to <em>any</em> Monday
 * week-start. {@link #run(LocalDate)} normalizes its arg to the week's Monday, then: ensures the 7
 * demo personas + 6 relationships exist (idempotent), {@code reset}s the {W, W−7} footprint, seeds
 * the full 7-persona lifecycle matrix for the target week (Grace + Dana also at W−7), recomputes
 * the manager projections from source per plan, and writes one SYSTEM (null-actor) audit row.
 *
 * <p><strong>Construction (rule #4 safety):</strong> the matrix is built by <em>direct row
 * insertion</em> — it never drives {@code PlanLifecycleService.lock} / {@code DisputeService} /
 * etc., because those register {@code afterCommit} SNS publishes that would fire <em>real</em>
 * Outlook calendar syncs for every seeded persona. Projections are the ONLY service reuse: {@link
 * ProjectionRefresher#recomputeForPlan} (read-only over source → writes the read-model, no
 * publish).
 *
 * <p><strong>Temporal correctness (§17 / rule #6):</strong> the two NOT_REVIEWED review due-dates
 * are anchored to the injected {@link Clock}'s {@code now} so the OVERDUE/not split derives at view
 * time regardless of when in the week the seed runs — Marco's due is the previous business day
 * before {@code now} (strictly past → OVERDUE), Priya's is {@link ReviewSlaService}
 * next-business-day after {@code now} (future → not). All other timestamps sit on the week grid.
 * OVERDUE is never stored.
 *
 * <p>Idempotent (reset-then-seed) + portable across weeks. Activation as a one-shot Job is {@link
 * com.st6.wc.job.DemoSeedRunner}'s job ({@code --app.job=seed-demo}).
 */
@Component
public class DemoSeeder implements DemoSeeding {

  static final String AUDIT_ACTION = "DEMO_SEEDED";

  // Fixed, non-PII failure detail for Marco's FAILED sync record (rule #7 — no name/email leak).
  static final String FAILED_FAILURE_CODE = "GRAPH_FORBIDDEN";
  static final String FAILED_SAFE_MESSAGE = "Calendar sync failed; you can retry.";

  private static final String SEED_ACTOR = "system-seed";
  private static final String ORG_TIMEZONE = "America/Chicago";
  private static final LocalTime SLA_TIME = LocalTime.of(17, 0); // 17:00 org-tz, mirrors §17

  // --- personas (V5 literal ids): Dana (manager) + her 6 IC direct reports --------------------
  private static final UUID DANA = UUID.fromString("d0000000-0000-0000-0000-000000000001");
  private static final UUID PRIYA = UUID.fromString("d0000000-0000-0000-0000-000000000002");
  private static final UUID MARCO = UUID.fromString("d0000000-0000-0000-0000-000000000003");
  private static final UUID AISHA = UUID.fromString("d0000000-0000-0000-0000-000000000004");
  private static final UUID TOMAS = UUID.fromString("d0000000-0000-0000-0000-000000000005");
  private static final UUID GRACE = UUID.fromString("d0000000-0000-0000-0000-000000000006");
  private static final UUID SAM = UUID.fromString("d0000000-0000-0000-0000-000000000007");

  private static final List<UUID> PERSONA_IDS =
      List.of(DANA, PRIYA, MARCO, AISHA, TOMAS, GRACE, SAM);

  // --- relationship ids (V5): Dana → each report ---------------------------------------------
  private static final UUID REL_PRIYA = UUID.fromString("e0000000-0000-0000-0000-000000000001");
  private static final UUID REL_MARCO = UUID.fromString("e0000000-0000-0000-0000-000000000002");
  private static final UUID REL_AISHA = UUID.fromString("e0000000-0000-0000-0000-000000000003");
  private static final UUID REL_TOMAS = UUID.fromString("e0000000-0000-0000-0000-000000000004");
  private static final UUID REL_GRACE = UUID.fromString("e0000000-0000-0000-0000-000000000005");
  private static final UUID REL_SAM = UUID.fromString("e0000000-0000-0000-0000-000000000006");

  // --- supporting outcomes (V4 RCDO): SO2 = SO-1.2 is the carry-forward link target -----------
  private static final UUID SO1 = UUID.fromString("c0000000-0000-0000-0000-000000000001");
  private static final UUID SO2 = UUID.fromString("c0000000-0000-0000-0000-000000000002");
  private static final UUID SO3 = UUID.fromString("c0000000-0000-0000-0000-000000000003");
  private static final UUID SO4 = UUID.fromString("c0000000-0000-0000-0000-000000000004");
  private static final UUID SO5 = UUID.fromString("c0000000-0000-0000-0000-000000000005");
  private static final UUID SO6 = UUID.fromString("c0000000-0000-0000-0000-000000000006");
  private static final UUID SO7 = UUID.fromString("c0000000-0000-0000-0000-000000000007");
  private static final UUID SO8 = UUID.fromString("c0000000-0000-0000-0000-000000000008");
  private static final UUID SO9 = UUID.fromString("c0000000-0000-0000-0000-000000000009");

  private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

  private final OrgTimeConfig orgTimeConfig;
  private final Clock clock;
  private final ReviewSlaService reviewSlaService;
  private final ProjectionRefresher projectionRefresher;
  private final AuditService auditService;
  private final EmployeeRepository employees;
  private final ManagerRelationshipRepository relationships;
  private final WeeklyPlanRepository plans;
  private final WeeklyCommitmentRepository commitments;
  private final ManagerReviewRepository reviews;
  private final AlignmentDisputeRepository disputes;
  private final OutlookCalendarSyncRecordRepository syncRecords;

  // Inject the NamedParameterJdbcOperations interface, not the concrete NamedParameterJdbcTemplate
  // —
  // the project convention (AwsSnsLifecycleGateway) that sidesteps the SpotBugs EI_EXPOSE_REP2
  // false-positive on storing an injected concrete; the auto-configured bean injects as the
  // interface.
  private final NamedParameterJdbcOperations jdbc;

  public DemoSeeder(
      OrgTimeConfig orgTimeConfig,
      Clock clock,
      ReviewSlaService reviewSlaService,
      ProjectionRefresher projectionRefresher,
      AuditService auditService,
      EmployeeRepository employees,
      ManagerRelationshipRepository relationships,
      WeeklyPlanRepository plans,
      WeeklyCommitmentRepository commitments,
      ManagerReviewRepository reviews,
      AlignmentDisputeRepository disputes,
      OutlookCalendarSyncRecordRepository syncRecords,
      NamedParameterJdbcOperations jdbc) {
    this.orgTimeConfig = orgTimeConfig;
    this.clock = clock;
    this.reviewSlaService = reviewSlaService;
    this.projectionRefresher = projectionRefresher;
    this.auditService = auditService;
    this.employees = employees;
    this.relationships = relationships;
    this.plans = plans;
    this.commitments = commitments;
    this.reviews = reviews;
    this.disputes = disputes;
    this.syncRecords = syncRecords;
    this.jdbc = jdbc;
  }

  /**
   * Reset-then-seed the demo matrix for the week containing {@code week}. The arg is normalized to
   * its Monday, so any day in the target week resolves to the same {@code week_start_date}.
   */
  @Override
  @Transactional
  public int run(LocalDate week) {
    LocalDate weekStart = orgTimeConfig.weekStartDate(week);
    ensurePersonas();
    reset(weekStart);
    List<WeeklyPlan> seeded = seedMatrix(weekStart);
    seeded.forEach(projectionRefresher::recomputeForPlan); // §28 skips unmanaged/unreviewed plans
    auditRun(weekStart, seeded.size());
    log.info("Demo-seed complete for week {} ({} plans seeded).", weekStart, seeded.size());
    return seeded.size();
  }

  // ===== persona-ensure (idempotent — identity is never week-scoped, so the reset never wipes it)
  // =

  private void ensurePersonas() {
    ensureEmployee(
        DANA,
        "st6|dana-okafor",
        "dana.okafor@dreddy817.onmicrosoft.com",
        "Dana Okafor",
        RoleType.MANAGER);
    ensureEmployee(
        PRIYA,
        "st6|priya-raman",
        "priya.raman@dreddy817.onmicrosoft.com",
        "Priya Raman",
        RoleType.IC);
    ensureEmployee(
        MARCO,
        "st6|marco-bellini",
        "marco.bellini@dreddy817.onmicrosoft.com",
        "Marco Bellini",
        RoleType.IC);
    ensureEmployee(
        AISHA, "st6|aisha-khan", "aisha.khan@dreddy817.onmicrosoft.com", "Aisha Khan", RoleType.IC);
    ensureEmployee(
        TOMAS,
        "st6|tomas-novak",
        "tomas.novak@dreddy817.onmicrosoft.com",
        "Tomas Novak",
        RoleType.IC);
    ensureEmployee(
        GRACE, "st6|grace-liu", "grace.liu@dreddy817.onmicrosoft.com", "Grace Liu", RoleType.IC);
    ensureEmployee(
        SAM, "st6|sam-carter", "sam.carter@dreddy817.onmicrosoft.com", "Sam Carter", RoleType.IC);

    ensureRelationship(REL_PRIYA, DANA, PRIYA);
    ensureRelationship(REL_MARCO, DANA, MARCO);
    ensureRelationship(REL_AISHA, DANA, AISHA);
    ensureRelationship(REL_TOMAS, DANA, TOMAS);
    ensureRelationship(REL_GRACE, DANA, GRACE);
    ensureRelationship(REL_SAM, DANA, SAM);
  }

  private void ensureEmployee(UUID id, String subject, String email, String name, RoleType role) {
    if (employees.existsById(id)) {
      return;
    }
    Employee e = new Employee();
    e.setId(id);
    e.setExternalSubject(subject);
    e.setEmail(email);
    e.setDisplayName(name);
    e.setRole(role);
    e.setActive(true);
    e.setTimezone(ORG_TIMEZONE);
    stamp(e);
    employees.save(e);
  }

  private void ensureRelationship(UUID id, UUID managerId, UUID reportId) {
    if (relationships.existsById(id)) {
      return;
    }
    ManagerRelationship r = new ManagerRelationship();
    r.setId(id);
    r.setManagerEmployeeId(managerId);
    r.setDirectReportEmployeeId(reportId);
    r.setActive(true);
    stamp(r);
    relationships.save(r);
  }

  // ===== the 7-persona matrix (a parameterized replica of the V6 fixture) =====================

  private List<WeeklyPlan> seedMatrix(LocalDate weekStart) {
    LocalDate priorWeek = weekStart.minusWeeks(1);
    Instant now = clock.instant();

    Instant generatedAt = gridInstant(weekStart, 8);
    Instant lockedAt = gridInstant(weekStart, 9);
    Instant recStartedAt = gridInstant(weekStart.plusDays(1), 13);
    Instant reviewedAt = gridInstant(weekStart.plusDays(1), 20);
    Instant resolvedAt = gridInstant(weekStart.plusDays(1), 21);
    Instant priorGenerated = gridInstant(priorWeek, 8);
    Instant priorLocked = gridInstant(priorWeek, 9);
    Instant priorRecStarted = gridInstant(priorWeek.plusDays(4), 14);
    Instant priorReconciled = gridInstant(priorWeek.plusDays(6), 18);

    Instant priyaDue = reviewSlaService.reviewDueAt(now); // next business day after now → future
    Instant marcoDue = previousBusinessDayDueBefore(now); // prev business day before now → past
    Instant reviewedDue =
        reviewSlaService.reviewDueAt(lockedAt); // week-grid (REVIEWED ⇒ irrelevant)

    List<WeeklyPlan> seeded = new ArrayList<>();

    // R1 Priya — LOCKED · 3 linked planned · NOT_REVIEWED not-overdue · SYNCED
    WeeklyPlan priya =
        insertPlan(PRIYA, weekStart, PlanState.LOCKED, generatedAt, lockedAt, null, null);
    seeded.add(priya);
    insertCommitment(
        priya,
        CommitmentKind.PLANNED,
        "Ship the activation funnel A/B test",
        SO1,
        Priority.P1,
        WorkType.STRATEGIC,
        Confidence.HIGH,
        AlignmentStatus.ALIGNED,
        null,
        null,
        null);
    insertCommitment(
        priya,
        CommitmentKind.PLANNED,
        "Cut onboarding drop-off on step 3",
        SO6,
        Priority.P2,
        WorkType.MAINTENANCE,
        Confidence.MEDIUM,
        AlignmentStatus.ALIGNED,
        null,
        null,
        null);
    insertCommitment(
        priya,
        CommitmentKind.PLANNED,
        "Instrument retention cohort dashboard",
        SO8,
        Priority.P1,
        WorkType.STRATEGIC,
        Confidence.HIGH,
        AlignmentStatus.ALIGNED,
        null,
        null,
        null);
    insertReview(priya, DANA, ReviewStatus.NOT_REVIEWED, priyaDue, null);
    insertSync(
        PRIYA,
        SyncRelatedType.WEEKLY_PLAN,
        priya.getId(),
        EventKind.IC_PLANNING,
        SyncStatus.SYNCED,
        "demo-graph-event-priya-planning",
        null,
        null,
        0,
        weekStart);

    // R2 Marco — LOCKED · 2 linked planned · NOT_REVIEWED OVERDUE · FAILED
    WeeklyPlan marco =
        insertPlan(MARCO, weekStart, PlanState.LOCKED, generatedAt, lockedAt, null, null);
    seeded.add(marco);
    insertCommitment(
        marco,
        CommitmentKind.PLANNED,
        "Harden the import pipeline retries",
        SO3,
        Priority.P1,
        WorkType.STRATEGIC,
        Confidence.HIGH,
        AlignmentStatus.ALIGNED,
        null,
        null,
        null);
    insertCommitment(
        marco,
        CommitmentKind.PLANNED,
        "Triage the support backlog SLA",
        SO4,
        Priority.P2,
        WorkType.MAINTENANCE,
        Confidence.MEDIUM,
        AlignmentStatus.ALIGNED,
        null,
        null,
        null);
    insertReview(marco, DANA, ReviewStatus.NOT_REVIEWED, marcoDue, null);
    insertSync(
        MARCO,
        SyncRelatedType.WEEKLY_PLAN,
        marco.getId(),
        EventKind.IC_PLANNING,
        SyncStatus.FAILED,
        null,
        FAILED_FAILURE_CODE,
        FAILED_SAFE_MESSAGE,
        1,
        weekStart);

    // R3 Aisha — LOCKED · 2 planned (one NEEDS_REVIEW w/ OPEN MISALIGNED dispute) ·
    // REVIEWED_WITH_DISPUTES
    WeeklyPlan aisha =
        insertPlan(AISHA, weekStart, PlanState.LOCKED, generatedAt, lockedAt, null, null);
    seeded.add(aisha);
    insertCommitment(
        aisha,
        CommitmentKind.PLANNED,
        "Migrate auth to the new identity provider",
        SO1,
        Priority.P1,
        WorkType.STRATEGIC,
        Confidence.HIGH,
        AlignmentStatus.ALIGNED,
        null,
        null,
        null);
    WeeklyCommitment aishaDisputed =
        insertCommitment(
            aisha,
            CommitmentKind.PLANNED,
            "Rework the billing reconciliation report",
            SO9,
            Priority.P2,
            WorkType.STRATEGIC,
            Confidence.MEDIUM,
            AlignmentStatus.NEEDS_REVIEW,
            null,
            null,
            null);
    insertReview(aisha, DANA, ReviewStatus.REVIEWED_WITH_DISPUTES, reviewedDue, reviewedAt);
    insertDispute(
        aishaDisputed.getId(),
        DANA,
        DisputeStatus.OPEN,
        FlagType.MISALIGNED,
        "This looks misaligned with the billing outcome — can you re-link?",
        null,
        null);

    // R4 Tomas — LOCKED · 2 planned (one had a RESOLVED dispute) · REVIEWED
    WeeklyPlan tomas =
        insertPlan(TOMAS, weekStart, PlanState.LOCKED, generatedAt, lockedAt, null, null);
    seeded.add(tomas);
    insertCommitment(
        tomas,
        CommitmentKind.PLANNED,
        "Lift API availability to four nines",
        SO6,
        Priority.P1,
        WorkType.STRATEGIC,
        Confidence.HIGH,
        AlignmentStatus.ALIGNED,
        null,
        null,
        null);
    WeeklyCommitment tomasResolved =
        insertCommitment(
            tomas,
            CommitmentKind.PLANNED,
            "Close the SEV-2 security findings",
            SO7,
            Priority.P2,
            WorkType.MAINTENANCE,
            Confidence.MEDIUM,
            AlignmentStatus.ALIGNED,
            null,
            null,
            null);
    insertReview(tomas, DANA, ReviewStatus.REVIEWED, reviewedDue, reviewedAt);
    insertResolvedDispute(
        tomasResolved.getId(),
        DANA,
        FlagType.NEEDS_REVISION,
        "Please tighten the scope to the SEV-2 set.",
        "Scoped down and re-linked — thanks.",
        resolvedAt);

    // R5 Grace prior (W−7) — RECONCILED · the carry-forward SOURCE (no review on the prior plan)
    WeeklyPlan gracePrior =
        insertPlan(
            GRACE,
            priorWeek,
            PlanState.RECONCILED,
            priorGenerated,
            priorLocked,
            priorRecStarted,
            priorReconciled);
    seeded.add(gracePrior);
    WeeklyCommitment graceSrc =
        insertCommitment(
            gracePrior,
            CommitmentKind.PLANNED,
            "Draft the activation-onboarding runbook",
            SO2,
            Priority.P1,
            WorkType.STRATEGIC,
            Confidence.HIGH,
            AlignmentStatus.ALIGNED,
            ReconciliationOutcome.CARRIED_FORWARD,
            "Not finished; carried into the next week.",
            null);
    insertCommitment(
        gracePrior,
        CommitmentKind.PLANNED,
        "Publish the onboarding metrics baseline",
        SO1,
        Priority.P2,
        WorkType.MAINTENANCE,
        Confidence.MEDIUM,
        AlignmentStatus.ALIGNED,
        ReconciliationOutcome.COMPLETED,
        "Baseline shipped.",
        null);

    // R5 Grace current (W) — RECONCILING · carry-forward TARGET + COMPLETED + BLOCKED + UNPLANNED ·
    // REVIEWED
    WeeklyPlan grace =
        insertPlan(
            GRACE, weekStart, PlanState.RECONCILING, generatedAt, lockedAt, recStartedAt, null);
    seeded.add(grace);
    insertCommitment(
        grace,
        CommitmentKind.PLANNED,
        "Finish the activation-onboarding runbook",
        SO2,
        Priority.P1,
        WorkType.STRATEGIC,
        Confidence.HIGH,
        AlignmentStatus.ALIGNED,
        null,
        null,
        graceSrc.getId());
    insertCommitment(
        grace,
        CommitmentKind.PLANNED,
        "Land the support first-response SLA",
        SO4,
        Priority.P2,
        WorkType.MAINTENANCE,
        Confidence.MEDIUM,
        AlignmentStatus.ALIGNED,
        ReconciliationOutcome.COMPLETED,
        "Hit under 2 hours.",
        null);
    insertCommitment(
        grace,
        CommitmentKind.PLANNED,
        "Stabilize the release train",
        SO7,
        Priority.P1,
        WorkType.STRATEGIC,
        Confidence.HIGH,
        AlignmentStatus.ALIGNED,
        ReconciliationOutcome.BLOCKED,
        "Blocked on an upstream infra dependency.",
        null);
    insertCommitment(
        grace,
        CommitmentKind.UNPLANNED,
        "Hotfix the time-to-first-plan regression",
        SO5,
        Priority.P2,
        WorkType.UNPLANNED,
        Confidence.MEDIUM,
        AlignmentStatus.ALIGNED,
        ReconciliationOutcome.PARTIALLY_COMPLETED,
        "Mitigated; full fix pending.",
        null);
    insertReview(grace, DANA, ReviewStatus.REVIEWED, reviewedDue, reviewedAt);

    // R6 Sam — DRAFT · one deliberately-unlinked + one linked planned · no review (the can't-lock
    // fixture)
    WeeklyPlan sam = insertPlan(SAM, weekStart, PlanState.DRAFT, generatedAt, null, null, null);
    seeded.add(sam);
    insertCommitment(
        sam,
        CommitmentKind.PLANNED,
        "Explore a weekly-digest email",
        null,
        Priority.P1,
        WorkType.STRATEGIC,
        Confidence.HIGH,
        AlignmentStatus.NEEDS_REVIEW,
        null,
        null,
        null);
    insertCommitment(
        sam,
        CommitmentKind.PLANNED,
        "Draft the Q3 reliability roadmap",
        SO1,
        Priority.P2,
        WorkType.MAINTENANCE,
        Confidence.MEDIUM,
        AlignmentStatus.ALIGNED,
        null,
        null,
        null);

    // Dana — her own IC plan RECONCILED at W−7 (unmanaged → no review) + a MANAGER_REVIEW_BLOCK
    // sync at W
    WeeklyPlan dana =
        insertPlan(
            DANA,
            priorWeek,
            PlanState.RECONCILED,
            priorGenerated,
            priorLocked,
            priorRecStarted,
            priorReconciled);
    seeded.add(dana);
    insertCommitment(
        dana,
        CommitmentKind.PLANNED,
        "Run the leadership weekly-commit rollout",
        SO1,
        Priority.P1,
        WorkType.STRATEGIC,
        Confidence.HIGH,
        AlignmentStatus.ALIGNED,
        ReconciliationOutcome.COMPLETED,
        "Rolled out to all squads.",
        null);
    insertCommitment(
        dana,
        CommitmentKind.PLANNED,
        "Close the quarterly alignment review",
        SO6,
        Priority.P2,
        WorkType.MAINTENANCE,
        Confidence.MEDIUM,
        AlignmentStatus.ALIGNED,
        ReconciliationOutcome.COMPLETED,
        "All reports reconciled.",
        null);
    insertSync(
        DANA,
        SyncRelatedType.MANAGER_REVIEW_WEEK,
        DANA,
        EventKind.MANAGER_REVIEW_BLOCK,
        SyncStatus.SYNCED,
        "demo-graph-event-dana-review-block",
        null,
        null,
        0,
        weekStart);

    return seeded;
  }

  // ===== insert helpers (direct row insertion — never the lifecycle services) ==================

  private WeeklyPlan insertPlan(
      UUID owner,
      LocalDate week,
      PlanState state,
      Instant generatedAt,
      Instant lockedAt,
      Instant reconciliationStartedAt,
      Instant reconciledAt) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(owner);
    p.setWeekStartDate(week);
    p.setWeekEndDate(week.plusDays(6));
    p.setState(state);
    p.setGeneratedAt(generatedAt);
    p.setLockedAt(lockedAt);
    p.setReconciliationStartedAt(reconciliationStartedAt);
    p.setReconciledAt(reconciledAt);
    stamp(p);
    return plans.save(p);
  }

  // A faithful fixture row needs every commitment column set — the long parameter list is data.
  private WeeklyCommitment insertCommitment(
      WeeklyPlan plan,
      CommitmentKind kind,
      String title,
      UUID supportingOutcomeId,
      Priority priority,
      WorkType workType,
      Confidence confidence,
      AlignmentStatus alignmentStatus,
      ReconciliationOutcome reconciliationOutcome,
      String outcomeNote,
      UUID carryForwardSourceCommitmentId) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(plan.getId());
    c.setCommitmentKind(kind);
    c.setTitle(title);
    c.setSupportingOutcomeId(supportingOutcomeId);
    c.setPriority(priority);
    c.setWorkType(workType);
    c.setConfidence(confidence);
    c.setAlignmentStatus(alignmentStatus);
    c.setReconciliationOutcome(reconciliationOutcome);
    c.setOutcomeNote(outcomeNote);
    c.setCarryForwardSourceCommitmentId(carryForwardSourceCommitmentId);
    stamp(c);
    return commitments.save(c);
  }

  private void insertReview(
      WeeklyPlan plan, UUID managerId, ReviewStatus status, Instant dueAt, Instant reviewedAt) {
    ManagerReview r = new ManagerReview();
    r.setId(UUID.randomUUID());
    r.setWeeklyPlanId(plan.getId());
    r.setManagerEmployeeId(managerId);
    r.setStatus(status);
    r.setReviewDueAt(dueAt);
    r.setReviewedAt(reviewedAt);
    stamp(r);
    reviews.save(r);
  }

  private void insertDispute(
      UUID commitmentId,
      UUID managerId,
      DisputeStatus status,
      FlagType flagType,
      String managerNote,
      String icResponse,
      Instant resolvedAt) {
    AlignmentDispute d = new AlignmentDispute();
    d.setId(UUID.randomUUID());
    d.setCommitmentId(commitmentId);
    d.setManagerEmployeeId(managerId);
    d.setStatus(status);
    d.setFlagType(flagType);
    d.setManagerNote(managerNote);
    d.setIcResponse(icResponse);
    d.setResolvedAt(resolvedAt);
    stamp(d);
    disputes.save(d);
  }

  private void insertResolvedDispute(
      UUID commitmentId,
      UUID managerId,
      FlagType flagType,
      String managerNote,
      String icResponse,
      Instant resolvedAt) {
    insertDispute(
        commitmentId,
        managerId,
        DisputeStatus.RESOLVED,
        flagType,
        managerNote,
        icResponse,
        resolvedAt);
  }

  private void insertSync(
      UUID owner,
      SyncRelatedType relatedType,
      UUID relatedId,
      EventKind eventKind,
      SyncStatus status,
      String graphEventId,
      String failureCode,
      String safeMessage,
      int retryCount,
      LocalDate weekStart) {
    OutlookCalendarSyncRecord s = new OutlookCalendarSyncRecord();
    s.setId(UUID.randomUUID());
    s.setOwnerEmployeeId(owner);
    s.setRelatedType(relatedType);
    s.setRelatedId(relatedId);
    s.setEventKind(eventKind);
    s.setStatus(status);
    s.setGraphEventId(graphEventId);
    s.setFailureCode(failureCode);
    s.setSafeMessage(safeMessage);
    s.setRetryCount(retryCount);
    s.setWeekStartDate(weekStart);
    stamp(s);
    syncRecords.save(s);
  }

  // ===== the FK-safe reset (107a) =============================================================

  /**
   * FK-safe delete of the personas' derived rows across the footprint {@code {weekStart,
   * weekStart−7}}: disputes → reviews → projections → sync → commitments → plans. Scoped by persona
   * id + the two weeks; identity + reference data are never in scope. Package-private so the reset
   * can be proven in isolation now that {@link #run} also re-seeds.
   */
  void reset(LocalDate weekStart) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("personas", PERSONA_IDS)
            .addValue("weeks", List.of(weekStart, weekStart.minusWeeks(1)));

    jdbc.update(
        "delete from alignment_dispute d using weekly_commitment c, weekly_plan p"
            + " where d.commitment_id = c.id and c.weekly_plan_id = p.id"
            + " and p.employee_id in (:personas) and p.week_start_date in (:weeks)",
        params);
    jdbc.update(
        "delete from manager_review mr using weekly_plan p"
            + " where mr.weekly_plan_id = p.id"
            + " and p.employee_id in (:personas) and p.week_start_date in (:weeks)",
        params);
    jdbc.update(
        "delete from manager_heatmap_cell"
            + " where employee_id in (:personas) and week_start_date in (:weeks)",
        params);
    jdbc.update(
        "delete from manager_plan_summary"
            + " where employee_id in (:personas) and week_start_date in (:weeks)",
        params);
    jdbc.update(
        "delete from outlook_calendar_sync_record"
            + " where owner_employee_id in (:personas) and week_start_date in (:weeks)",
        params);
    jdbc.update(
        "delete from weekly_commitment c using weekly_plan p"
            + " where c.weekly_plan_id = p.id"
            + " and p.employee_id in (:personas) and p.week_start_date in (:weeks)",
        params);
    jdbc.update(
        "delete from weekly_plan"
            + " where employee_id in (:personas) and week_start_date in (:weeks)",
        params);
  }

  // ===== temporal + audit + stamp helpers =====================================================

  /** {@code hourCt:00} org-tz on {@code date}, as an Instant (week-grid timestamps). */
  private Instant gridInstant(LocalDate date, int hourCt) {
    return ZonedDateTime.of(date, LocalTime.of(hourCt, 0), orgTimeConfig.zoneId()).toInstant();
  }

  /**
   * 17:00 org-tz on the business day strictly before {@code now} — the inverse of {@link
   * ReviewSlaService#reviewDueAt}. Always in the past (an earlier calendar day at 17:00), so the
   * overdue persona derives OVERDUE at any view time within the target week (§17 / rule #6).
   */
  private Instant previousBusinessDayDueBefore(Instant now) {
    LocalDate d = LocalDate.ofInstant(now, orgTimeConfig.zoneId()).minusDays(1);
    while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
      d = d.minusDays(1);
    }
    return ZonedDateTime.of(d, SLA_TIME, orgTimeConfig.zoneId()).toInstant();
  }

  /** One SYSTEM (null-actor) run audit; safe count-only metadata, no PII (rule #7). */
  private void auditRun(LocalDate weekStart, int plansSeeded) {
    String safeMetadata =
        JsonNodeFactory.instance
            .objectNode()
            .put("week_start", weekStart.toString())
            .put("plans_seeded", plansSeeded)
            .toString();
    auditService.record(
        AUDIT_ACTION,
        "DemoSeed",
        null, // run-level — no single entity id
        null, // SYSTEM actor (§6): null actor_employee_id
        "Seeded demo matrix for week " + weekStart,
        safeMetadata);
  }

  /** Stamp the provenance audit quartet on a seeded row (SEED_ACTOR @ now). */
  private void stamp(AbstractAuditingEntity e) {
    Instant now = clock.instant();
    e.setCreatedBy(SEED_ACTOR);
    e.setCreatedAt(now);
    e.setUpdatedBy(SEED_ACTOR);
    e.setUpdatedAt(now);
  }
}
