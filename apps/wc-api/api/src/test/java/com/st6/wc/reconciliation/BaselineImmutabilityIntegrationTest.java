package com.st6.wc.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.st6.wc.audit.repo.AuditEventRepository;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.ReconciliationOutcome;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.WorkType;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.repo.ManagerHeatmapCellRepository;
import com.st6.wc.projection.repo.ManagerPlanSummaryRepository;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.support.AbstractAppBootTest;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * REQ-E-005 acceptance proof (task 4.6 — the Phase-4 closer): drives a FULL reconciliation pass
 * (lock E8 → start E9 → add-unplanned E11 → record-outcomes E6 → link-unplanned-SO E6 →
 * carry-forward E12 → close E10) through the REAL demo-mode 2.6 HTTP chain against real PG16 + the
 * V4 RCDO seed ({@link AbstractAppBootTest}), and asserts the locked PLANNED baseline is
 * byte-identical end-to-end.
 *
 * <p>The locked-baseline-immutability <em>behavior</em> already exists (3.4b/3.5 gates confine
 * writes; 4.4/4.5 confine reconciliation writes) — this is the deterministic <em>proof</em> that no
 * unplanned-add, outcome-record, carry-forward, or close rewrites any captured baseline field
 * (REQ-E-005), that carry-forward writes ONLY the source's {@code reconciliation_outcome} +
 * successor self-link with the successor in a distinct next-week plan (REQ-D-006, the R5 two-week
 * shape), and that the chain reaches {@code RECONCILED}. A RED here is a real REQ-E-005 violation.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class BaselineImmutabilityIntegrationTest extends AbstractAppBootTest {

  private static final String HEADER = "X-Demo-Employee-Id";
  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1); // a Monday
  private static final LocalDate NEXT_WEEK = LocalDate.of(2026, 6, 8);
  private static final UUID SEED_SO_1_1 = UUID.fromString("c0000000-0000-0000-0000-000000000001");

  @Autowired private MockMvc mvc;
  @Autowired private EmployeeRepository employees;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private WeeklyCommitmentRepository commitments;
  @Autowired private ManagerRelationshipRepository relationships;
  @Autowired private ManagerReviewRepository reviews;
  @Autowired private ManagerPlanSummaryRepository summaries;
  @Autowired private ManagerHeatmapCellRepository heatmapCells;
  @Autowired private OutlookCalendarSyncRecordRepository syncRecords;
  @Autowired private AuditEventRepository auditEvents;

  @AfterEach
  void cleanup() {
    auditEvents.deleteAll();
    summaries.deleteAll();
    heatmapCells.deleteAll();
    syncRecords.deleteAll();
    reviews.deleteAll();
    commitments.deleteAll();
    relationships.deleteAll();
    plans.deleteAll();
    employees.deleteAll();
  }

  // ===== the immutable planned-baseline fieldset (Appendix A / §3 / REQ-E-005) =====
  private record Baseline(
      String title,
      String description,
      UUID supportingOutcomeId,
      Priority priority,
      WorkType workType,
      Confidence confidence,
      CommitmentKind commitmentKind) {
    static Baseline of(WeeklyCommitment c) {
      return new Baseline(
          c.getTitle(),
          c.getDescription(),
          c.getSupportingOutcomeId(),
          c.getPriority(),
          c.getWorkType(),
          c.getConfidence(),
          c.getCommitmentKind());
    }
  }

  // --- seeding helpers (mirror the slice endpoint tests' direct-repo seed → drive real endpoints)

  private Employee saveEmployee(String email, RoleType role) {
    Employee e = new Employee();
    e.setId(UUID.randomUUID());
    e.setEmail(email);
    e.setDisplayName("Name " + email);
    e.setRole(role);
    e.setActive(true);
    return employees.saveAndFlush(e);
  }

  private WeeklyPlan saveDraftPlan(UUID ownerId) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(ownerId);
    p.setWeekStartDate(WEEK);
    p.setWeekEndDate(WEEK.plusDays(6));
    p.setState(PlanState.DRAFT);
    return plans.saveAndFlush(p);
  }

  /** A linked PLANNED commitment with caller-chosen distinct baseline values. */
  private WeeklyCommitment savePlanned(
      UUID planId,
      String title,
      String description,
      Priority priority,
      WorkType workType,
      Confidence confidence) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(planId);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle(title);
    c.setDescription(description);
    c.setSupportingOutcomeId(SEED_SO_1_1);
    c.setPriority(priority);
    c.setWorkType(workType);
    c.setConfidence(confidence);
    c.setAlignmentStatus(AlignmentStatus.ALIGNED);
    return commitments.saveAndFlush(c);
  }

  private void saveActiveRelationship(UUID managerId, UUID reportId) {
    ManagerRelationship r = new ManagerRelationship();
    r.setId(UUID.randomUUID());
    r.setManagerEmployeeId(managerId);
    r.setDirectReportEmployeeId(reportId);
    r.setActive(true);
    relationships.saveAndFlush(r);
  }

  // --- thin endpoint drivers (the real production HTTP path)

  private void lock(UUID planId, UUID ic) throws Exception {
    mvc.perform(post("/api/plans/" + planId + "/lock").header(HEADER, ic.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("LOCKED"));
  }

  private void startReconciliation(UUID planId, UUID ic) throws Exception {
    mvc.perform(
            post("/api/plans/" + planId + "/start-reconciliation").header(HEADER, ic.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("RECONCILING"));
  }

  private void addUnplanned(UUID planId, UUID ic, String title) throws Exception {
    mvc.perform(
            post("/api/plans/" + planId + "/unplanned-commitments")
                .header(HEADER, ic.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"%s\",\"priority\":\"P1\",\"confidence\":\"MEDIUM\"}"
                        .formatted(title)))
        .andExpect(status().isCreated());
  }

  private void recordOutcome(UUID commitmentId, UUID ic, ReconciliationOutcome outcome)
      throws Exception {
    mvc.perform(
            patch("/api/commitments/" + commitmentId)
                .header(HEADER, ic.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reconciliationOutcome\":\"%s\"}".formatted(outcome.name())))
        .andExpect(status().isOk());
  }

  private void linkSupportingOutcome(UUID commitmentId, UUID ic, UUID soId) throws Exception {
    mvc.perform(
            patch("/api/commitments/" + commitmentId)
                .header(HEADER, ic.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supportingOutcomeId\":\"%s\"}".formatted(soId)))
        .andExpect(status().isOk());
  }

  private void carryForward(UUID commitmentId, UUID ic) throws Exception {
    mvc.perform(
            post("/api/commitments/" + commitmentId + "/carry-forward")
                .header(HEADER, ic.toString()))
        .andExpect(status().isCreated());
  }

  private void close(UUID planId, UUID ic) throws Exception {
    mvc.perform(
            post("/api/plans/" + planId + "/close-reconciliation").header(HEADER, ic.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("RECONCILED"));
  }

  private UUID unplannedIdIn(UUID planId) {
    return commitments.findByWeeklyPlanIdOrderByIdAsc(planId).stream()
        .filter(c -> c.getCommitmentKind() == CommitmentKind.UNPLANNED)
        .map(WeeklyCommitment::getId)
        .findFirst()
        .orElseThrow();
  }

  // === #1 REQ-E-005: a full reconciliation pass leaves every PLANNED baseline field byte-identical
  @Test
  void fullReconciliationPass_leavesPlannedBaselineByteIdentical() throws Exception {
    Employee ic = saveEmployee("grace@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = saveDraftPlan(ic.getId());
    WeeklyCommitment p1 =
        savePlanned(
            plan.getId(),
            "Draft the activation runbook",
            "the runbook detail",
            Priority.P1,
            WorkType.STRATEGIC,
            Confidence.MEDIUM);
    WeeklyCommitment p2 =
        savePlanned(
            plan.getId(),
            "Migrate the billing ledger",
            "ledger migration detail",
            Priority.P2,
            WorkType.MAINTENANCE,
            Confidence.HIGH);

    lock(plan.getId(), ic.getId());

    // capture each PLANNED commitment's baseline immediately post-lock
    Map<UUID, Baseline> postLock =
        Map.of(
            p1.getId(), Baseline.of(commitments.findById(p1.getId()).orElseThrow()),
            p2.getId(), Baseline.of(commitments.findById(p2.getId()).orElseThrow()));

    // drive the full reconciliation pass
    startReconciliation(plan.getId(), ic.getId());
    addUnplanned(plan.getId(), ic.getId(), "Hotfix the incident");
    UUID unplanned = unplannedIdIn(plan.getId());
    recordOutcome(p1.getId(), ic.getId(), ReconciliationOutcome.COMPLETED);
    recordOutcome(unplanned, ic.getId(), ReconciliationOutcome.BLOCKED);
    linkSupportingOutcome(unplanned, ic.getId(), SEED_SO_1_1);
    carryForward(p2.getId(), ic.getId()); // sets p2.reconciliationOutcome=CARRIED_FORWARD
    close(plan.getId(), ic.getId());

    // REQ-E-005: every PLANNED baseline field byte-identical to the post-lock snapshot
    assertThat(Baseline.of(commitments.findById(p1.getId()).orElseThrow()))
        .isEqualTo(postLock.get(p1.getId()));
    assertThat(Baseline.of(commitments.findById(p2.getId()).orElseThrow()))
        .isEqualTo(postLock.get(p2.getId()));
    // sanity: the reconciliation DID write the non-baseline outcome fields (the pass really ran)
    assertThat(commitments.findById(p1.getId()).orElseThrow().getReconciliationOutcome())
        .isEqualTo(ReconciliationOutcome.COMPLETED);
    assertThat(commitments.findById(p2.getId()).orElseThrow().getReconciliationOutcome())
        .isEqualTo(ReconciliationOutcome.CARRIED_FORWARD);
    // sanity (defense-in-depth): the unplanned commitment reached the close-eligible state
    // (outcome + SO-link both landed via E6 — else close's completeness gate would have 422'd)
    WeeklyCommitment unplannedAfter = commitments.findById(unplanned).orElseThrow();
    assertThat(unplannedAfter.getReconciliationOutcome()).isEqualTo(ReconciliationOutcome.BLOCKED);
    assertThat(unplannedAfter.getSupportingOutcomeId()).isEqualTo(SEED_SO_1_1);
  }

  // === #2 REQ-D-006: carry-forward writes ONLY the source outcome + a successor in the next week
  @Test
  void carryForward_writesOnlySourceOutcomeAndSuccessorLink() throws Exception {
    Employee ic = saveEmployee("grace@x.test", RoleType.IC);
    WeeklyPlan plan = saveDraftPlan(ic.getId());
    WeeklyCommitment src =
        savePlanned(
            plan.getId(),
            "Draft the activation runbook",
            "the runbook detail",
            Priority.P1,
            WorkType.STRATEGIC,
            Confidence.MEDIUM);

    lock(plan.getId(), ic.getId());
    startReconciliation(plan.getId(), ic.getId());
    Baseline beforeCf = Baseline.of(commitments.findById(src.getId()).orElseThrow());

    carryForward(src.getId(), ic.getId());

    // source: ONLY reconciliation_outcome changed (null → CARRIED_FORWARD); baseline byte-identical
    WeeklyCommitment after = commitments.findById(src.getId()).orElseThrow();
    assertThat(after.getReconciliationOutcome()).isEqualTo(ReconciliationOutcome.CARRIED_FORWARD);
    assertThat(Baseline.of(after)).isEqualTo(beforeCf);
    assertThat(after.getCarryForwardSourceCommitmentId()).isNull(); // the source is not a successor

    // successor: a distinct next-week plan, self-linked back to the source
    WeeklyPlan nextPlan =
        plans.findByEmployeeIdAndWeekStartDate(ic.getId(), NEXT_WEEK).orElseThrow();
    assertThat(nextPlan.getId()).isNotEqualTo(plan.getId());
    List<WeeklyCommitment> successors =
        commitments.findByWeeklyPlanIdOrderByIdAsc(nextPlan.getId());
    assertThat(successors).hasSize(1);
    assertThat(successors.get(0).getCarryForwardSourceCommitmentId()).isEqualTo(src.getId());
  }

  // === #3 the rule-#2 gate fires on the REAL locked state mid-reconciliation (end-to-end)
  @Test
  void plannedBaselineEdit_midReconciliation_rejected() throws Exception {
    Employee ic = saveEmployee("grace@x.test", RoleType.IC);
    WeeklyPlan plan = saveDraftPlan(ic.getId());
    WeeklyCommitment planned =
        savePlanned(
            plan.getId(),
            "Draft the activation runbook",
            "the runbook detail",
            Priority.P1,
            WorkType.STRATEGIC,
            Confidence.MEDIUM);

    lock(plan.getId(), ic.getId());
    startReconciliation(plan.getId(), ic.getId());

    // a PLANNED baseline edit during RECONCILING → 409 LOCKED_BASELINE_EDIT
    mvc.perform(
            patch("/api/commitments/" + planned.getId())
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"a rewritten title\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LOCKED_BASELINE_EDIT"));
    assertThat(commitments.findById(planned.getId()).orElseThrow().getTitle())
        .isEqualTo("Draft the activation runbook"); // unchanged
  }

  // === #4 the chain reaches RECONCILED with reconciledAt stamped
  @Test
  void chainReachesReconciled() throws Exception {
    Employee ic = saveEmployee("grace@x.test", RoleType.IC);
    WeeklyPlan plan = saveDraftPlan(ic.getId());
    WeeklyCommitment planned =
        savePlanned(
            plan.getId(),
            "Draft the activation runbook",
            "the runbook detail",
            Priority.P1,
            WorkType.STRATEGIC,
            Confidence.MEDIUM);

    lock(plan.getId(), ic.getId());
    startReconciliation(plan.getId(), ic.getId());
    recordOutcome(planned.getId(), ic.getId(), ReconciliationOutcome.COMPLETED);

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/close-reconciliation")
                .header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("RECONCILED"))
        .andExpect(jsonPath("$.reconciledAt").exists());

    WeeklyPlan reconciled = plans.findById(plan.getId()).orElseThrow();
    assertThat(reconciled.getState()).isEqualTo(PlanState.RECONCILED);
    assertThat(reconciled.getReconciledAt()).isNotNull();
  }
}
