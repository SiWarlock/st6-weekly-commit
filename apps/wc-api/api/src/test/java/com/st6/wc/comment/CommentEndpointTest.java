package com.st6.wc.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.st6.wc.audit.repo.AuditEventRepository;
import com.st6.wc.comment.repo.CommentRepository;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.CommentTargetType;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.WorkType;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.support.AbstractAppBootTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * {@code GET /api/comments} (E20) + {@code POST /api/comments} (E21) end-to-end through the
 * demo-mode 2.6 chain (comments backend, §5/§11 / §6 rule #3 / §15/§16 / REQ-F-014) against real
 * PG16 + the V4 seed ({@link AbstractAppBootTest}). The FIRST production caller of {@code
 * authorizeCommentTargetAccess}. Proves: paginated B.20 envelope ordered {@code createdAt ASC, id
 * ASC}; flat create (201, depth 0, parent null, createdAt populated); body validation; no entity
 * leak; rule-#3 IDOR (unseeable → codeless 404; cross-owner create → 404 + denial audit;
 * chokepoint); the REQ-F-014 manager-LOCKED+ gate (manager-on-report-DRAFT → 409 no-audit, on
 * LOCKED → 201; the IC owner comments in any state); rule-#2 baseline non-interference; the safe
 * (body-free) create audit.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class CommentEndpointTest extends AbstractAppBootTest {

  private static final String HEADER = "X-Demo-Employee-Id";
  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);

  @Autowired private MockMvc mvc;
  @Autowired private EmployeeRepository employees;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private WeeklyCommitmentRepository commitments;
  @Autowired private CommentRepository comments;
  @Autowired private AuditEventRepository auditEvents;
  @Autowired private ManagerRelationshipRepository relationships;

  // Two RequestMappingHandlerMapping beans exist (the MVC one + the actuator
  // controllerEndpointHandlerMapping) — qualify the MVC one for the reachability assertion.
  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  @AfterEach
  void cleanup() {
    comments.deleteAll();
    auditEvents.deleteAll();
    commitments.deleteAll();
    relationships.deleteAll();
    plans.deleteAll();
    employees.deleteAll();
  }

  // ===== helpers (mirror CommitmentCreateEndpointTest) =====

  private Employee saveIc(String email) {
    return saveEmployee(email, RoleType.IC);
  }

  private Employee saveManager(String email) {
    return saveEmployee(email, RoleType.MANAGER);
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

  private void saveActiveRelationship(UUID managerId, UUID reportId) {
    ManagerRelationship r = new ManagerRelationship();
    r.setId(UUID.randomUUID());
    r.setManagerEmployeeId(managerId);
    r.setDirectReportEmployeeId(reportId);
    r.setActive(true);
    relationships.saveAndFlush(r);
  }

  private WeeklyCommitment saveCommitment(UUID planId) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(planId);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("baseline title");
    c.setPriority(Priority.P1);
    c.setWorkType(WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(AlignmentStatus.ALIGNED);
    return commitments.saveAndFlush(c);
  }

  private Comment saveComment(UUID targetId, UUID authorId, String body, Instant createdAt) {
    Comment c = new Comment();
    c.setId(UUID.randomUUID());
    c.setTargetType(CommentTargetType.PLAN);
    c.setTargetId(targetId);
    c.setAuthorEmployeeId(authorId);
    c.setDepth(0);
    c.setBody(body);
    c.setCreatedAt(createdAt);
    return comments.saveAndFlush(c);
  }

  private static String createBody(String targetType, UUID targetId, String body) {
    return "{\"targetType\":\"%s\",\"targetId\":\"%s\",\"body\":\"%s\"}"
        .formatted(targetType, targetId, body.replace("\\", "\\\\").replace("\"", "\\\""));
  }

  // ===== E20 list =====

  // --- #1: list returns a B.20 envelope ordered createdAt ASC, id ASC ----------------------------
  @Test
  void list_returnsPagedCommentsForTarget_orderedByCreatedAtThenId() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    saveComment(plan.getId(), ic.getId(), "third", Instant.parse("2026-06-04T03:00:00Z"));
    saveComment(plan.getId(), ic.getId(), "first", Instant.parse("2026-06-04T01:00:00Z"));
    saveComment(plan.getId(), ic.getId(), "second", Instant.parse("2026-06-04T02:00:00Z"));

    mvc.perform(
            get("/api/comments")
                .header(HEADER, ic.getId().toString())
                .param("targetType", "PLAN")
                .param("targetId", plan.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3))
        .andExpect(jsonPath("$.content[0].body").value("first"))
        .andExpect(jsonPath("$.content[1].body").value("second"))
        .andExpect(jsonPath("$.content[2].body").value("third"))
        .andExpect(jsonPath("$.content[0].authorDisplayName").value("Name ada@x.test"))
        .andExpect(jsonPath("$.page.totalElements").value(3))
        .andExpect(jsonPath("$.sort[0].property").value("createdAt"))
        .andExpect(jsonPath("$.sort[0].direction").value("ASC"));
  }

  // --- #6: the DTO never leaks entity internals (audit quartet / path) ---------------------------
  @Test
  void commentDto_noEntityLeak() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    saveComment(plan.getId(), ic.getId(), "visible", Instant.parse("2026-06-04T01:00:00Z"));

    mvc.perform(
            get("/api/comments")
                .header(HEADER, ic.getId().toString())
                .param("targetType", "PLAN")
                .param("targetId", plan.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").exists())
        .andExpect(jsonPath("$.content[0].createdBy").doesNotExist())
        .andExpect(jsonPath("$.content[0].updatedBy").doesNotExist())
        .andExpect(jsonPath("$.content[0].updatedAt").doesNotExist())
        .andExpect(jsonPath("$.content[0].path").doesNotExist());
  }

  // --- #7: list on an unseeable / nonexistent target → codeless 404 (rule #3, no body leak) ------
  @Test
  void list_unseeableTarget_404_codeless_noBodyLeak() throws Exception {
    Employee ada = saveIc("ada@x.test");
    Employee eve = saveIc("eve@x.test");
    WeeklyPlan evePlan = savePlan(eve.getId(), PlanState.LOCKED); // owned by eve

    // ada (no relationship) lists comments on eve's plan → IDOR-safe 404, no named code
    mvc.perform(
            get("/api/comments")
                .header(HEADER, ada.getId().toString())
                .param("targetType", "PLAN")
                .param("targetId", evePlan.getId().toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").doesNotExist());

    // a genuinely nonexistent target id → 404 as well
    mvc.perform(
            get("/api/comments")
                .header(HEADER, ada.getId().toString())
                .param("targetType", "PLAN")
                .param("targetId", UUID.randomUUID().toString()))
        .andExpect(status().isNotFound());
  }

  // --- #12 (list leg): the owning IC may list their own target's comments, any state ------------
  @Test
  void list_icOwnTarget_succeeds() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT); // own DRAFT — owner read is fine
    saveComment(plan.getId(), ic.getId(), "mine", Instant.parse("2026-06-04T01:00:00Z"));

    mvc.perform(
            get("/api/comments")
                .header(HEADER, ic.getId().toString())
                .param("targetType", "PLAN")
                .param("targetId", plan.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1));
  }

  // ===== E21 create =====

  // --- #3: create persists a FLAT comment (depth 0, parent null), createdAt populated, 201 -------
  @Test
  void create_persistsFlatComment_returns201() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);

    mvc.perform(
            post("/api/comments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("PLAN", plan.getId(), "looks good")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.targetType").value("PLAN"))
        .andExpect(jsonPath("$.targetId").value(plan.getId().toString()))
        .andExpect(jsonPath("$.authorEmployeeId").value(ic.getId().toString()))
        .andExpect(jsonPath("$.authorDisplayName").value("Name ada@x.test"))
        .andExpect(jsonPath("$.depth").value(0))
        .andExpect(jsonPath("$.body").value("looks good"))
        .andExpect(jsonPath("$.createdAt").isNotEmpty());

    assertThat(comments.findAll()).hasSize(1);
    Comment saved = comments.findAll().get(0);
    assertThat(saved.getDepth()).isZero();
    assertThat(saved.getParentCommentId()).isNull();
    assertThat(saved.getCreatedAt()).isNotNull();
  }

  // --- #12 (create leg): the owning IC may comment on their own DRAFT target (gate skipped) ------
  @Test
  void create_icOwnTarget_succeeds() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);

    mvc.perform(
            post("/api/comments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("PLAN", plan.getId(), "my own draft note")))
        .andExpect(status().isCreated());

    assertThat(comments.findAll()).hasSize(1);
  }

  // --- #5: body validation — blank and oversize → 400 VALIDATION_ERROR + fieldErrors ------------
  @Test
  void create_validatesBody_blankAndOversize_400() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);

    mvc.perform(
            post("/api/comments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("PLAN", plan.getId(), "   ")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors").exists());

    mvc.perform(
            post("/api/comments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("PLAN", plan.getId(), "x".repeat(4001))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    assertThat(comments.findAll()).isEmpty();
  }

  // --- #8: create on an unauthorized target → 404 + exactly one AUTHORIZATION_DENIED audit -------
  @Test
  void create_unauthorizedTarget_404_auditedDenial() throws Exception {
    Employee ada = saveIc("ada@x.test");
    Employee eve = saveIc("eve@x.test");
    WeeklyPlan evePlan = savePlan(eve.getId(), PlanState.LOCKED);

    mvc.perform(
            post("/api/comments")
                .header(HEADER, ada.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("PLAN", evePlan.getId(), "sneaky")))
        .andExpect(status().isNotFound());

    assertThat(comments.findAll()).isEmpty();
    assertThat(auditEvents.findAll()).hasSize(1);
    assertThat(auditEvents.findAll().get(0).getAction()).isEqualTo("AUTHORIZATION_DENIED");
  }

  // --- #10: REQ-F-014 — a manager commenting on a direct-report DRAFT target → 409, NO audit -----
  @Test
  void create_managerOnDirectReportDraft_rejected_REQ_F_014() throws Exception {
    Employee ic = saveIc("ada@x.test");
    Employee mgr = saveManager("boss@x.test");
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan draft = savePlan(ic.getId(), PlanState.DRAFT);

    mvc.perform(
            post("/api/comments")
                .header(HEADER, mgr.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("PLAN", draft.getId(), "manager on a draft")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));

    assertThat(comments.findAll()).isEmpty();
    // 5.6 posture: a legitimate manager relationship, just a state precondition → 409, NO audit.
    assertThat(auditEvents.findAll()).isEmpty();
  }

  // --- #11: the gate admits a manager once the report's plan is LOCKED+ → 201 --------------------
  @Test
  void create_managerOnDirectReportLocked_succeeds() throws Exception {
    Employee ic = saveIc("ada@x.test");
    Employee mgr = saveManager("boss@x.test");
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan locked = savePlan(ic.getId(), PlanState.LOCKED);

    mvc.perform(
            post("/api/comments")
                .header(HEADER, mgr.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("PLAN", locked.getId(), "manager comment post-lock")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.authorEmployeeId").value(mgr.getId().toString()));

    assertThat(comments.findAll()).hasSize(1);
  }

  // --- #13: commenting touches NO commitment baseline field (rule #2 non-interference) -----------
  @Test
  void comment_doesNotTouchBaseline() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    WeeklyCommitment c = saveCommitment(plan.getId());
    String title0 = c.getTitle();
    Priority priority0 = c.getPriority();
    WorkType work0 = c.getWorkType();
    Confidence conf0 = c.getConfidence();

    mvc.perform(
            post("/api/comments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("COMMITMENT", c.getId(), "a note on this commitment")))
        .andExpect(status().isCreated());

    WeeklyCommitment reloaded = commitments.findById(c.getId()).orElseThrow();
    assertThat(reloaded.getTitle()).isEqualTo(title0);
    assertThat(reloaded.getPriority()).isEqualTo(priority0);
    assertThat(reloaded.getWorkType()).isEqualTo(work0);
    assertThat(reloaded.getConfidence()).isEqualTo(conf0);
  }

  // --- #14: the create audit carries ids only — no body text (§15 SENTINEL) ----------------------
  @Test
  void create_writesSafeAudit_noBody() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);
    String secret = "SENTINEL-BODY-TEXT-do-not-leak";

    mvc.perform(
            post("/api/comments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("PLAN", plan.getId(), secret)))
        .andExpect(status().isCreated());

    assertThat(auditEvents.findAll()).hasSize(1);
    var audit = auditEvents.findAll().get(0);
    assertThat(audit.getAction()).isEqualTo("COMMENT_CREATED");
    assertThat(audit.getMetadataJson()).doesNotContain(secret);
    assertThat(audit.getSummary()).doesNotContain(secret);
  }

  // --- ADD: REQ-F-014 gate applies to the E20 LIST path too — a manager listing a direct-report's
  // DRAFT-plan comments (the IC's pre-lock private notes) → 409, no audit (the §11
  // confidentiality).
  @Test
  void list_managerOnDirectReportDraft_rejected_REQ_F_014() throws Exception {
    Employee ic = saveIc("ada@x.test");
    Employee mgr = saveManager("boss@x.test");
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan draft = savePlan(ic.getId(), PlanState.DRAFT);
    saveComment(
        draft.getId(), ic.getId(), "ic private note", Instant.parse("2026-06-04T01:00:00Z"));

    mvc.perform(
            get("/api/comments")
                .header(HEADER, mgr.getId().toString())
                .param("targetType", "PLAN")
                .param("targetId", draft.getId().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));

    assertThat(auditEvents.findAll()).isEmpty(); // no-audit-on-DRAFT-409, uniform with create
  }

  // --- ADD: the list gate admits the manager once the report's plan is LOCKED+ → 200 -------------
  @Test
  void list_managerOnDirectReportLocked_succeeds() throws Exception {
    Employee ic = saveIc("ada@x.test");
    Employee mgr = saveManager("boss@x.test");
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan locked = savePlan(ic.getId(), PlanState.LOCKED);
    saveComment(
        locked.getId(), ic.getId(), "post-lock note", Instant.parse("2026-06-04T01:00:00Z"));

    mvc.perform(
            get("/api/comments")
                .header(HEADER, mgr.getId().toString())
                .param("targetType", "PLAN")
                .param("targetId", locked.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1));
  }

  // --- ADD (orch note 1): the gate resolves plan-state via the commitment→plan hop too -----------
  @Test
  void create_managerOnDirectReportDraftCommitment_rejected_REQ_F_014() throws Exception {
    Employee ic = saveIc("ada@x.test");
    Employee mgr = saveManager("boss@x.test");
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan draft = savePlan(ic.getId(), PlanState.DRAFT);
    WeeklyCommitment c = saveCommitment(draft.getId()); // a commitment under a DRAFT plan

    mvc.perform(
            post("/api/comments")
                .header(HEADER, mgr.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("COMMITMENT", c.getId(), "manager on a draft-plan commitment")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));

    assertThat(comments.findAll()).isEmpty();
    assertThat(auditEvents.findAll()).isEmpty();
  }

  // --- ADD (orch note 2): an invalid targetType query param → 400 (enum bind failure) ------------
  @Test
  void list_invalidTargetType_400() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.LOCKED);

    mvc.perform(
            get("/api/comments")
                .header(HEADER, ic.getId().toString())
                .param("targetType", "BOGUS")
                .param("targetId", plan.getId().toString()))
        .andExpect(status().isBadRequest());
  }

  // --- #15: both /api/comments mappings are request-reachable (registered in the handler map) ----
  @Test
  void commentEndpoints_requestReachable() {
    boolean getMapped =
        handlerMapping.getHandlerMethods().keySet().stream()
            .anyMatch(
                info ->
                    info.getPathPatternsCondition() != null
                        && info.getPathPatternsCondition()
                            .getPatternValues()
                            .contains("/api/comments")
                        && info.getMethodsCondition().getMethods().contains(RequestMethod.GET));
    boolean postMapped =
        handlerMapping.getHandlerMethods().keySet().stream()
            .anyMatch(
                info ->
                    info.getPathPatternsCondition() != null
                        && info.getPathPatternsCondition()
                            .getPatternValues()
                            .contains("/api/comments")
                        && info.getMethodsCondition().getMethods().contains(RequestMethod.POST));
    assertThat(getMapped).as("GET /api/comments mapped").isTrue();
    assertThat(postMapped).as("POST /api/comments mapped").isTrue();
  }
}
