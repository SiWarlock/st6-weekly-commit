package com.st6.wc.commitment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.st6.wc.audit.repo.AuditEventRepository;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.ReconciliationOutcome;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.WorkType;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.repo.ManagerHeatmapCellRepository;
import com.st6.wc.projection.repo.ManagerPlanSummaryRepository;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.ManagerReview;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.support.AbstractAppBootTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code PATCH /api/commitments/{id}} (E6) + {@code DELETE /api/commitments/{id}} (E7) end-to-end
 * through the demo-mode 2.6 chain (task 3.4b, §3 rule #2 / §5 / §6 rule #3 / Appendix E rule 2)
 * against real PG16 ({@link AbstractAppBootTest}). Proves: owning-IC partial edit of a {@code
 * DRAFT} commitment (only provided fields touched, validation reused); the rule-#3 mutation
 * chokepoint (cross-IC → codeless 404+audit; a manager-direct-report who can <em>read</em> →
 * 403+audit, NOT edit); the two distinct post-lock gates — a frozen-baseline-field edit → {@code
 * 409 LOCKED_BASELINE_EDIT} (rule #2), an {@code alignmentStatus} edit → {@code 409
 * ILLEGAL_STATE_TRANSITION} (constraint {@code alignment_status_read_only_post_lock}); and delete →
 * 204 on a DRAFT plan, 409 on a non-DRAFT plan.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class CommitmentPatchDeleteEndpointTest extends AbstractAppBootTest {

  private static final String HEADER = "X-Demo-Employee-Id";
  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);
  private static final UUID SEED_SO_1_1 = UUID.fromString("c0000000-0000-0000-0000-000000000001");
  private static final UUID SEED_SO_1_2 = UUID.fromString("c0000000-0000-0000-0000-000000000002");

  @Autowired private MockMvc mvc;
  @Autowired private EmployeeRepository employees;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private WeeklyCommitmentRepository commitments;
  @Autowired private ManagerRelationshipRepository relationships;
  @Autowired private ManagerReviewRepository reviews;
  @Autowired private ManagerPlanSummaryRepository summaries;
  @Autowired private ManagerHeatmapCellRepository heatmapCells;
  @Autowired private AuditEventRepository auditEvents;

  @AfterEach
  void cleanup() {
    auditEvents.deleteAll();
    summaries.deleteAll();
    heatmapCells.deleteAll();
    reviews.deleteAll();
    commitments.deleteAll();
    relationships.deleteAll();
    plans.deleteAll();
    employees.deleteAll();
  }

  private Employee saveEmployee(String email, RoleType role) {
    Employee e = new Employee();
    e.setId(UUID.randomUUID());
    e.setEmail(email);
    e.setDisplayName("Name " + email);
    e.setRole(role);
    e.setActive(true);
    return employees.saveAndFlush(e);
  }

  private WeeklyPlan savePlan(UUID ownerId, PlanState state) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(ownerId);
    p.setWeekStartDate(WEEK);
    p.setWeekEndDate(WEEK.plusDays(6));
    p.setState(state);
    return plans.saveAndFlush(p);
  }

  private WeeklyCommitment saveCommitment(UUID planId, UUID soId) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(planId);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("Original title");
    c.setDescription("original description");
    c.setSupportingOutcomeId(soId);
    c.setPriority(Priority.P1);
    c.setWorkType(WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(AlignmentStatus.NEEDS_REVIEW);
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

  private void saveReview(UUID planId, UUID managerId) {
    ManagerReview r = new ManagerReview();
    r.setId(UUID.randomUUID());
    r.setWeeklyPlanId(planId);
    r.setManagerEmployeeId(managerId);
    r.setStatus(ReviewStatus.NOT_REVIEWED);
    r.setReviewDueAt(Instant.parse("2026-06-02T22:00:00Z"));
    reviews.saveAndFlush(r);
  }

  // ===================== PATCH (E6) =====================

  // --- #1: owning IC, DRAFT — patch only title+priority → 200; other fields untouched ----
  @Test
  void patch_draftOwner_updatesProvidedFieldsOnly() throws Exception {
    Employee ic = saveEmployee("ada@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);
    WeeklyCommitment c = saveCommitment(plan.getId(), SEED_SO_1_1);

    mvc.perform(
            patch("/api/commitments/" + c.getId())
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"  New   title  \",\"priority\":\"P2\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("New title")) // normalized (collapse + strip)
        .andExpect(jsonPath("$.priority").value("P2"));

    WeeklyCommitment after = commitments.findById(c.getId()).orElseThrow();
    assertThat(after.getTitle()).isEqualTo("New title");
    assertThat(after.getPriority()).isEqualTo(Priority.P2);
    // untouched fields keep their seeded values
    assertThat(after.getDescription()).isEqualTo("original description");
    assertThat(after.getSupportingOutcomeId()).isEqualTo(SEED_SO_1_1);
    assertThat(after.getWorkType()).isEqualTo(WorkType.STRATEGIC);
    assertThat(after.getConfidence()).isEqualTo(Confidence.MEDIUM);
    assertThat(after.getAlignmentStatus()).isEqualTo(AlignmentStatus.NEEDS_REVIEW);
  }

  // --- #2: re-link supportingOutcomeId — valid SO resolves; unknown SO → 400 ----
  @Test
  void patch_relinkSupportingOutcome_validResolves_unknownRejected() throws Exception {
    Employee ic = saveEmployee("ada@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);
    WeeklyCommitment c = saveCommitment(plan.getId(), SEED_SO_1_1);

    // re-link to a different valid seeded SO
    mvc.perform(
            patch("/api/commitments/" + c.getId())
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supportingOutcomeId\":\"" + SEED_SO_1_2 + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.supportingOutcomeId").value(SEED_SO_1_2.toString()));
    assertThat(commitments.findById(c.getId()).orElseThrow().getSupportingOutcomeId())
        .isEqualTo(SEED_SO_1_2);

    // unknown SO id → 400 VALIDATION_ERROR, no change persisted
    mvc.perform(
            patch("/api/commitments/" + c.getId())
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supportingOutcomeId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    assertThat(commitments.findById(c.getId()).orElseThrow().getSupportingOutcomeId())
        .isEqualTo(SEED_SO_1_2); // unchanged
  }

  // --- #3: validation reused (3.4a suite) — blank title / 256-cp title / unknown enum → 400 ----
  @Test
  void patch_validationReused_blankOversizeUnknownEnum_400() throws Exception {
    Employee ic = saveEmployee("ada@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);
    WeeklyCommitment c = saveCommitment(plan.getId(), SEED_SO_1_1);

    // a PRESENT but blank title → 400 (reused @NotBlank)
    mvc.perform(
            patch("/api/commitments/" + c.getId())
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    // 256 code points → 400 (reused @CodePointSize)
    mvc.perform(
            patch("/api/commitments/" + c.getId())
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + "a".repeat(256) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    // unknown enum value → 400, never 500 (reused HttpMessageNotReadable handler)
    mvc.perform(
            patch("/api/commitments/" + c.getId())
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"priority\":\"NOPE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    // nothing mutated
    assertThat(commitments.findById(c.getId()).orElseThrow().getTitle())
        .isEqualTo("Original title");
  }

  // --- #4: rule #2 — a LOCKED plan freezes EVERY baseline field → 409 LOCKED_BASELINE_EDIT ----
  @Test
  void patch_lockedPlan_eachBaselineField_409LockedBaselineEdit() throws Exception {
    Employee ic = saveEmployee("ada@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    WeeklyCommitment c = saveCommitment(plan.getId(), SEED_SO_1_1);

    List<String> baselineEdits =
        List.of(
            "{\"title\":\"x\"}",
            "{\"description\":\"y\"}",
            "{\"supportingOutcomeId\":\"" + SEED_SO_1_2 + "\"}",
            "{\"priority\":\"P2\"}",
            "{\"workType\":\"MAINTENANCE\"}",
            "{\"confidence\":\"HIGH\"}");

    for (String body : baselineEdits) {
      mvc.perform(
              patch("/api/commitments/" + c.getId())
                  .header(HEADER, ic.getId().toString())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("LOCKED_BASELINE_EDIT"));
    }

    // the frozen baseline is unchanged
    WeeklyCommitment after = commitments.findById(c.getId()).orElseThrow();
    assertThat(after.getTitle()).isEqualTo("Original title");
    assertThat(after.getSupportingOutcomeId()).isEqualTo(SEED_SO_1_1);
    assertThat(after.getPriority()).isEqualTo(Priority.P1);
  }

  // --- #5: alignmentStatus post-lock is read-only → 409 ILLEGAL_STATE_TRANSITION (distinct code) -
  @Test
  void patch_lockedPlan_alignmentStatus_409IllegalStateTransition() throws Exception {
    Employee ic = saveEmployee("ada@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    WeeklyCommitment c = saveCommitment(plan.getId(), SEED_SO_1_1);

    mvc.perform(
            patch("/api/commitments/" + c.getId())
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"alignmentStatus\":\"ALIGNED\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"))
        .andExpect(jsonPath("$.constraint").value("alignment_status_read_only_post_lock"));

    assertThat(commitments.findById(c.getId()).orElseThrow().getAlignmentStatus())
        .isEqualTo(AlignmentStatus.NEEDS_REVIEW); // unchanged
  }

  // --- #6: alignmentStatus on a DRAFT plan is editable (IC self-assessment) → 200 ----
  @Test
  void patch_draftPlan_alignmentStatus_allowed() throws Exception {
    Employee ic = saveEmployee("ada@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);
    WeeklyCommitment c = saveCommitment(plan.getId(), SEED_SO_1_1);

    mvc.perform(
            patch("/api/commitments/" + c.getId())
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"alignmentStatus\":\"ALIGNED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.alignmentStatus").value("ALIGNED"));
    assertThat(commitments.findById(c.getId()).orElseThrow().getAlignmentStatus())
        .isEqualTo(AlignmentStatus.ALIGNED);
  }

  // --- #6b: present-null clears nullable fields (Optional 3-way) — unlink SO / clear description
  // --
  @Test
  void patch_draftPlan_presentNull_clearsNullableFields() throws Exception {
    Employee ic = saveEmployee("ada@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);
    WeeklyCommitment c = saveCommitment(plan.getId(), SEED_SO_1_1); // linked + has a description

    // explicit JSON null on supportingOutcomeId → UNLINK (set to NULL)
    mvc.perform(
            patch("/api/commitments/" + c.getId())
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supportingOutcomeId\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.supportingOutcomeId").doesNotExist());
    assertThat(commitments.findById(c.getId()).orElseThrow().getSupportingOutcomeId()).isNull();

    // explicit JSON null on description → CLEAR (set to NULL)
    mvc.perform(
            patch("/api/commitments/" + c.getId())
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":null}"))
        .andExpect(status().isOk());
    assertThat(commitments.findById(c.getId()).orElseThrow().getDescription()).isNull();
  }

  // --- #6c: workType=UNPLANNED is rejected on PATCH too (planned-only; UNPLANNED is the E11 path)
  // -
  @Test
  void patch_workTypeUnplanned_400() throws Exception {
    Employee ic = saveEmployee("ada@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);
    WeeklyCommitment c = saveCommitment(plan.getId(), SEED_SO_1_1);

    mvc.perform(
            patch("/api/commitments/" + c.getId())
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"workType\":\"UNPLANNED\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    assertThat(commitments.findById(c.getId()).orElseThrow().getWorkType())
        .isEqualTo(WorkType.STRATEGIC); // unchanged
  }

  // --- #7: a non-owner IC → codeless 404 (IDOR-safe) + one denial audit ----
  @Test
  void patch_crossIc_404_andAudit() throws Exception {
    Employee owner = saveEmployee("owner@x.test", RoleType.IC);
    Employee other = saveEmployee("other@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(owner.getId(), PlanState.DRAFT);
    WeeklyCommitment c = saveCommitment(plan.getId(), SEED_SO_1_1);

    mvc.perform(
            patch("/api/commitments/" + c.getId())
                .header(HEADER, other.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"hijack\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").doesNotExist()); // codeless IDOR 404
    assertThat(auditEvents.findAll()).hasSize(1);
    assertThat(auditEvents.findAll().get(0).getActorEmployeeId()).isEqualTo(other.getId());
    assertThat(commitments.findById(c.getId()).orElseThrow().getTitle())
        .isEqualTo("Original title");
  }

  // --- #8: a manager-direct-report can READ but NOT edit → 403 COMMITMENT_OWNER_REQUIRED + audit -
  @Test
  void patch_managerDirectReport_403_andAudit() throws Exception {
    Employee ic = saveEmployee("report@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);
    WeeklyCommitment c = saveCommitment(plan.getId(), SEED_SO_1_1);

    mvc.perform(
            patch("/api/commitments/" + c.getId())
                .header(HEADER, mgr.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"manager edit\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COMMITMENT_OWNER_REQUIRED"));
    assertThat(auditEvents.findAll()).hasSize(1);
    assertThat(commitments.findById(c.getId()).orElseThrow().getTitle())
        .isEqualTo("Original title");
  }

  // --- #9: unauthenticated PATCH → 401 ----
  @Test
  void patch_unauthenticated_401() throws Exception {
    mvc.perform(
            patch("/api/commitments/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\"}"))
        .andExpect(status().isUnauthorized());
  }

  // ===================== PATCH outcome recording (E6 / 4.1, RECONCILING) =====================

  // --- #O1: owning IC records an outcome on a RECONCILING plan → 200; persisted + projection
  // refreshed (plan_state=RECONCILING) + OUTCOME_RECORDED audit, all in one txn ----
  @Test
  void patch_outcomeInReconciling_persists_andProjectionRefreshed() throws Exception {
    Employee ic = saveEmployee("ada@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.RECONCILING);
    WeeklyCommitment c = saveCommitment(plan.getId(), SEED_SO_1_1);
    saveReview(plan.getId(), mgr.getId());

    mvc.perform(
            patch("/api/commitments/" + c.getId())
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"reconciliationOutcome\":\"COMPLETED\",\"outcomeNote\":\"  Shipped it  \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reconciliationOutcome").value("COMPLETED"));

    WeeklyCommitment after = commitments.findById(c.getId()).orElseThrow();
    assertThat(after.getReconciliationOutcome()).isEqualTo(ReconciliationOutcome.COMPLETED);
    assertThat(after.getOutcomeNote()).isEqualTo("Shipped it"); // normalized (strip ends)
    // §9 synchronous projection refresh in the same txn
    assertThat(summaries.findAll().get(0).getPlanState()).isEqualTo(PlanState.RECONCILING);
    // IC-actor audit
    assertThat(
            auditEvents.findAll().stream().anyMatch(a -> a.getAction().equals("OUTCOME_RECORDED")))
        .isTrue();
  }

  // --- #O2: single-outcome rule — a DIRECT reconciliationOutcome=CARRIED_FORWARD → 400 ----
  @Test
  void patch_directCarriedForward_400() throws Exception {
    Employee ic = saveEmployee("ada@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.RECONCILING);
    WeeklyCommitment c = saveCommitment(plan.getId(), SEED_SO_1_1);

    mvc.perform(
            patch("/api/commitments/" + c.getId())
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reconciliationOutcome\":\"CARRIED_FORWARD\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    assertThat(commitments.findById(c.getId()).orElseThrow().getReconciliationOutcome()).isNull();
  }

  // --- #O3: unknown reconciliationOutcome enum value → 400, never 500 (HttpMessageNotReadable)
  // ----
  @Test
  void patch_unknownOutcomeEnum_400() throws Exception {
    Employee ic = saveEmployee("ada@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.RECONCILING);
    WeeklyCommitment c = saveCommitment(plan.getId(), SEED_SO_1_1);

    mvc.perform(
            patch("/api/commitments/" + c.getId())
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reconciliationOutcome\":\"NOPE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    assertThat(commitments.findById(c.getId()).orElseThrow().getReconciliationOutcome()).isNull();
  }

  // --- #O4: per-state allow-list — an outcome on a LOCKED (not-yet-reconciling) plan → 409 ----
  @Test
  void patch_outcomeWhileLocked_409IllegalState() throws Exception {
    Employee ic = saveEmployee("ada@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    WeeklyCommitment c = saveCommitment(plan.getId(), SEED_SO_1_1);

    mvc.perform(
            patch("/api/commitments/" + c.getId())
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reconciliationOutcome\":\"COMPLETED\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));
    assertThat(commitments.findById(c.getId()).orElseThrow().getReconciliationOutcome()).isNull();
  }

  // --- #O5: outcomeNote validation — blank → NULL persisted; 4001 code points → 400 ----
  @Test
  void patch_outcomeNote_blankClears_oversizeRejected() throws Exception {
    Employee ic = saveEmployee("ada@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.RECONCILING);
    WeeklyCommitment c = saveCommitment(plan.getId(), SEED_SO_1_1);

    // blank outcomeNote → NULL (with a valid outcome)
    mvc.perform(
            patch("/api/commitments/" + c.getId())
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reconciliationOutcome\":\"BLOCKED\",\"outcomeNote\":\"   \"}"))
        .andExpect(status().isOk());
    assertThat(commitments.findById(c.getId()).orElseThrow().getOutcomeNote()).isNull();

    // 4001 code points → 400
    mvc.perform(
            patch("/api/commitments/" + c.getId())
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcomeNote\":\"" + "a".repeat(4001) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  // ===================== DELETE (E7) =====================

  // --- #10: owning IC, DRAFT → 204, row gone ----
  @Test
  void delete_draftOwner_204_rowGone() throws Exception {
    Employee ic = saveEmployee("ada@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);
    WeeklyCommitment c = saveCommitment(plan.getId(), SEED_SO_1_1);

    mvc.perform(delete("/api/commitments/" + c.getId()).header(HEADER, ic.getId().toString()))
        .andExpect(status().isNoContent());
    assertThat(commitments.findById(c.getId())).isEmpty();
  }

  // --- #11: delete on a non-DRAFT (LOCKED) plan → 409 ILLEGAL_STATE_TRANSITION ----
  @Test
  void delete_nonDraftPlan_409() throws Exception {
    Employee ic = saveEmployee("ada@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    WeeklyCommitment c = saveCommitment(plan.getId(), SEED_SO_1_1);

    mvc.perform(delete("/api/commitments/" + c.getId()).header(HEADER, ic.getId().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));
    assertThat(commitments.findById(c.getId())).isPresent(); // not deleted
  }

  // --- #12: a non-owner IC delete → codeless 404 + one audit; row retained ----
  @Test
  void delete_crossIc_404_andAudit() throws Exception {
    Employee owner = saveEmployee("owner@x.test", RoleType.IC);
    Employee other = saveEmployee("other@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(owner.getId(), PlanState.DRAFT);
    WeeklyCommitment c = saveCommitment(plan.getId(), SEED_SO_1_1);

    mvc.perform(delete("/api/commitments/" + c.getId()).header(HEADER, other.getId().toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").doesNotExist());
    assertThat(auditEvents.findAll()).hasSize(1);
    assertThat(commitments.findById(c.getId())).isPresent();
  }

  // --- #13: unauthenticated DELETE → 401 ----
  @Test
  void delete_unauthenticated_401() throws Exception {
    mvc.perform(delete("/api/commitments/" + UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
  }
}
