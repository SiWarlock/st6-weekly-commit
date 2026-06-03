package com.st6.wc.commitment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.st6.wc.audit.repo.AuditEventRepository;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.CommitmentKind;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.RoleType;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.support.AbstractAppBootTest;
import java.nio.charset.StandardCharsets;
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
 * {@code POST /api/plans/{id}/commitments} (E5) end-to-end through the demo-mode 2.6 chain (task
 * 3.4a, §5 / §6 rule #3 / §16 / Appendix E Part 1) against real PG16 + the V4 seed ({@link
 * AbstractAppBootTest}). Proves: create on own DRAFT plan → 201 PLANNED commitment; the Appendix-E
 * validation suite (code-point caps, normalize-once, enum→400-not-500, blank→NULL); XSS/Unicode
 * stored RAW (§16); planned-only (reject {@code UNPLANNED}); parent-plan authz (cross-owner →
 * codeless 404+audit); non-DRAFT → 409; unauth → 401.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class CommitmentCreateEndpointTest extends AbstractAppBootTest {

  private static final String HEADER = "X-Demo-Employee-Id";
  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);
  private static final UUID SEED_SO_1_1 = UUID.fromString("c0000000-0000-0000-0000-000000000001");

  @Autowired private MockMvc mvc;
  @Autowired private EmployeeRepository employees;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private WeeklyCommitmentRepository commitments;
  @Autowired private AuditEventRepository auditEvents;
  @Autowired private ManagerRelationshipRepository relationships;

  @AfterEach
  void cleanup() {
    auditEvents.deleteAll();
    commitments.deleteAll();
    relationships.deleteAll();
    plans.deleteAll();
    employees.deleteAll();
  }

  private Employee saveIc(String email) {
    Employee e = new Employee();
    e.setId(UUID.randomUUID());
    e.setEmail(email);
    e.setDisplayName("Name " + email);
    e.setRole(RoleType.IC);
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

  private Employee saveManager(String email) {
    Employee e = new Employee();
    e.setId(UUID.randomUUID());
    e.setEmail(email);
    e.setDisplayName("Name " + email);
    e.setRole(RoleType.MANAGER);
    e.setActive(true);
    return employees.saveAndFlush(e);
  }

  private void saveActiveRelationship(UUID managerId, UUID reportId) {
    ManagerRelationship r = new ManagerRelationship();
    r.setId(UUID.randomUUID());
    r.setManagerEmployeeId(managerId);
    r.setDirectReportEmployeeId(reportId);
    r.setActive(true);
    relationships.saveAndFlush(r);
  }

  /** Build an E5 request body from pre-formatted JSON value fragments (so bad enums/nulls work). */
  private static String body(
      String title, String desc, String so, String prio, String work, String conf, String align) {
    return "{\"title\":%s,\"description\":%s,\"supportingOutcomeId\":%s,\"priority\":%s,\"workType\":%s,\"confidence\":%s,\"alignmentStatus\":%s}"
        .formatted(title, desc, so, prio, work, conf, align);
  }

  private static String q(String s) {
    return s == null ? "null" : "\"" + s + "\"";
  }

  // --- #1: IC owner, DRAFT plan, valid request -> 201, PLANNED commitment persisted ----
  @Test
  void create_draftOwner_planned_201() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        q("Ship it"),
                        q("do the thing"),
                        "null",
                        q("P1"),
                        q("STRATEGIC"),
                        q("MEDIUM"),
                        q("ALIGNED"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.commitmentKind").value("PLANNED"))
        .andExpect(jsonPath("$.title").value("Ship it"))
        .andExpect(jsonPath("$.weeklyPlanId").value(plan.getId().toString()));

    assertThat(commitments.findAll()).hasSize(1);
    assertThat(commitments.findAll().get(0).getCommitmentKind()).isEqualTo(CommitmentKind.PLANNED);
  }

  // --- #1b: E5 create is OWNER-ONLY (§6) — a manager-direct-report who can READ the report's DRAFT
  // plan must NOT author on it → 403 PLAN_OWNER_REQUIRED + a denial audit; no commitment persisted.
  // The regression pin for the handoff-005 Finding (the create was on the read authorizer). ----
  @Test
  void create_managerDirectReport_403PlanOwnerRequired() throws Exception {
    Employee ic = saveIc("ada@x.test");
    Employee mgr = saveManager("boss@x.test");
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/commitments")
                .header(HEADER, mgr.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        q("manager-authored"),
                        q("nope"),
                        "null",
                        q("P1"),
                        q("STRATEGIC"),
                        q("MEDIUM"),
                        q("ALIGNED"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PLAN_OWNER_REQUIRED"));

    assertThat(commitments.findAll()).isEmpty(); // never authored on the report's plan
    // exactly one safe-metadata denial audit (§15), consistent with the other mutation denials
    assertThat(auditEvents.findAll()).hasSize(1);
    assertThat(auditEvents.findAll().get(0).getAction()).isEqualTo("AUTHORIZATION_DENIED");
  }

  // --- #2: workType=UNPLANNED → 400 VALIDATION_ERROR (planned-only endpoint) ----
  @Test
  void create_unplannedWorkType_400() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        q("X"),
                        "null",
                        "null",
                        q("P1"),
                        q("UNPLANNED"),
                        q("MEDIUM"),
                        q("ALIGNED"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    assertThat(commitments.count()).isZero();
  }

  // --- #3: title — blank → 400, 256 cp → 400, 255 ASTRAL cp → accepted + normalized on store ----
  @Test
  void create_titleValidation_codePointAndNormalize() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);

    // blank (whitespace-only) → @NotBlank → 400
    mvc.perform(
            post("/api/plans/" + plan.getId() + "/commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        q("   "),
                        "null",
                        "null",
                        q("P1"),
                        q("STRATEGIC"),
                        q("MEDIUM"),
                        q("ALIGNED"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    // 256 code points → 400
    mvc.perform(
            post("/api/plans/" + plan.getId() + "/commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        q("a".repeat(256)),
                        "null",
                        "null",
                        q("P1"),
                        q("STRATEGIC"),
                        q("MEDIUM"),
                        q("ALIGNED"))))
        .andExpect(status().isBadRequest());

    // 255 ASTRAL code points (510 UTF-16 units) → accepted (code-point, not UTF-16, counting)
    String astral255 = "🚀".repeat(255);
    mvc.perform(
            post("/api/plans/" + plan.getId() + "/commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content(
                    body(
                        q(astral255),
                        "null",
                        "null",
                        q("P1"),
                        q("STRATEGIC"),
                        q("MEDIUM"),
                        q("ALIGNED"))))
        .andExpect(status().isCreated());
    assertThat(commitments.findAll().get(0).getTitle()).isEqualTo(astral255); // stored verbatim

    commitments.deleteAll();
    // normalize: lead/trail strip + internal whitespace-run collapse to single space
    mvc.perform(
            post("/api/plans/" + plan.getId() + "/commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        q("  hello   world  "),
                        "null",
                        "null",
                        q("P1"),
                        q("STRATEGIC"),
                        q("MEDIUM"),
                        q("ALIGNED"))))
        .andExpect(status().isCreated());
    assertThat(commitments.findAll().get(0).getTitle()).isEqualTo("hello world");
  }

  // --- #4: description — whitespace-only → NULL, 4001 cp → 400, newlines preserved ----
  @Test
  void create_descriptionBlankToNull_and4000Cap() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);

    // whitespace-only description → stored NULL
    mvc.perform(
            post("/api/plans/" + plan.getId() + "/commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        q("T"),
                        q("   "),
                        "null",
                        q("P1"),
                        q("STRATEGIC"),
                        q("MEDIUM"),
                        q("ALIGNED"))))
        .andExpect(status().isCreated());
    assertThat(commitments.findAll().get(0).getDescription()).isNull();

    commitments.deleteAll();
    // 4001 code points → 400
    mvc.perform(
            post("/api/plans/" + plan.getId() + "/commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        q("T"),
                        q("d".repeat(4001)),
                        "null",
                        q("P1"),
                        q("STRATEGIC"),
                        q("MEDIUM"),
                        q("ALIGNED"))))
        .andExpect(status().isBadRequest());

    commitments.deleteAll();
    // internal newlines preserved
    mvc.perform(
            post("/api/plans/" + plan.getId() + "/commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        q("T"),
                        q("line1\\nline2"),
                        "null",
                        q("P1"),
                        q("STRATEGIC"),
                        q("MEDIUM"),
                        q("ALIGNED"))))
        .andExpect(status().isCreated());
    assertThat(commitments.findAll().get(0).getDescription()).isEqualTo("line1\nline2");
  }

  // --- #5: unknown enum → 400 VALIDATION_ERROR (never 500) ----
  @Test
  void create_unknownEnum_400_not500() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        q("T"),
                        "null",
                        "null",
                        q("NOPE"),
                        q("STRATEGIC"),
                        q("MEDIUM"),
                        q("ALIGNED"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  // --- #6: XSS + Unicode stored VERBATIM (no server HTML strip, §16) ----
  @Test
  void create_xssAndUnicode_storedVerbatim() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);
    String xssTitle = "<img src=x onerror=alert(1)>";
    String unicodeDesc = "🚩مرحبا";

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content(
                    body(
                        q(xssTitle),
                        q(unicodeDesc),
                        "null",
                        q("P1"),
                        q("STRATEGIC"),
                        q("MEDIUM"),
                        q("ALIGNED"))))
        .andExpect(status().isCreated());

    assertThat(commitments.findAll().get(0).getTitle()).isEqualTo(xssTitle); // raw, no strip
    assertThat(commitments.findAll().get(0).getDescription()).isEqualTo(unicodeDesc);
  }

  // --- #7: supportingOutcomeId — valid links; unknown → 400 ----
  @Test
  void create_soLink_validResolves_unknownRejected() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);

    // valid seeded SO → linked
    mvc.perform(
            post("/api/plans/" + plan.getId() + "/commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        q("T"),
                        "null",
                        q(SEED_SO_1_1.toString()),
                        q("P1"),
                        q("STRATEGIC"),
                        q("MEDIUM"),
                        q("ALIGNED"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.supportingOutcomeId").value(SEED_SO_1_1.toString()));

    commitments.deleteAll();
    // unknown SO id → 400 VALIDATION_ERROR
    mvc.perform(
            post("/api/plans/" + plan.getId() + "/commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        q("T"),
                        "null",
                        q(UUID.randomUUID().toString()),
                        q("P1"),
                        q("STRATEGIC"),
                        q("MEDIUM"),
                        q("ALIGNED"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    assertThat(commitments.count()).isZero();
  }

  // --- #8: cross-owner (IC creates on another IC's plan) → codeless 404 + denial audit ----
  @Test
  void create_crossOwner_404_andAudit() throws Exception {
    Employee a = saveIc("a@x.test");
    Employee b = saveIc("b@x.test");
    WeeklyPlan planB = savePlan(b.getId(), PlanState.DRAFT);

    mvc.perform(
            post("/api/plans/" + planB.getId() + "/commitments")
                .header(HEADER, a.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        q("T"),
                        "null",
                        "null",
                        q("P1"),
                        q("STRATEGIC"),
                        q("MEDIUM"),
                        q("ALIGNED"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").doesNotExist()); // codeless IDOR 404
    assertThat(auditEvents.findAll()).hasSize(1);
    assertThat(auditEvents.findAll().get(0).getActorEmployeeId()).isEqualTo(a.getId());
    assertThat(commitments.count()).isZero();
  }

  // --- #9: plan not DRAFT → 409 ILLEGAL_STATE_TRANSITION ----
  @Test
  void create_nonDraftPlan_409() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan locked = savePlan(ic.getId(), PlanState.LOCKED);

    mvc.perform(
            post("/api/plans/" + locked.getId() + "/commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        q("T"),
                        "null",
                        "null",
                        q("P1"),
                        q("STRATEGIC"),
                        q("MEDIUM"),
                        q("ALIGNED"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));
    assertThat(commitments.count()).isZero();
  }

  // --- #10: unauthenticated → 401 ----
  @Test
  void create_unauthenticated_401() throws Exception {
    mvc.perform(
            post("/api/plans/" + UUID.randomUUID() + "/commitments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        q("T"),
                        "null",
                        "null",
                        q("P1"),
                        q("STRATEGIC"),
                        q("MEDIUM"),
                        q("ALIGNED"))))
        .andExpect(status().isUnauthorized());
  }

  // --- #11: title with a (JSON-escaped) C0 control char → 400 VALIDATION_ERROR (@NoControlChars)
  // ----
  @Test
  void create_titleWithControlChar_400() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);
    // "\\u0001" is the 6-char JSON escape in the body → Jackson decodes to a real U+0001 control
    // char in the title → survives whitespace-collapse → @NoControlChars rejects (single-line, §16)
    String bodyWithControlTitle =
        "{\"title\":\"a\\u0001b\",\"description\":null,\"supportingOutcomeId\":null,"
            + "\"priority\":\"P1\",\"workType\":\"STRATEGIC\",\"confidence\":\"MEDIUM\",\"alignmentStatus\":\"ALIGNED\"}";

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyWithControlTitle))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    assertThat(commitments.count()).isZero();
  }

  // --- #12: malformed JSON → 400 VALIDATION_ERROR (never 500; no untrusted echo) ----
  @Test
  void create_malformedJson_400() throws Exception {
    Employee ic = saveIc("ada@x.test");
    WeeklyPlan plan = savePlan(ic.getId(), PlanState.DRAFT);

    mvc.perform(
            post("/api/plans/" + plan.getId() + "/commitments")
                .header(HEADER, ic.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ not valid json "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }
}
