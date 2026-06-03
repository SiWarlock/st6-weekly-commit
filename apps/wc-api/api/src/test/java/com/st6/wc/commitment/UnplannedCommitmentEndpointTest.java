package com.st6.wc.commitment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.nio.charset.StandardCharsets;
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
 * {@code POST /api/plans/{id}/unplanned-commitments} (E11) end-to-end through the demo-mode 2.6
 * chain (task 4.3, §3 / §5 / §6 rule #3 / §9 / REQ-F-025/026) against real PG16 + the V4 seed
 * ({@link AbstractAppBootTest}). Proves: owner create on a LOCKED/RECONCILING plan → 201 with
 * server-forced {@code commitmentKind=UNPLANNED} + {@code workType=UNPLANNED} (a client {@code
 * workType} is ignored); optional SO (null accepted, valid links, unknown → 400); the state guard
 * (DRAFT/RECONCILED → 409); the owner-only authz (a manager-direct-report can read but → 403, a
 * cross-IC → 404); the Appendix-E validation; the §9 {@code unplanned_count} projection upsert; and
 * <strong>REQ-F-025</strong> — adding unplanned leaves every planned-commitment baseline row
 * byte-identical.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class UnplannedCommitmentEndpointTest extends AbstractAppBootTest {

  private static final String HEADER = "X-Demo-Employee-Id";
  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);
  private static final UUID SEED_SO_1_1 = UUID.fromString("c0000000-0000-0000-0000-000000000001");

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

  private WeeklyCommitment savePlanned(UUID planId, UUID soId) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(planId);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("Planned baseline");
    c.setDescription("baseline description");
    c.setSupportingOutcomeId(soId);
    c.setPriority(Priority.P1);
    c.setWorkType(WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
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

  private void saveReview(UUID planId, UUID managerId) {
    ManagerReview r = new ManagerReview();
    r.setId(UUID.randomUUID());
    r.setWeeklyPlanId(planId);
    r.setManagerEmployeeId(managerId);
    r.setStatus(ReviewStatus.NOT_REVIEWED);
    r.setReviewDueAt(Instant.parse("2026-06-02T22:00:00Z"));
    reviews.saveAndFlush(r);
  }

  private static String q(String s) {
    return s == null ? "null" : "\"" + s + "\"";
  }

  /** E11 body — NOTE: no workType field (server-owned, always UNPLANNED). */
  private static String body(String title, String desc, String so, String prio, String conf) {
    return "{\"title\":%s,\"description\":%s,\"supportingOutcomeId\":%s,\"priority\":%s,\"confidence\":%s}"
        .formatted(q(title), q(desc), so == null ? "null" : q(so), q(prio), q(conf));
  }

  // --- #1 happy: owner, RECONCILING, no SO → 201 UNPLANNED + workType forced + projection +1 ----
  @Test
  void createUnplanned_inReconciling_forcesUnplanned_andProjects() throws Exception {
    Employee ic = saveEmployee("ada@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.RECONCILING);
    savePlanned(plan.getId(), SEED_SO_1_1);
    saveReview(plan.getId(), mgr.getId());

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/unplanned-commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Hotfix the incident", null, null, "P1", "MEDIUM")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.commitmentKind").value("UNPLANNED"))
        .andExpect(jsonPath("$.workType").value("UNPLANNED"));

    assertThat(
            commitments.findAll().stream()
                .filter(c -> c.getCommitmentKind() == CommitmentKind.UNPLANNED)
                .count())
        .isEqualTo(1);
    // §9 unplanned_count projection upsert (1 planned + 1 unplanned)
    assertThat(summaries.findAll().get(0).getUnplannedCount()).isEqualTo(1);
    assertThat(
            auditEvents.findAll().stream()
                .anyMatch(a -> a.getAction().equals("UNPLANNED_COMMITMENT_CREATED")))
        .isTrue();
  }

  // --- #2 also allowed in LOCKED ----
  @Test
  void createUnplanned_inLocked_201() throws Exception {
    Employee ic = saveEmployee("ada@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/unplanned-commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Surfaced work", null, null, "P2", "LOW")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.commitmentKind").value("UNPLANNED"));
  }

  // --- #3 a client workType is IGNORED — server forces UNPLANNED (Q1: omitted from the DTO) ----
  @Test
  void createUnplanned_clientWorkTypeIgnored_forcedUnplanned() throws Exception {
    Employee ic = saveEmployee("ada@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/unplanned-commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"x\",\"priority\":\"P1\",\"confidence\":\"MEDIUM\",\"workType\":\"STRATEGIC\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.workType").value("UNPLANNED")); // client STRATEGIC ignored
    assertThat(commitments.findAll().get(0).getWorkType()).isEqualTo(WorkType.UNPLANNED);
  }

  // --- #4 SO: valid links; unknown → 400 ----
  @Test
  void createUnplanned_soLink_validResolves_unknownRejected() throws Exception {
    Employee ic = saveEmployee("ada@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.RECONCILING);

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/unplanned-commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Linked unplanned", null, SEED_SO_1_1.toString(), "P1", "MEDIUM")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.supportingOutcomeId").value(SEED_SO_1_1.toString()));

    commitments.deleteAll();
    mvc.perform(
            post("/api/plans/" + plan.getId() + "/unplanned-commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Bad SO", null, UUID.randomUUID().toString(), "P1", "MEDIUM")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    assertThat(commitments.count()).isZero();
  }

  // --- #5 state guard: DRAFT and RECONCILED → 409 ILLEGAL_STATE_TRANSITION ----
  @Test
  void createUnplanned_onDraftOrReconciled_409() throws Exception {
    // a distinct owner per state — weekly_plan has unique(employee_id, week_start_date), so two
    // plans for the same IC+week would collide before the state guard is even exercised.
    for (PlanState state : new PlanState[] {PlanState.DRAFT, PlanState.RECONCILED}) {
      Employee ic = saveEmployee(state + "@x.test", RoleType.IC);
      WeeklyPlan plan = savePlan(ic.getId(), state);
      mvc.perform(
              post("/api/plans/" + plan.getId() + "/unplanned-commitments")
                  .header(HEADER, ic.getId().toString())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body("x", null, null, "P1", "MEDIUM")))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));
    }
    assertThat(commitments.count()).isZero();
  }

  // --- #6 authz: a cross-IC → codeless 404 (IDOR-safe) ----
  @Test
  void createUnplanned_crossIc_404() throws Exception {
    Employee owner = saveEmployee("owner@x.test", RoleType.IC);
    Employee other = saveEmployee("other@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(owner.getId(), PlanState.LOCKED);

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/unplanned-commitments")
                .header(HEADER, other.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("hijack", null, null, "P1", "MEDIUM")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").doesNotExist());
    assertThat(commitments.count()).isZero();
  }

  // --- #7 authz: a manager-direct-report can READ the locked plan but NOT author → 403 ----
  @Test
  void createUnplanned_managerDirectReport_403() throws Exception {
    Employee ic = saveEmployee("report@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/unplanned-commitments")
                .header(HEADER, mgr.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("manager-authored", null, null, "P1", "MEDIUM")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PLAN_OWNER_REQUIRED"));
    assertThat(commitments.count()).isZero();
  }

  // --- #8 validation: blank title → 400; XSS/Unicode stored raw (§16) ----
  @Test
  void createUnplanned_validation_blankTitle400_xssStoredRaw() throws Exception {
    Employee ic = saveEmployee("ada@x.test", RoleType.IC);
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/unplanned-commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("   ", null, null, "P1", "MEDIUM")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    String xss = "<img src=x onerror=alert(1)>";
    mvc.perform(
            post("/api/plans/" + plan.getId() + "/unplanned-commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content(body(xss, "🚩مرحبا", null, "P1", "MEDIUM")))
        .andExpect(status().isCreated());
    assertThat(commitments.findAll().get(0).getTitle()).isEqualTo(xss); // raw, no strip
  }

  // --- #9 unauthenticated → 401 ----
  @Test
  void createUnplanned_unauthenticated_401() throws Exception {
    mvc.perform(
            post("/api/plans/" + UUID.randomUUID() + "/unplanned-commitments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("x", null, null, "P1", "MEDIUM")))
        .andExpect(status().isUnauthorized());
  }

  // --- #10 REQ-F-025: adding unplanned leaves every planned-commitment baseline row byte-identical
  // -
  @Test
  void createUnplanned_leavesPlannedBaselineUnmutated() throws Exception {
    Employee ic = saveEmployee("ada@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    WeeklyCommitment planned = savePlanned(plan.getId(), SEED_SO_1_1);
    saveReview(plan.getId(), mgr.getId());

    // capture the planned baseline before
    WeeklyCommitment before = commitments.findById(planned.getId()).orElseThrow();
    String beforeTitle = before.getTitle();
    String beforeDescription = before.getDescription();
    UUID beforeSo = before.getSupportingOutcomeId();
    Priority beforePriority = before.getPriority();
    WorkType beforeWorkType = before.getWorkType();
    Confidence beforeConfidence = before.getConfidence();
    CommitmentKind beforeKind = before.getCommitmentKind();
    long beforeVersion = before.getVersion();

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/unplanned-commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Surfaced work", null, null, "P1", "MEDIUM")))
        .andExpect(status().isCreated());

    // the planned baseline row is byte-identical (no rewrite, no @Version bump)
    WeeklyCommitment after = commitments.findById(planned.getId()).orElseThrow();
    assertThat(after.getTitle()).isEqualTo(beforeTitle);
    assertThat(after.getDescription()).isEqualTo(beforeDescription);
    assertThat(after.getSupportingOutcomeId()).isEqualTo(beforeSo);
    assertThat(after.getPriority()).isEqualTo(beforePriority);
    assertThat(after.getWorkType()).isEqualTo(beforeWorkType);
    assertThat(after.getConfidence()).isEqualTo(beforeConfidence);
    assertThat(after.getCommitmentKind()).isEqualTo(beforeKind);
    assertThat(after.getVersion()).isEqualTo(beforeVersion); // untouched → no optimistic-lock bump
    // and the unplanned row landed + projection counted it in the same txn
    assertThat(
            commitments.findAll().stream()
                .filter(c -> c.getCommitmentKind() == CommitmentKind.UNPLANNED)
                .count())
        .isEqualTo(1);
    assertThat(summaries.findAll().get(0).getUnplannedCount()).isEqualTo(1);
  }
}
