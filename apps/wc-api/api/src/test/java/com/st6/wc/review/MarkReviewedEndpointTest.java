package com.st6.wc.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.st6.wc.audit.repo.AuditEventRepository;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.dispute.AlignmentDispute;
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
 * {@code POST /api/manager/reviews/{reviewId}/mark-reviewed} (E16) end-to-end through the demo-mode
 * 2.6 chain (task 5.2, §3 review lifecycle / §5 / §6 manager-mutation authz / §15 audit) against
 * real PG16 ({@link AbstractAppBootTest}). Proves: the direct manager marks reviewed (REVIEWED vs
 * REVIEWED_WITH_DISPUTES server-derived; REVIEWED_WITH_DISPUTES is never overdue while the count
 * surfaces); status never client-set; the manager-mutation authz (IC-self / non-direct-manager /
 * unrelated-manager → IDOR 404 + denial audit); the DRAFT-plan guard, oversize validation, the
 * note-body-free audit; unauth → 401.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class MarkReviewedEndpointTest extends AbstractAppBootTest {

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

  private ManagerReview saveReview(UUID planId, UUID managerId) {
    ManagerReview r = new ManagerReview();
    r.setId(UUID.randomUUID());
    r.setWeeklyPlanId(planId);
    r.setManagerEmployeeId(managerId);
    r.setStatus(ReviewStatus.NOT_REVIEWED);
    r.setReviewDueAt(Instant.parse("2026-06-04T22:00:00Z"));
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

  // --- #1 the direct manager marks reviewed, 0 disputes → REVIEWED + reviewedAt + REVIEW_MARKED
  // ---
  @Test
  void markReviewed_byDirectManager_0disputes_reviewed() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    ManagerReview review = saveReview(plan.getId(), mgr.getId());

    mvc.perform(
            post("/api/manager/reviews/" + review.getId() + "/mark-reviewed")
                .header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REVIEWED"))
        .andExpect(jsonPath("$.reviewedAt").exists())
        .andExpect(jsonPath("$.isOverdue").value(false))
        .andExpect(jsonPath("$.unresolvedDisputeCount").value(0));

    assertThat(reviews.findById(review.getId()).orElseThrow().getStatus())
        .isEqualTo(ReviewStatus.REVIEWED);
    assertThat(auditEvents.findAll().stream().anyMatch(a -> a.getAction().equals("REVIEW_MARKED")))
        .isTrue();
  }

  // --- #2 an unresolved (OPEN) dispute → REVIEWED_WITH_DISPUTES, not overdue, count surfaces ----
  @Test
  void markReviewed_withUnresolvedDispute_reviewedWithDisputes_notOverdue() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    WeeklyCommitment c = saveCommitment(plan.getId());
    saveDispute(c.getId(), mgr.getId(), DisputeStatus.OPEN);
    ManagerReview review = saveReview(plan.getId(), mgr.getId());

    mvc.perform(
            post("/api/manager/reviews/" + review.getId() + "/mark-reviewed")
                .header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REVIEWED_WITH_DISPUTES"))
        .andExpect(jsonPath("$.isOverdue").value(false))
        .andExpect(jsonPath("$.unresolvedDisputeCount").value(1));
  }

  // --- #3 the client cannot force the status — an unknown body prop is ignored, server derives
  // ----
  @Test
  void markReviewed_clientCannotForceStatus() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    ManagerReview review = saveReview(plan.getId(), mgr.getId());

    // no disputes seeded → server MUST derive REVIEWED regardless of the client-sent status
    mvc.perform(
            post("/api/manager/reviews/" + review.getId() + "/mark-reviewed")
                .header(HEADER, mgr.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"REVIEWED_WITH_DISPUTES\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REVIEWED"));
  }

  // --- #4 the owning IC cannot mark their own review → IDOR-safe 404 + a denial audit; untouched.
  // The IC PASSES the access-check (owns the plan) then FAILS the manager-capability → a GENUINE
  // authorization denial (rule #3 → one audited deny), NOT a missing-resource no-audit 404. ----
  @Test
  void markReviewed_byIcSelf_404_andAudit() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    ManagerReview review = saveReview(plan.getId(), mgr.getId());

    mvc.perform(
            post("/api/manager/reviews/" + review.getId() + "/mark-reviewed")
                .header(HEADER, ic.getId().toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").doesNotExist());
    assertThat(reviews.findById(review.getId()).orElseThrow().getStatus())
        .isEqualTo(ReviewStatus.NOT_REVIEWED); // untouched
    // genuine capability denial → exactly one safe-metadata AUTHORIZATION_DENIED audit (rule #3)
    assertThat(auditEvents.findAll()).hasSize(1);
    assertThat(auditEvents.findAll().get(0).getAction()).isEqualTo("AUTHORIZATION_DENIED");
  }

  // --- #5 a manager with no relationship to the owner → 404 + a denial audit ----
  @Test
  void markReviewed_byNonDirectManager_404_andAudit() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee directMgr = saveEmployee("direct@x.test", RoleType.MANAGER);
    Employee strangerMgr = saveEmployee("stranger@x.test", RoleType.MANAGER);
    saveActiveRelationship(directMgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    ManagerReview review = saveReview(plan.getId(), directMgr.getId());

    mvc.perform(
            post("/api/manager/reviews/" + review.getId() + "/mark-reviewed")
                .header(HEADER, strangerMgr.getId().toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").doesNotExist());
    assertThat(reviews.findById(review.getId()).orElseThrow().getStatus())
        .isEqualTo(ReviewStatus.NOT_REVIEWED);
    assertThat(auditEvents.findAll()).hasSize(1);
    assertThat(auditEvents.findAll().get(0).getAction()).isEqualTo("AUTHORIZATION_DENIED");
  }

  // --- #6 a manager of a DIFFERENT report → 404 (not this owner's direct manager) ----
  @Test
  void markReviewed_byUnrelatedManager_404() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee directMgr = saveEmployee("direct@x.test", RoleType.MANAGER);
    Employee otherIc = saveEmployee("other-ic@x.test", RoleType.IC);
    Employee otherMgr = saveEmployee("other-mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(directMgr.getId(), ic.getId());
    saveActiveRelationship(otherMgr.getId(), otherIc.getId()); // manager of someone else
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    ManagerReview review = saveReview(plan.getId(), directMgr.getId());

    mvc.perform(
            post("/api/manager/reviews/" + review.getId() + "/mark-reviewed")
                .header(HEADER, otherMgr.getId().toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").doesNotExist());
  }

  // --- #7 a review whose plan is still DRAFT (defensive guard) → 409 ILLEGAL_STATE_TRANSITION ----
  @Test
  void markReviewed_planDraft_409() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT); // artificial — no review in production
    ManagerReview review = saveReview(plan.getId(), mgr.getId());

    mvc.perform(
            post("/api/manager/reviews/" + review.getId() + "/mark-reviewed")
                .header(HEADER, mgr.getId().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));
    assertThat(reviews.findById(review.getId()).orElseThrow().getStatus())
        .isEqualTo(ReviewStatus.NOT_REVIEWED);
  }

  // --- #8 an oversize summaryNote (>4000 code points) → 400 VALIDATION_ERROR ----
  @Test
  void markReviewed_oversizeSummaryNote_400() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    ManagerReview review = saveReview(plan.getId(), mgr.getId());

    mvc.perform(
            post("/api/manager/reviews/" + review.getId() + "/mark-reviewed")
                .header(HEADER, mgr.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"summaryNote\":\"" + "x".repeat(4001) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  // --- #9 the audit carries NO summaryNote body (§15 / REQ-S-006) ----
  @Test
  void markReviewed_auditHasNoNoteBody() throws Exception {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    ManagerReview review = saveReview(plan.getId(), mgr.getId());

    mvc.perform(
            post("/api/manager/reviews/" + review.getId() + "/mark-reviewed")
                .header(HEADER, mgr.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"summaryNote\":\"SENSITIVE-REVIEW-NOTE-XYZ\"}"))
        .andExpect(status().isOk());

    var marked =
        auditEvents.findAll().stream()
            .filter(a -> a.getAction().equals("REVIEW_MARKED"))
            .findFirst()
            .orElseThrow();
    assertThat(marked.getMetadataJson()).doesNotContain("SENSITIVE-REVIEW-NOTE-XYZ");
    assertThat(marked.getSummary()).doesNotContain("SENSITIVE-REVIEW-NOTE-XYZ");
  }

  // --- #10 unauthenticated → 401 ----
  @Test
  void markReviewed_unauthenticated_401() throws Exception {
    mvc.perform(post("/api/manager/reviews/" + UUID.randomUUID() + "/mark-reviewed"))
        .andExpect(status().isUnauthorized());
  }
}
