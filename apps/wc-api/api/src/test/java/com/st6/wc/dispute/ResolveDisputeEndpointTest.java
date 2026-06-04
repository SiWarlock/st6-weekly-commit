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
import com.st6.wc.projection.repo.ManagerHeatmapCellRepository;
import com.st6.wc.projection.repo.ManagerPlanSummaryRepository;
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
 * {@code POST /api/disputes/{id}/resolve} (E19) end-to-end through the demo-mode 2.6 chain (task
 * 5.5, §3 {@code OPEN|IC_RESPONDED→RESOLVED} / §5 / §6 manager-capability authz / §15 audit)
 * against real PG16 + the V4 RCDO seed ({@link AbstractAppBootTest}). Proves: the active direct
 * manager resolves ({@code resolvedAt} stamped); the §33 manager-capability authz (the IC owner →
 * 403 {@code IC_CANNOT_RESOLVE_DISPUTE}, an unrelated actor / non-direct manager → 404, each
 * audited); the guarded review re-derivation ({@code REVIEWED_WITH_DISPUTES→REVIEWED} on the last
 * resolve, stays {@code WITH_DISPUTES} while another remains, and a {@code NOT_REVIEWED} review
 * STAYS {@code NOT_REVIEWED} — the correctness crux); the already-RESOLVED state guard; the
 * note-body-free audit; no projection write; a provided body is ignored (body-less E19); unauth →
 * 401.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class ResolveDisputeEndpointTest extends AbstractAppBootTest {

  private static final String HEADER = "X-Demo-Employee-Id";
  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);
  private static final UUID SO = UUID.fromString("c0000000-0000-0000-0000-000000000001");

  @Autowired private MockMvc mvc;
  @Autowired private EmployeeRepository employees;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private WeeklyCommitmentRepository commitments;
  @Autowired private ManagerRelationshipRepository relationships;
  @Autowired private ManagerReviewRepository reviews;
  @Autowired private AlignmentDisputeRepository disputes;
  @Autowired private AuditEventRepository auditEvents;
  @Autowired private ManagerPlanSummaryRepository planSummaries;
  @Autowired private ManagerHeatmapCellRepository heatmapCells;

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
    c.setSupportingOutcomeId(SO);
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
    d.setManagerNote("SENSITIVE-MGR-NOTE-XYZ");
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

  // --- #1 the direct manager resolves an OPEN dispute → 200 RESOLVED + resolvedAt + audit ----
  @Test
  void resolve_byDirectManager_openDispute_resolved() throws Exception {
    Fixture f = fixture();
    AlignmentDispute d = saveDispute(f.commitment().getId(), f.mgr().getId(), DisputeStatus.OPEN);

    mvc.perform(
            post("/api/disputes/" + d.getId() + "/resolve")
                .header(HEADER, f.mgr().getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESOLVED"))
        .andExpect(jsonPath("$.resolvedAt").exists());

    AlignmentDispute after = disputes.findById(d.getId()).orElseThrow();
    assertThat(after.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
    assertThat(after.getResolvedAt()).isNotNull();
    assertThat(
            auditEvents.findAll().stream().anyMatch(a -> a.getAction().equals("DISPUTE_RESOLVED")))
        .isTrue();
  }

  // --- #2 an IC_RESPONDED dispute is also resolvable (both unresolved pre-states) ----
  @Test
  void resolve_byDirectManager_icResponded_resolved() throws Exception {
    Fixture f = fixture();
    AlignmentDispute d =
        saveDispute(f.commitment().getId(), f.mgr().getId(), DisputeStatus.IC_RESPONDED);

    mvc.perform(
            post("/api/disputes/" + d.getId() + "/resolve")
                .header(HEADER, f.mgr().getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESOLVED"));

    assertThat(disputes.findById(d.getId()).orElseThrow().getStatus())
        .isEqualTo(DisputeStatus.RESOLVED);
  }

  // --- #3 re-derivation: resolving the LAST unresolved dispute → REVIEWED_WITH_DISPUTES→REVIEWED
  // --
  @Test
  void resolve_lastUnresolved_reDerivesToReviewed() throws Exception {
    Fixture f = fixture();
    ManagerReview review =
        saveReview(
            f.commitment().getWeeklyPlanId(), f.mgr().getId(), ReviewStatus.REVIEWED_WITH_DISPUTES);
    AlignmentDispute d = saveDispute(f.commitment().getId(), f.mgr().getId(), DisputeStatus.OPEN);

    mvc.perform(
            post("/api/disputes/" + d.getId() + "/resolve")
                .header(HEADER, f.mgr().getId().toString()))
        .andExpect(status().isOk());

    assertThat(reviews.findById(review.getId()).orElseThrow().getStatus())
        .isEqualTo(ReviewStatus.REVIEWED); // the last unresolved cleared → back to REVIEWED
  }

  // --- #4 re-derivation: another unresolved dispute remains → review stays WITH_DISPUTES ----
  @Test
  void resolve_nonLastUnresolved_staysReviewedWithDisputes() throws Exception {
    Fixture f = fixture();
    ManagerReview review =
        saveReview(
            f.commitment().getWeeklyPlanId(), f.mgr().getId(), ReviewStatus.REVIEWED_WITH_DISPUTES);
    // a second commitment carries its own still-unresolved dispute (rule #6 is per-commitment)
    WeeklyCommitment other = saveCommitment(f.commitment().getWeeklyPlanId());
    saveDispute(other.getId(), f.mgr().getId(), DisputeStatus.OPEN);
    AlignmentDispute d = saveDispute(f.commitment().getId(), f.mgr().getId(), DisputeStatus.OPEN);

    mvc.perform(
            post("/api/disputes/" + d.getId() + "/resolve")
                .header(HEADER, f.mgr().getId().toString()))
        .andExpect(status().isOk());

    assertThat(reviews.findById(review.getId()).orElseThrow().getStatus())
        .isEqualTo(ReviewStatus.REVIEWED_WITH_DISPUTES); // the other dispute keeps it WITH_DISPUTES
  }

  // --- #5 CORRECTNESS crux (Q2): a NOT_REVIEWED review STAYS NOT_REVIEWED on resolve ----
  @Test
  void resolve_onNotReviewedPlan_staysNotReviewed() throws Exception {
    Fixture f = fixture();
    ManagerReview review =
        saveReview(f.commitment().getWeeklyPlanId(), f.mgr().getId(), ReviewStatus.NOT_REVIEWED);
    AlignmentDispute d = saveDispute(f.commitment().getId(), f.mgr().getId(), DisputeStatus.OPEN);

    mvc.perform(
            post("/api/disputes/" + d.getId() + "/resolve")
                .header(HEADER, f.mgr().getId().toString()))
        .andExpect(status().isOk());

    assertThat(reviews.findById(review.getId()).orElseThrow().getStatus())
        .isEqualTo(ReviewStatus.NOT_REVIEWED); // resolving a dispute is NOT a review action
  }

  // --- #6 §33 manager-capability: the IC owner sees the dispute (plan read) but CANNOT resolve →
  // 403 IC_CANNOT_RESOLVE_DISPUTE + a denial audit (the IC legitimately uses /api/disputes via
  // respond, so existence is not hidden → 403, not 404). ----
  @Test
  void resolve_byIcOwner_403IcCannotResolve() throws Exception {
    Fixture f = fixture();
    AlignmentDispute d = saveDispute(f.commitment().getId(), f.mgr().getId(), DisputeStatus.OPEN);

    mvc.perform(
            post("/api/disputes/" + d.getId() + "/resolve")
                .header(HEADER, f.ic().getId().toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("IC_CANNOT_RESOLVE_DISPUTE"));
    assertThat(disputes.findById(d.getId()).orElseThrow().getStatus())
        .isEqualTo(DisputeStatus.OPEN); // untouched
    assertThat(auditEvents.findAll()).hasSize(1);
    assertThat(auditEvents.findAll().get(0).getAction()).isEqualTo("AUTHORIZATION_DENIED");
  }

  // --- #7 an unrelated actor (no relationship to the commitment) → 404 IDOR + audit ----
  @Test
  void resolve_byUnrelatedActor_404() throws Exception {
    Fixture f = fixture();
    Employee stranger = saveEmployee("stranger@x.test", RoleType.IC);
    AlignmentDispute d = saveDispute(f.commitment().getId(), f.mgr().getId(), DisputeStatus.OPEN);

    mvc.perform(
            post("/api/disputes/" + d.getId() + "/resolve")
                .header(HEADER, stranger.getId().toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").doesNotExist());
    assertThat(disputes.findById(d.getId()).orElseThrow().getStatus())
        .isEqualTo(DisputeStatus.OPEN);
    assertThat(auditEvents.findAll()).hasSize(1);
    assertThat(auditEvents.findAll().get(0).getAction()).isEqualTo("AUTHORIZATION_DENIED");
  }

  // --- #8 a manager who is NOT this IC's direct manager → 404 IDOR + audit ----
  @Test
  void resolve_byNonDirectManager_404() throws Exception {
    Fixture f = fixture();
    Employee otherMgr = saveEmployee("othermgr@x.test", RoleType.MANAGER);
    Employee otherReport = saveEmployee("otherreport@x.test", RoleType.IC);
    saveActiveRelationship(otherMgr.getId(), otherReport.getId()); // a manager, but not of our IC
    AlignmentDispute d = saveDispute(f.commitment().getId(), f.mgr().getId(), DisputeStatus.OPEN);

    mvc.perform(
            post("/api/disputes/" + d.getId() + "/resolve")
                .header(HEADER, otherMgr.getId().toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").doesNotExist());
    assertThat(disputes.findById(d.getId()).orElseThrow().getStatus())
        .isEqualTo(DisputeStatus.OPEN);
    assertThat(auditEvents.findAll()).hasSize(1);
    assertThat(auditEvents.findAll().get(0).getAction()).isEqualTo("AUTHORIZATION_DENIED");
  }

  // --- #9 state guard: an already-RESOLVED dispute → 409 ILLEGAL_STATE_TRANSITION ----
  @Test
  void resolve_alreadyResolved_409() throws Exception {
    Fixture f = fixture();
    AlignmentDispute d =
        saveDispute(f.commitment().getId(), f.mgr().getId(), DisputeStatus.RESOLVED);

    mvc.perform(
            post("/api/disputes/" + d.getId() + "/resolve")
                .header(HEADER, f.mgr().getId().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));
  }

  // --- #10 unknown dispute id → 404 (no audit — genuinely missing) ----
  @Test
  void resolve_unknownDisputeId_404() throws Exception {
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);

    mvc.perform(
            post("/api/disputes/" + UUID.randomUUID() + "/resolve")
                .header(HEADER, mgr.getId().toString()))
        .andExpect(status().isNotFound());
  }

  // --- #11 unauthenticated → 401 ----
  @Test
  void resolve_unauthenticated_401() throws Exception {
    mvc.perform(post("/api/disputes/" + UUID.randomUUID() + "/resolve"))
        .andExpect(status().isUnauthorized());
  }

  // --- #12 the DISPUTE_RESOLVED audit carries NO note bodies (§15 / REQ-S-006) ----
  @Test
  void resolve_auditHasNoNoteBody() throws Exception {
    Fixture f = fixture();
    AlignmentDispute d = saveDispute(f.commitment().getId(), f.mgr().getId(), DisputeStatus.OPEN);

    mvc.perform(
            post("/api/disputes/" + d.getId() + "/resolve")
                .header(HEADER, f.mgr().getId().toString()))
        .andExpect(status().isOk());

    var resolved =
        auditEvents.findAll().stream()
            .filter(a -> a.getAction().equals("DISPUTE_RESOLVED"))
            .findFirst()
            .orElseThrow();
    assertThat(resolved.getMetadataJson()).doesNotContain("SENSITIVE-MGR-NOTE-XYZ");
    assertThat(resolved.getSummary()).doesNotContain("SENSITIVE-MGR-NOTE-XYZ");
  }

  // --- #13 Q3: resolve writes NO projection rows (deferred to Phase 6) ----
  @Test
  void resolve_doesNotTouchProjection() throws Exception {
    Fixture f = fixture();
    AlignmentDispute d = saveDispute(f.commitment().getId(), f.mgr().getId(), DisputeStatus.OPEN);
    long before = planSummaries.count() + heatmapCells.count();

    mvc.perform(
            post("/api/disputes/" + d.getId() + "/resolve")
                .header(HEADER, f.mgr().getId().toString()))
        .andExpect(status().isOk());

    assertThat(planSummaries.count() + heatmapCells.count())
        .isEqualTo(before); // resolve recomputes no projection (the §9 read-model is Phase-6 work)
  }

  // --- #14 Q1: E19 is body-less — a provided body is ignored (no resolutionNote persisted) ----
  @Test
  void resolve_ignoresProvidedBody_resolved() throws Exception {
    Fixture f = fixture();
    AlignmentDispute d = saveDispute(f.commitment().getId(), f.mgr().getId(), DisputeStatus.OPEN);

    mvc.perform(
            post("/api/disputes/" + d.getId() + "/resolve")
                .header(HEADER, f.mgr().getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolutionNote\":\"please ignore me\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESOLVED"));
  }
}
