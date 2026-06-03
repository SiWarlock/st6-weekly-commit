package com.st6.wc.dispute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.st6.wc.audit.repo.AuditEventRepository;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.dispute.repo.AlignmentDisputeRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.DisputeStatus;
import com.st6.wc.enums.FlagType;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.ReviewStatus;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.WorkType;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.review.ManagerReview;
import com.st6.wc.review.repo.ManagerReviewRepository;
import com.st6.wc.support.AbstractAppBootTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code POST /api/commitments/{id}/disputes} (E17) end-to-end through the demo-mode 2.6 chain
 * (task 5.3, §3 dispute lifecycle / §5 / §6 manager authz / rule #6 / §15 audit) against real PG16
 * ({@link AbstractAppBootTest}). Proves: the direct manager opens an OPEN dispute; the
 * single-unresolved invariant (an existing OPEN/IC_RESPONDED → 409 SECOND_OPEN_DISPUTE; historical
 * RESOLVED allowed); the review re-derivation (REVIEWED → REVIEWED_WITH_DISPUTES); the §33
 * manager-capability authz (IC-self → 403, non-direct/unrelated manager → 404, each audited); the
 * DRAFT guard; managerNote / flagType validation; the note-body-free audit; unauth → 401.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class OpenDisputeEndpointTest extends AbstractAppBootTest {

  private static final String HEADER = "X-Demo-Employee-Id";
  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);

  @Autowired private MockMvc mvc;
  @Autowired private EmployeeRepository employees;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private WeeklyCommitmentRepository commitments;
  @Autowired private ManagerRelationshipRepository relationships;
  @Autowired private ManagerReviewRepository reviews;
  @Autowired private AlignmentDisputeRepository disputes;
  @Autowired private AuditEventRepository auditEvents;

  @AfterEach
  void cleanup() {
    auditEvents.deleteAll();
    disputes.deleteAll();
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

  private WeeklyCommitment saveCommitment(UUID planId) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(planId);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("Ship it");
    c.setPriority(Priority.P1);
    c.setWorkType(WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(AlignmentStatus.ALIGNED);
    return commitments.saveAndFlush(c);
  }

  private void saveDispute(UUID commitmentId, UUID managerId, DisputeStatus status) {
    AlignmentDispute d = new AlignmentDispute();
    d.setId(UUID.randomUUID());
    d.setCommitmentId(commitmentId);
    d.setManagerEmployeeId(managerId);
    d.setStatus(status);
    d.setFlagType(FlagType.MISALIGNED);
    d.setManagerNote("please revisit the alignment");
    disputes.saveAndFlush(d);
  }

  private ManagerReview saveReview(UUID planId, UUID managerId, ReviewStatus status) {
    ManagerReview r = new ManagerReview();
    r.setId(UUID.randomUUID());
    r.setWeeklyPlanId(planId);
    r.setManagerEmployeeId(managerId);
    r.setStatus(status);
    r.setReviewDueAt(Instant.parse("2026-06-04T22:00:00Z"));
    if (status != ReviewStatus.NOT_REVIEWED) {
      r.setReviewedAt(Instant.parse("2026-06-03T12:00:00Z"));
    }
    return reviews.saveAndFlush(r);
  }

  private void saveActiveRelationship(UUID managerId, UUID reportId) {
    ManagerRelationship rel = new ManagerRelationship();
    rel.setId(UUID.randomUUID());
    rel.setManagerEmployeeId(managerId);
    rel.setDirectReportEmployeeId(reportId);
    rel.setActive(true);
    relationships.saveAndFlush(rel);
  }

  private static String body(String flagType, String managerNote) {
    return "{\"flagType\":%s,\"managerNote\":%s}"
        .formatted(
            flagType == null ? "null" : "\"" + flagType + "\"",
            managerNote == null ? "null" : "\"" + managerNote + "\"");
  }

  // --- #1 the direct manager opens an OPEN dispute (opener = the manager) + DISPUTE_OPENED audit
  // --
  @Test
  void open_byDirectManager_createsOpenDispute() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    WeeklyCommitment c = saveCommitment(plan.getId());

    mvc.perform(
            post("/api/commitments/" + c.getId() + "/disputes")
                .header(HEADER, mgr.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("NEEDS_REVISION", "please re-scope to the SO")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.flagType").value("NEEDS_REVISION"))
        .andExpect(jsonPath("$.commitmentId").value(c.getId().toString()))
        .andExpect(jsonPath("$.managerEmployeeId").value(mgr.getId().toString()))
        // a DTO, never the entity — the audit quartet must not leak across the boundary (FP #3)
        .andExpect(jsonPath("$.createdAt").doesNotExist())
        .andExpect(jsonPath("$.createdBy").doesNotExist())
        .andExpect(jsonPath("$.updatedAt").doesNotExist())
        .andExpect(jsonPath("$.updatedBy").doesNotExist());

    assertThat(disputes.findAll()).hasSize(1);
    assertThat(disputes.findAll().get(0).getStatus()).isEqualTo(DisputeStatus.OPEN);
    assertThat(auditEvents.findAll().stream().anyMatch(a -> a.getAction().equals("DISPUTE_OPENED")))
        .isTrue();
  }

  // --- #2 single-unresolved: an existing OPEN dispute → 409 SECOND_OPEN_DISPUTE ----
  @Test
  void open_secondUnresolvedOpen_409() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    WeeklyCommitment c = saveCommitment(plan.getId());
    saveDispute(c.getId(), mgr.getId(), DisputeStatus.OPEN);

    mvc.perform(
            post("/api/commitments/" + c.getId() + "/disputes")
                .header(HEADER, mgr.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("MISALIGNED", "second flag")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SECOND_OPEN_DISPUTE"));
    assertThat(disputes.findAll()).hasSize(1); // no second dispute created
  }

  // --- #3 single-unresolved: an existing IC_RESPONDED dispute also blocks → 409 ----
  @Test
  void open_secondUnresolvedIcResponded_409() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    WeeklyCommitment c = saveCommitment(plan.getId());
    saveDispute(c.getId(), mgr.getId(), DisputeStatus.IC_RESPONDED);

    mvc.perform(
            post("/api/commitments/" + c.getId() + "/disputes")
                .header(HEADER, mgr.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("MISALIGNED", "second flag")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SECOND_OPEN_DISPUTE"));
  }

  // --- #4 historical RESOLVED disputes do NOT block a new OPEN ----
  @Test
  void open_historicalResolvedPlusNewOpen_allowed() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    WeeklyCommitment c = saveCommitment(plan.getId());
    saveDispute(c.getId(), mgr.getId(), DisputeStatus.RESOLVED);

    mvc.perform(
            post("/api/commitments/" + c.getId() + "/disputes")
                .header(HEADER, mgr.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("NEEDS_REVISION", "new flag")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("OPEN"));
    assertThat(disputes.findAll()).hasSize(2); // the RESOLVED + the new OPEN coexist
  }

  // --- #5 review re-derivation: a REVIEWED review flips to REVIEWED_WITH_DISPUTES on open ----
  @Test
  void open_onReviewedPlan_reDerivesReviewedWithDisputes() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    WeeklyCommitment c = saveCommitment(plan.getId());
    ManagerReview review = saveReview(plan.getId(), mgr.getId(), ReviewStatus.REVIEWED);

    mvc.perform(
            post("/api/commitments/" + c.getId() + "/disputes")
                .header(HEADER, mgr.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("MISALIGNED", "this drifted")))
        .andExpect(status().isCreated());

    assertThat(reviews.findById(review.getId()).orElseThrow().getStatus())
        .isEqualTo(ReviewStatus.REVIEWED_WITH_DISPUTES);
  }

  // --- #6 §33: the owning IC cannot open a dispute on their own commitment → 403 + denial audit.
  // The IC legitimately uses /api/commitments (E6/E7) → existence not hidden → 403 (NOT 404). ----
  @Test
  void open_byIcSelf_403_andAudit() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    WeeklyCommitment c = saveCommitment(plan.getId());

    mvc.perform(
            post("/api/commitments/" + c.getId() + "/disputes")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("NEEDS_REVISION", "self flag")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("IC_CANNOT_OPEN_DISPUTE"));
    assertThat(disputes.findAll()).isEmpty();
    assertThat(auditEvents.findAll()).hasSize(1);
    assertThat(auditEvents.findAll().get(0).getAction()).isEqualTo("AUTHORIZATION_DENIED");
  }

  // --- #7 a manager with no relationship to the owner → 404 (IDOR-safe) + audit ----
  @Test
  void open_byNonDirectManager_404() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee directMgr = saveEmployee("direct@x.test", RoleType.MANAGER);
    Employee strangerMgr = saveEmployee("stranger@x.test", RoleType.MANAGER);
    saveActiveRelationship(directMgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    WeeklyCommitment c = saveCommitment(plan.getId());

    mvc.perform(
            post("/api/commitments/" + c.getId() + "/disputes")
                .header(HEADER, strangerMgr.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("NEEDS_REVISION", "stranger flag")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").doesNotExist());
    assertThat(disputes.findAll()).isEmpty();
    assertThat(auditEvents.findAll()).hasSize(1);
    assertThat(auditEvents.findAll().get(0).getAction()).isEqualTo("AUTHORIZATION_DENIED");
  }

  // --- #8 a manager of a DIFFERENT report → 404 ----
  @Test
  void open_byUnrelatedManager_404() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee directMgr = saveEmployee("direct@x.test", RoleType.MANAGER);
    Employee otherIc = saveEmployee("other-ic@x.test", RoleType.IC);
    Employee otherMgr = saveEmployee("other-mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(directMgr.getId(), ic.getId());
    saveActiveRelationship(otherMgr.getId(), otherIc.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    WeeklyCommitment c = saveCommitment(plan.getId());

    mvc.perform(
            post("/api/commitments/" + c.getId() + "/disputes")
                .header(HEADER, otherMgr.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("NEEDS_REVISION", "unrelated flag")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").doesNotExist());
  }

  // --- #9 state guard: a DRAFT parent plan → 409 ILLEGAL_STATE_TRANSITION ----
  @Test
  void open_planDraft_409() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);
    WeeklyCommitment c = saveCommitment(plan.getId());

    mvc.perform(
            post("/api/commitments/" + c.getId() + "/disputes")
                .header(HEADER, mgr.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("NEEDS_REVISION", "early flag")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));
    assertThat(disputes.findAll()).isEmpty();
  }

  // --- #10 validation: a blank managerNote → 400 VALIDATION_ERROR ----
  @Test
  void open_blankManagerNote_400() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    WeeklyCommitment c = saveCommitment(plan.getId());

    mvc.perform(
            post("/api/commitments/" + c.getId() + "/disputes")
                .header(HEADER, mgr.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("NEEDS_REVISION", "   ")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  // --- #11 validation: an unknown flagType → 400 (HttpMessageNotReadable, never 500) ----
  @Test
  void open_unknownFlagType_400() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    WeeklyCommitment c = saveCommitment(plan.getId());

    mvc.perform(
            post("/api/commitments/" + c.getId() + "/disputes")
                .header(HEADER, mgr.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("NOPE", "flag")))
        .andExpect(status().isBadRequest());
  }

  // --- #12 the DISPUTE_OPENED audit carries NO managerNote body (§15 / REQ-S-006) ----
  @Test
  void open_auditHasNoNoteBody() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    WeeklyCommitment c = saveCommitment(plan.getId());

    mvc.perform(
            post("/api/commitments/" + c.getId() + "/disputes")
                .header(HEADER, mgr.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("MISALIGNED", "SENSITIVE-DISPUTE-NOTE-XYZ")))
        .andExpect(status().isCreated());

    var opened =
        auditEvents.findAll().stream()
            .filter(a -> a.getAction().equals("DISPUTE_OPENED"))
            .findFirst()
            .orElseThrow();
    assertThat(opened.getMetadataJson()).doesNotContain("SENSITIVE-DISPUTE-NOTE-XYZ");
    assertThat(opened.getSummary()).doesNotContain("SENSITIVE-DISPUTE-NOTE-XYZ");
  }

  // --- #13 unauthenticated → 401 ----
  @Test
  void open_unauthenticated_401() throws Exception {
    mvc.perform(
            post("/api/commitments/" + UUID.randomUUID() + "/disputes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("NEEDS_REVISION", "flag")))
        .andExpect(status().isUnauthorized());
  }
}
