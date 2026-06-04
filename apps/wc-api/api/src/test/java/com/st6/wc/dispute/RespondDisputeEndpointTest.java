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
 * {@code POST /api/disputes/{id}/respond} (E18) end-to-end through the demo-mode 2.6 chain (task
 * 5.4, §3 {@code OPEN→IC_RESPONDED} / §5 / §6 IC-owner authz / rule-#2 SO-revision exception / §15
 * audit) against real PG16 + the V4 RCDO seed ({@link AbstractAppBootTest}). Proves: the owning IC
 * responds (rationale and/or SO revision); the rule-#2 exception is scoped to {@code
 * supportingOutcomeId} only; the §33 respond-variant authz (manager → 403, unrelated → 404, each
 * audited); the OPEN-only state guard; validation; the note-body-free audit; the dispute stays
 * unresolved (review unchanged); unauth → 401.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class RespondDisputeEndpointTest extends AbstractAppBootTest {

  private static final String HEADER = "X-Demo-Employee-Id";
  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);
  private static final UUID SO_OLD = UUID.fromString("c0000000-0000-0000-0000-000000000001");
  private static final UUID SO_NEW = UUID.fromString("c0000000-0000-0000-0000-000000000002");

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

  private WeeklyPlan saveLockedPlan(UUID ownerId) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(ownerId);
    p.setWeekStartDate(WEEK);
    p.setWeekEndDate(WEEK.plusDays(6));
    p.setState(PlanState.LOCKED);
    return plans.saveAndFlush(p);
  }

  private WeeklyCommitment saveCommitment(UUID planId) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(planId);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("Draft the runbook");
    c.setDescription("the runbook detail");
    c.setSupportingOutcomeId(SO_OLD);
    c.setPriority(Priority.P1);
    c.setWorkType(WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(AlignmentStatus.ALIGNED);
    return commitments.saveAndFlush(c);
  }

  private AlignmentDispute saveDispute(UUID commitmentId, UUID managerId, DisputeStatus status) {
    AlignmentDispute d = new AlignmentDispute();
    d.setId(UUID.randomUUID());
    d.setCommitmentId(commitmentId);
    d.setManagerEmployeeId(managerId);
    d.setStatus(status);
    d.setFlagType(FlagType.MISALIGNED);
    d.setManagerNote("please revisit the alignment");
    return disputes.saveAndFlush(d);
  }

  private void saveActiveRelationship(UUID managerId, UUID reportId) {
    ManagerRelationship rel = new ManagerRelationship();
    rel.setId(UUID.randomUUID());
    rel.setManagerEmployeeId(managerId);
    rel.setDirectReportEmployeeId(reportId);
    rel.setActive(true);
    relationships.saveAndFlush(rel);
  }

  private ManagerReview saveReview(UUID planId, UUID managerId, ReviewStatus status) {
    ManagerReview r = new ManagerReview();
    r.setId(UUID.randomUUID());
    r.setWeeklyPlanId(planId);
    r.setManagerEmployeeId(managerId);
    r.setStatus(status);
    r.setReviewDueAt(Instant.parse("2026-06-04T22:00:00Z"));
    return reviews.saveAndFlush(r);
  }

  /** A seeded IC owner + direct manager + active relationship + LOCKED plan + commitment. */
  private record Fixture(Employee ic, Employee mgr, WeeklyCommitment commitment) {}

  private Fixture fixture() {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = saveLockedPlan(ic.getId());
    return new Fixture(ic, mgr, saveCommitment(plan.getId()));
  }

  // --- #1 owning IC responds with rationale → 200 IC_RESPONDED + icResponse + DISPUTE_RESPONDED --
  @Test
  void respond_byOwningIc_withRationale_icResponded() throws Exception {
    Fixture f = fixture();
    AlignmentDispute d = saveDispute(f.commitment().getId(), f.mgr().getId(), DisputeStatus.OPEN);

    mvc.perform(
            post("/api/disputes/" + d.getId() + "/respond")
                .header(HEADER, f.ic().getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"icResponse\":\"I re-scoped it as asked\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IC_RESPONDED"))
        .andExpect(jsonPath("$.icResponse").value("I re-scoped it as asked"));

    assertThat(disputes.findById(d.getId()).orElseThrow().getStatus())
        .isEqualTo(DisputeStatus.IC_RESPONDED);
    assertThat(
            auditEvents.findAll().stream().anyMatch(a -> a.getAction().equals("DISPUTE_RESPONDED")))
        .isTrue();
  }

  // --- #2 the rule-#2 exception: a SO revision updates the disputed commitment's
  // supportingOutcomeId
  @Test
  void respond_revisesSupportingOutcome_updatesCommitmentSo() throws Exception {
    Fixture f = fixture();
    AlignmentDispute d = saveDispute(f.commitment().getId(), f.mgr().getId(), DisputeStatus.OPEN);

    mvc.perform(
            post("/api/disputes/" + d.getId() + "/respond")
                .header(HEADER, f.ic().getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supportingOutcomeId\":\"" + SO_NEW + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IC_RESPONDED"));

    assertThat(commitments.findById(f.commitment().getId()).orElseThrow().getSupportingOutcomeId())
        .isEqualTo(SO_NEW); // the locked SO was revised via the dispute-respond path
    // the rule-#2 exception leaves a safe audit trail: the SO revision (ids only) is recorded
    var responded =
        auditEvents.findAll().stream()
            .filter(a -> a.getAction().equals("DISPUTE_RESPONDED"))
            .findFirst()
            .orElseThrow();
    assertThat(responded.getMetadataJson())
        .contains("supportingOutcomeRevised")
        .contains(SO_NEW.toString());
  }

  // --- #3 SAFETY pin: the SO-revision touches ONLY supportingOutcomeId — all else byte-identical
  // --
  @Test
  void respond_doesNotTouchOtherBaselineFields() throws Exception {
    Fixture f = fixture();
    AlignmentDispute d = saveDispute(f.commitment().getId(), f.mgr().getId(), DisputeStatus.OPEN);

    mvc.perform(
            post("/api/disputes/" + d.getId() + "/respond")
                .header(HEADER, f.ic().getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supportingOutcomeId\":\"" + SO_NEW + "\"}"))
        .andExpect(status().isOk());

    WeeklyCommitment after = commitments.findById(f.commitment().getId()).orElseThrow();
    assertThat(after.getSupportingOutcomeId()).isEqualTo(SO_NEW); // the ONLY change
    assertThat(after.getTitle()).isEqualTo("Draft the runbook");
    assertThat(after.getDescription()).isEqualTo("the runbook detail");
    assertThat(after.getPriority()).isEqualTo(Priority.P1);
    assertThat(after.getWorkType()).isEqualTo(WorkType.STRATEGIC);
    assertThat(after.getConfidence()).isEqualTo(Confidence.MEDIUM);
    assertThat(after.getAlignmentStatus()).isEqualTo(AlignmentStatus.ALIGNED);
  }

  // --- #4 §33 respond-variant: the disputed commitment's direct manager has NO respond capability
  // → 403 MANAGER_CANNOT_RESPOND_DISPUTE + a denial audit (the manager uses /api/disputes
  // legitimately
  // to open/resolve, so existence is not hidden → 403, not 404). ----
  @Test
  void respond_byDirectManager_403MgrCannotRespond() throws Exception {
    Fixture f = fixture();
    AlignmentDispute d = saveDispute(f.commitment().getId(), f.mgr().getId(), DisputeStatus.OPEN);

    mvc.perform(
            post("/api/disputes/" + d.getId() + "/respond")
                .header(HEADER, f.mgr().getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"icResponse\":\"manager trying to respond\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("MANAGER_CANNOT_RESPOND_DISPUTE"));
    assertThat(disputes.findById(d.getId()).orElseThrow().getStatus())
        .isEqualTo(DisputeStatus.OPEN); // untouched
    assertThat(auditEvents.findAll()).hasSize(1);
    assertThat(auditEvents.findAll().get(0).getAction()).isEqualTo("AUTHORIZATION_DENIED");
  }

  // --- #5 an unrelated actor (no relationship to the commitment) → 404 IDOR + audit ----
  @Test
  void respond_byUnrelatedActor_404() throws Exception {
    Fixture f = fixture();
    Employee stranger = saveEmployee("stranger@x.test", RoleType.IC);
    AlignmentDispute d = saveDispute(f.commitment().getId(), f.mgr().getId(), DisputeStatus.OPEN);

    mvc.perform(
            post("/api/disputes/" + d.getId() + "/respond")
                .header(HEADER, stranger.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"icResponse\":\"not mine\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").doesNotExist());
    assertThat(disputes.findById(d.getId()).orElseThrow().getStatus())
        .isEqualTo(DisputeStatus.OPEN);
    assertThat(auditEvents.findAll()).hasSize(1);
    assertThat(auditEvents.findAll().get(0).getAction()).isEqualTo("AUTHORIZATION_DENIED");
  }

  // --- #6 state guard: an already-IC_RESPONDED dispute → 409 ILLEGAL_STATE_TRANSITION ----
  @Test
  void respond_nonOpenDispute_409() throws Exception {
    Fixture f = fixture();
    AlignmentDispute d =
        saveDispute(f.commitment().getId(), f.mgr().getId(), DisputeStatus.IC_RESPONDED);

    mvc.perform(
            post("/api/disputes/" + d.getId() + "/respond")
                .header(HEADER, f.ic().getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"icResponse\":\"again\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));
  }

  // --- #7 validation: an unknown supportingOutcomeId → 400 VALIDATION_ERROR ----
  @Test
  void respond_unknownSupportingOutcome_400() throws Exception {
    Fixture f = fixture();
    AlignmentDispute d = saveDispute(f.commitment().getId(), f.mgr().getId(), DisputeStatus.OPEN);

    mvc.perform(
            post("/api/disputes/" + d.getId() + "/respond")
                .header(HEADER, f.ic().getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supportingOutcomeId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  // --- #8 at-least-one-of: an empty body (neither field) → 400 VALIDATION_ERROR ----
  @Test
  void respond_emptyBody_400() throws Exception {
    Fixture f = fixture();
    AlignmentDispute d = saveDispute(f.commitment().getId(), f.mgr().getId(), DisputeStatus.OPEN);

    mvc.perform(
            post("/api/disputes/" + d.getId() + "/respond")
                .header(HEADER, f.ic().getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  // --- #9 the DISPUTE_RESPONDED audit carries NO icResponse body (§15 / REQ-S-006) ----
  @Test
  void respond_auditHasNoResponseBody() throws Exception {
    Fixture f = fixture();
    AlignmentDispute d = saveDispute(f.commitment().getId(), f.mgr().getId(), DisputeStatus.OPEN);

    mvc.perform(
            post("/api/disputes/" + d.getId() + "/respond")
                .header(HEADER, f.ic().getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"icResponse\":\"SENSITIVE-IC-RESPONSE-XYZ\"}"))
        .andExpect(status().isOk());

    var responded =
        auditEvents.findAll().stream()
            .filter(a -> a.getAction().equals("DISPUTE_RESPONDED"))
            .findFirst()
            .orElseThrow();
    assertThat(responded.getMetadataJson()).doesNotContain("SENSITIVE-IC-RESPONSE-XYZ");
    assertThat(responded.getSummary()).doesNotContain("SENSITIVE-IC-RESPONSE-XYZ");
  }

  // --- #10 no side effect: respond keeps the dispute unresolved → review status unchanged ----
  @Test
  void respond_doesNotChangeReviewStatus() throws Exception {
    Fixture f = fixture();
    ManagerReview review =
        saveReview(
            f.commitment().getWeeklyPlanId(), f.mgr().getId(), ReviewStatus.REVIEWED_WITH_DISPUTES);
    AlignmentDispute d = saveDispute(f.commitment().getId(), f.mgr().getId(), DisputeStatus.OPEN);

    mvc.perform(
            post("/api/disputes/" + d.getId() + "/respond")
                .header(HEADER, f.ic().getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"icResponse\":\"rationale\"}"))
        .andExpect(status().isOk());

    assertThat(reviews.findById(review.getId()).orElseThrow().getStatus())
        .isEqualTo(ReviewStatus.REVIEWED_WITH_DISPUTES); // unresolved → review untouched
  }

  // --- #11 unknown dispute id → 404 ----
  @Test
  void respond_unknownDisputeId_404() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);

    mvc.perform(
            post("/api/disputes/" + UUID.randomUUID() + "/respond")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"icResponse\":\"hello\"}"))
        .andExpect(status().isNotFound());
  }

  // --- #12 unauthenticated → 401 ----
  @Test
  void respond_unauthenticated_401() throws Exception {
    mvc.perform(
            post("/api/disputes/" + UUID.randomUUID() + "/respond")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"icResponse\":\"hello\"}"))
        .andExpect(status().isUnauthorized());
  }
}
