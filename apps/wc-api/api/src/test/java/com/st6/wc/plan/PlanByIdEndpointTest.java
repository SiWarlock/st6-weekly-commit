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
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.WorkType;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.support.AbstractAppBootTest;
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
  @Autowired private AuditEventRepository auditEvents;

  @AfterEach
  void cleanup() {
    auditEvents.deleteAll();
    commitments.deleteAll();
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

  private void savePlannedCommitment(UUID planId) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(planId);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("Ship it");
    c.setPriority(Priority.P1);
    c.setWorkType(WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(AlignmentStatus.ALIGNED);
    commitments.saveAndFlush(c);
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
