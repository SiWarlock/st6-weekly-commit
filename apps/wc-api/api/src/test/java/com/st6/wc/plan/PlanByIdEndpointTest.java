package com.st6.wc.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.st6.wc.audit.AuditEvent;
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
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.projection.ProjectionRefresher;
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
 * {@code GET /api/plans/{id}} (E4) IDOR matrix end-to-end through the demo-mode 2.6 chain (task
 * 3.3b, §5 / <strong>§6 rule #3</strong> / Appendix B.5 / REQ-S-001/002) against real PG16 ({@link
 * AbstractAppBootTest}). Proves the per-resource authorizer ({@code authorizePlanAccess}) is the
 * chokepoint: IC-own → 200, manager-active-direct-report → 200; cross-IC / non-report-manager →
 * <strong>codeless 404 + one {@code REQUIRES_NEW} denial audit</strong>; a genuinely-missing id →
 * the <strong>same codeless 404 but NO audit</strong> (existence never revealed — only the
 * server-internal audit differs); unauthenticated → 401. E4's 404 is the codeless IDOR 404, NOT
 * E3's named {@code PLAN_NOT_FOUND}.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class PlanByIdEndpointTest extends AbstractAppBootTest {

  private static final String HEADER = "X-Demo-Employee-Id";
  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);

  @Autowired private MockMvc mvc;
  @Autowired private EmployeeRepository employees;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private WeeklyCommitmentRepository commitments;
  @Autowired private ManagerRelationshipRepository relationships;
  @Autowired private AlignmentDisputeRepository disputes;
  @Autowired private AuditEventRepository auditEvents;
  @Autowired private ManagerReviewRepository reviews;
  @Autowired private ManagerPlanSummaryRepository summaries;
  @Autowired private ProjectionRefresher refresher;

  @AfterEach
  void cleanup() {
    auditEvents.deleteAll();
    disputes.deleteAll();
    commitments.deleteAll();
    summaries.deleteAll(); // §38 FK-order: projection rows before plans/employees
    reviews.deleteAll(); // FK → weekly_plan
    plans.deleteAll();
    relationships.deleteAll();
    employees.deleteAll();
  }

  private Employee saveEmployee(RoleType role, String email) {
    Employee e = new Employee();
    e.setId(UUID.randomUUID());
    e.setEmail(email);
    e.setDisplayName("Name " + email);
    e.setRole(role);
    e.setActive(true);
    return employees.saveAndFlush(e);
  }

  private WeeklyPlan savePlan(UUID ownerId) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(ownerId);
    p.setWeekStartDate(WEEK);
    p.setWeekEndDate(WEEK.plusDays(6));
    p.setState(PlanState.DRAFT);
    return plans.saveAndFlush(p);
  }

  private void saveRelationship(UUID managerId, UUID reportId) {
    ManagerRelationship r = new ManagerRelationship();
    r.setId(UUID.randomUUID());
    r.setManagerEmployeeId(managerId);
    r.setDirectReportEmployeeId(reportId);
    r.setActive(true);
    relationships.saveAndFlush(r);
  }

  private WeeklyPlan saveReconcilingPlan(UUID ownerId) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(ownerId);
    p.setWeekStartDate(WEEK);
    p.setWeekEndDate(WEEK.plusDays(6));
    p.setState(PlanState.RECONCILING);
    return plans.saveAndFlush(p);
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

  private static final UUID SO = UUID.fromString("c0000000-0000-0000-0000-000000000001");

  private void savePlannedCommitment(UUID planId) {
    saveCommitmentReturning(planId);
  }

  private WeeklyCommitment saveCommitmentWithOutcome(UUID planId, UUID soId) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(planId);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("Ship it");
    c.setSupportingOutcomeId(soId);
    c.setPriority(Priority.P1);
    c.setWorkType(WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(AlignmentStatus.ALIGNED);
    return commitments.saveAndFlush(c);
  }

  private WeeklyCommitment saveCommitmentReturning(UUID planId) {
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

  private AlignmentDispute saveDispute(UUID commitmentId, UUID managerId, DisputeStatus status) {
    AlignmentDispute d = new AlignmentDispute();
    d.setId(UUID.randomUUID());
    d.setCommitmentId(commitmentId);
    d.setManagerEmployeeId(managerId);
    d.setStatus(status);
    d.setFlagType(FlagType.MISALIGNED);
    d.setManagerNote("please re-scope to the SO");
    return disputes.saveAndFlush(d);
  }

  // --- 5.6 REQ-F-009: an active direct manager reads a direct report's DRAFT plan → 200 with the
  // draft commitment details (title + chess fields + SO breadcrumb); REQ-F-010: managerReview null
  // +
  // NO manager affordances (formal review/dispute begin only post-lock). ----
  @Test
  void managerReadsDirectReportDraftPlan_200_withDetailsNoReviewNoManagerAffordances()
      throws Exception {
    Employee ic = saveEmployee(RoleType.IC, "ada@x.test");
    Employee mgr = saveEmployee(RoleType.MANAGER, "boss@x.test");
    saveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId()); // DRAFT
    saveCommitmentWithOutcome(plan.getId(), SO);

    mvc.perform(get("/api/plans/" + plan.getId()).header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("DRAFT"))
        // REQ-F-009: the draft commitment details are visible to the direct manager
        .andExpect(jsonPath("$.commitments[0].title").value("Ship it"))
        .andExpect(jsonPath("$.commitments[0].priority").value("P1"))
        .andExpect(jsonPath("$.commitments[0].workType").value("STRATEGIC"))
        .andExpect(jsonPath("$.commitments[0].confidence").value("MEDIUM"))
        .andExpect(jsonPath("$.commitments[0].supportingOutcomeBreadcrumb").exists())
        // REQ-F-010: no formal review before lock — managerReview null + no manager affordances
        .andExpect(jsonPath("$.managerReview").doesNotExist())
        .andExpect(jsonPath("$.commitments[0].allowedActions[?(@ == 'OPEN_DISPUTE')]").isEmpty())
        .andExpect(jsonPath("$.commitments[0].allowedActions[?(@ == 'MARK_REVIEWED')]").isEmpty());
    assertThat(auditEvents.count()).as("an authorized manager read writes no audit").isZero();
  }

  // --- 5.3b (the 9.11a unblock proof): a commitment with an OPEN dispute nests `dispute` in the
  // E4 plan read — id + managerNote + status + flagType; the nested object is the B.8 DTO, never
  // the
  // entity (no audit quartet leak). This is the field the frontend dtos.ts mirror + 9.11a consume.
  @Test
  void byId_disputedCommitment_nestsDisputeInJson() throws Exception {
    Employee ic = saveEmployee(RoleType.IC, "ada@x.test");
    Employee mgr = saveEmployee(RoleType.MANAGER, "boss@x.test");
    saveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = saveReconcilingPlan(ic.getId());
    WeeklyCommitment c = saveCommitmentReturning(plan.getId());
    AlignmentDispute open = saveDispute(c.getId(), mgr.getId(), DisputeStatus.OPEN);

    mvc.perform(get("/api/plans/" + plan.getId()).header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.commitments[0].dispute.id").value(open.getId().toString()))
        .andExpect(jsonPath("$.commitments[0].dispute.status").value("OPEN"))
        .andExpect(jsonPath("$.commitments[0].dispute.flagType").value("MISALIGNED"))
        .andExpect(
            jsonPath("$.commitments[0].dispute.managerNote").value("please re-scope to the SO"))
        // the nested dispute is the B.8 DTO, never the entity — no audit-quartet leak (FP #3)
        .andExpect(jsonPath("$.commitments[0].dispute.createdAt").doesNotExist())
        .andExpect(jsonPath("$.commitments[0].dispute.createdBy").doesNotExist())
        .andExpect(jsonPath("$.commitments[0].dispute.updatedAt").doesNotExist())
        .andExpect(jsonPath("$.commitments[0].dispute.updatedBy").doesNotExist());
  }

  // --- 5.3b: a commitment with ONLY a RESOLVED dispute (no unresolved) → `dispute` is null (the
  // real finder's unresolved bucket {OPEN,IC_RESPONDED} excludes RESOLVED) ----
  @Test
  void byId_resolvedOnlyDispute_disputeNull() throws Exception {
    Employee ic = saveEmployee(RoleType.IC, "ada@x.test");
    Employee mgr = saveEmployee(RoleType.MANAGER, "boss@x.test");
    saveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = saveReconcilingPlan(ic.getId());
    WeeklyCommitment c = saveCommitmentReturning(plan.getId());
    saveDispute(c.getId(), mgr.getId(), DisputeStatus.RESOLVED);

    mvc.perform(get("/api/plans/" + plan.getId()).header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.commitments[0].dispute").doesNotExist());
  }

  private void saveReview(UUID planId, UUID managerId, ReviewStatus status) {
    ManagerReview r = new ManagerReview();
    r.setId(UUID.randomUUID());
    r.setWeeklyPlanId(planId);
    r.setManagerEmployeeId(managerId);
    r.setStatus(status);
    r.setReviewDueAt(Instant.parse("2026-06-08T22:00:00Z"));
    reviews.saveAndFlush(r);
  }

  // === 6.8: MARK_REVIEWED plan-read affordance + real unresolvedDisputeCount ===

  // --- 6.8: the active direct manager reading a report's LOCKED plan with a NOT_REVIEWED review
  // sees MARK_REVIEWED on managerReview.allowedActions (the affordance MIRRORS E16's precondition).
  // ---
  @Test
  void planRead_directManager_seesMarkReviewedAffordance() throws Exception {
    Employee mgr = saveEmployee(RoleType.MANAGER, "boss@x.test");
    Employee ic = saveEmployee(RoleType.IC, "ada@x.test");
    saveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = saveLockedPlan(ic.getId());
    saveReview(plan.getId(), mgr.getId(), ReviewStatus.NOT_REVIEWED);

    mvc.perform(get("/api/plans/" + plan.getId()).header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.managerReview.allowedActions[?(@ == 'MARK_REVIEWED')]").exists());
  }

  // --- 6.8 (§35 leak guard, load-bearing): the IC owner viewing their OWN plan sees an EMPTY
  // managerReview.allowedActions — never MARK_REVIEWED (viewerIsDirectManager=false). ---
  @Test
  void planRead_icOwner_noMarkReviewedAffordance() throws Exception {
    Employee mgr = saveEmployee(RoleType.MANAGER, "boss@x.test");
    Employee ic = saveEmployee(RoleType.IC, "ada@x.test");
    saveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = saveLockedPlan(ic.getId());
    saveReview(plan.getId(), mgr.getId(), ReviewStatus.NOT_REVIEWED);

    mvc.perform(get("/api/plans/" + plan.getId()).header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.managerReview.allowedActions").isEmpty());
  }

  // --- 6.8 (§31 subset): a REVIEWED review → no MARK_REVIEWED (already-reviewed; the affordance
  // narrows to NOT_REVIEWED even though E16 would re-accept). ---
  @Test
  void planRead_reviewedReview_noMarkReviewedAffordance() throws Exception {
    Employee mgr = saveEmployee(RoleType.MANAGER, "boss@x.test");
    Employee ic = saveEmployee(RoleType.IC, "ada@x.test");
    saveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = saveLockedPlan(ic.getId());
    saveReview(plan.getId(), mgr.getId(), ReviewStatus.REVIEWED);

    mvc.perform(get("/api/plans/" + plan.getId()).header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.managerReview.allowedActions").isEmpty());
  }

  // --- 6.8: real unresolvedDisputeCount derived from the loaded nested disputes
  // (OPEN/IC_RESPONDED),
  // with parity against the §9 projection's unresolved_dispute_count. ---
  @Test
  void planRead_realUnresolvedDisputeCount() throws Exception {
    Employee mgr = saveEmployee(RoleType.MANAGER, "boss@x.test");
    Employee ic = saveEmployee(RoleType.IC, "ada@x.test");
    saveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = saveLockedPlan(ic.getId());
    WeeklyCommitment c1 = saveCommitmentReturning(plan.getId());
    WeeklyCommitment c2 = saveCommitmentReturning(plan.getId());
    saveCommitmentReturning(plan.getId()); // no dispute
    saveDispute(c1.getId(), mgr.getId(), DisputeStatus.OPEN);
    saveDispute(c2.getId(), mgr.getId(), DisputeStatus.IC_RESPONDED);
    saveReview(plan.getId(), mgr.getId(), ReviewStatus.NOT_REVIEWED);

    mvc.perform(get("/api/plans/" + plan.getId()).header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.managerReview.unresolvedDisputeCount").value(2));

    // parity: the §9 projection derives the same count from source (both count OPEN/IC_RESPONDED)
    refresher.recomputeForPlan(plan);
    assertThat(summaries.findAll().get(0).getUnresolvedDisputeCount()).isEqualTo(2);
  }

  // --- 6.8 regression: a DRAFT plan still has a null managerReview (no review pre-lock,
  // unchanged). ---
  @Test
  void planRead_draftPlan_nullReview_unchanged() throws Exception {
    Employee ic = saveEmployee(RoleType.IC, "ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId()); // DRAFT

    mvc.perform(get("/api/plans/" + plan.getId()).header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.managerReview").doesNotExist());
  }

  // --- 4.4b: IC reads OWN RECONCILING plan -> the nested commitment carries CARRY_FORWARD
  // (the must-have — the frontend CarryForwardButton gates on allowedActions.includes) ----
  @Test
  void byId_reconcilingOwnPlan_commitmentCarriesCarryForwardAffordance() throws Exception {
    Employee ic = saveEmployee(RoleType.IC, "ada@x.test");
    WeeklyPlan plan = saveReconcilingPlan(ic.getId());
    savePlannedCommitment(plan.getId());

    mvc.perform(get("/api/plans/" + plan.getId()).header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("RECONCILING"))
        .andExpect(jsonPath("$.commitments[0].allowedActions[?(@ == 'CARRY_FORWARD')]").exists());
  }

  // --- 4.4b: a manager-direct-report reading a report's RECONCILING plan sees NO CARRY_FORWARD
  // (it's an IC-self action) ----
  @Test
  void byId_managerReadsReconcilingReportPlan_noCarryForward() throws Exception {
    Employee mgr = saveEmployee(RoleType.MANAGER, "boss@x.test");
    Employee report = saveEmployee(RoleType.IC, "rep@x.test");
    saveRelationship(mgr.getId(), report.getId());
    WeeklyPlan plan = saveReconcilingPlan(report.getId());
    savePlannedCommitment(plan.getId());

    mvc.perform(get("/api/plans/" + plan.getId()).header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.commitments[0].allowedActions[?(@ == 'CARRY_FORWARD')]").isEmpty());
  }

  // --- 5.5b (the 9.11a activation proof): a direct manager reading a report's LOCKED plan sees
  // OPEN_DISPUTE on an undisputed commitment (the frontend "open dispute" control gates on this)
  // ----
  @Test
  void byId_managerViewsReportLockedPlan_undisputedCommitment_emitsOpenDispute() throws Exception {
    Employee ic = saveEmployee(RoleType.IC, "ada@x.test");
    Employee mgr = saveEmployee(RoleType.MANAGER, "boss@x.test");
    saveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = saveLockedPlan(ic.getId());
    savePlannedCommitment(plan.getId());

    mvc.perform(get("/api/plans/" + plan.getId()).header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.commitments[0].allowedActions[?(@ == 'OPEN_DISPUTE')]").exists())
        // a manager is not the owner → no IC-only affordances on the commitment
        .andExpect(jsonPath("$.commitments[0].allowedActions[?(@ == 'CARRY_FORWARD')]").isEmpty());
  }

  // --- 5.5b: a direct manager reading a report's plan with a DISPUTED commitment → no OPEN_DISPUTE
  // on that commitment (rule #6), and RESOLVE_DISPUTE on the nested dispute (the frontend "resolve"
  // control gates on this) ----
  @Test
  void byId_managerViewsReportPlan_disputedCommitment_nestedResolveNoOpen() throws Exception {
    Employee ic = saveEmployee(RoleType.IC, "ada@x.test");
    Employee mgr = saveEmployee(RoleType.MANAGER, "boss@x.test");
    saveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = saveLockedPlan(ic.getId());
    WeeklyCommitment c = saveCommitmentReturning(plan.getId());
    saveDispute(c.getId(), mgr.getId(), DisputeStatus.OPEN);

    mvc.perform(get("/api/plans/" + plan.getId()).header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.commitments[0].allowedActions[?(@ == 'OPEN_DISPUTE')]").isEmpty())
        .andExpect(
            jsonPath("$.commitments[0].dispute.allowedActions[?(@ == 'RESOLVE_DISPUTE')]").exists())
        // the manager is not the owner → no RESPOND on the nested dispute
        .andExpect(
            jsonPath("$.commitments[0].dispute.allowedActions[?(@ == 'RESPOND_DISPUTE')]")
                .isEmpty());
  }

  // --- 5.5b: the owning IC reading their own plan's OPEN dispute → RESPOND_DISPUTE nested; the IC
  // (viewerIsDirectManager=false) gets NO manager affordances (OPEN/RESOLVE) ----
  @Test
  void byId_icViewsOwnPlan_openDispute_emitsRespondNotManagerActions() throws Exception {
    Employee ic = saveEmployee(RoleType.IC, "ada@x.test");
    Employee mgr = saveEmployee(RoleType.MANAGER, "boss@x.test");
    saveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = saveLockedPlan(ic.getId());
    WeeklyCommitment c = saveCommitmentReturning(plan.getId());
    saveDispute(c.getId(), mgr.getId(), DisputeStatus.OPEN);

    mvc.perform(get("/api/plans/" + plan.getId()).header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.commitments[0].dispute.allowedActions[?(@ == 'RESPOND_DISPUTE')]").exists())
        .andExpect(
            jsonPath("$.commitments[0].dispute.allowedActions[?(@ == 'RESOLVE_DISPUTE')]")
                .isEmpty())
        // the IC owns the commitment but already has an unresolved dispute → no OPEN_DISPUTE either
        .andExpect(jsonPath("$.commitments[0].allowedActions[?(@ == 'OPEN_DISPUTE')]").isEmpty());
  }

  // --- #1: IC reads OWN plan -> 200 WeeklyPlanDto ----
  @Test
  void byId_icOwnPlan_200() throws Exception {
    Employee ic = saveEmployee(RoleType.IC, "ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId());

    mvc.perform(get("/api/plans/" + plan.getId()).header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(plan.getId().toString()))
        .andExpect(jsonPath("$.employeeId").value(ic.getId().toString()))
        .andExpect(jsonPath("$.allowedActions").isArray());
    assertThat(auditEvents.count()).as("authorized read writes no denial audit").isZero();
  }

  // --- #2: manager reads an ACTIVE-direct-report's plan -> 200 ----
  @Test
  void byId_managerDirectReportPlan_200() throws Exception {
    Employee manager = saveEmployee(RoleType.MANAGER, "boss@x.test");
    Employee report = saveEmployee(RoleType.IC, "rep@x.test");
    saveRelationship(manager.getId(), report.getId());
    WeeklyPlan reportPlan = savePlan(report.getId());

    mvc.perform(get("/api/plans/" + reportPlan.getId()).header(HEADER, manager.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.employeeId").value(report.getId().toString()))
        // §15: allowedActions are for the VIEWING actor — a manager is not the owner, so no LOCK
        .andExpect(jsonPath("$.allowedActions[?(@ == 'LOCK')]").isEmpty());
    assertThat(auditEvents.count()).isZero();
  }

  // --- #3: cross-IC -> codeless 404 + exactly one denial audit (actor = the requester) ----
  @Test
  void byId_crossIc_404_andDenialAudit() throws Exception {
    Employee a = saveEmployee(RoleType.IC, "a@x.test");
    Employee b = saveEmployee(RoleType.IC, "b@x.test");
    WeeklyPlan planB = savePlan(b.getId());

    mvc.perform(get("/api/plans/" + planB.getId()).header(HEADER, a.getId().toString()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").doesNotExist()); // codeless IDOR 404 (never reveal existence)

    List<AuditEvent> events = auditEvents.findAll();
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getAction()).isEqualTo("AUTHORIZATION_DENIED");
    assertThat(events.get(0).getActorEmployeeId()).isEqualTo(a.getId());
  }

  // --- #4: a manager reads a NON-direct-report's plan -> codeless 404 + denial audit ----
  @Test
  void byId_nonDirectReportManager_404_andDenialAudit() throws Exception {
    Employee manager = saveEmployee(RoleType.MANAGER, "boss@x.test");
    Employee ownReport = saveEmployee(RoleType.IC, "rep@x.test");
    saveRelationship(manager.getId(), ownReport.getId()); // makes the manager isManager=true
    Employee stranger = saveEmployee(RoleType.IC, "stranger@x.test");
    WeeklyPlan strangerPlan = savePlan(stranger.getId()); // NOT this manager's report

    mvc.perform(
            get("/api/plans/" + strangerPlan.getId()).header(HEADER, manager.getId().toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").doesNotExist());

    List<AuditEvent> events = auditEvents.findAll();
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getActorEmployeeId()).isEqualTo(manager.getId());
  }

  // --- #5: genuinely-missing id -> SAME codeless 404, but ZERO audit (not a denial) ----
  @Test
  void byId_missingId_404_noAudit() throws Exception {
    Employee a = saveEmployee(RoleType.IC, "a@x.test");

    mvc.perform(get("/api/plans/" + UUID.randomUUID()).header(HEADER, a.getId().toString()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").doesNotExist());
    assertThat(auditEvents.count())
        .as("genuinely-missing is not a denial → no audit-spam")
        .isZero();
  }

  // --- #6: unauthenticated -> 401 problem+json (composes 2.6) ----
  @Test
  void byId_unauthenticated_401() throws Exception {
    mvc.perform(get("/api/plans/" + UUID.randomUUID()))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  // --- #7: cross-owner (denied) and missing-id 404 bodies are identical modulo the per-request
  //         traceId — existence is NOT revealed by the response (only the server audit differs)
  // ----
  @Test
  void crossOwnerAndMissing_404BodiesIdenticalModuloTraceId() throws Exception {
    Employee a = saveEmployee(RoleType.IC, "a@x.test");
    Employee b = saveEmployee(RoleType.IC, "b@x.test");
    WeeklyPlan planB = savePlan(b.getId());

    String crossOwnerBody =
        mvc.perform(get("/api/plans/" + planB.getId()).header(HEADER, a.getId().toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String missingBody =
        mvc.perform(get("/api/plans/" + UUID.randomUUID()).header(HEADER, a.getId().toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    ObjectMapper om = new ObjectMapper();
    ObjectNode crossOwner = (ObjectNode) om.readTree(crossOwnerBody);
    ObjectNode missing = (ObjectNode) om.readTree(missingBody);
    // strip the request-correlation fields that legitimately vary and reveal nothing about
    // existence: `traceId` (random per response) + `instance` (the request URI = the id the CLIENT
    // already supplied). What remains — type/title/status/detail/safeMessage, code-absence — is the
    // server-determined body, which must be IDENTICAL so a prober can't distinguish denied vs
    // missing.
    crossOwner.remove("traceId");
    crossOwner.remove("instance");
    missing.remove("traceId");
    missing.remove("instance");
    assertThat(crossOwner).isEqualTo(missing);
  }
}
