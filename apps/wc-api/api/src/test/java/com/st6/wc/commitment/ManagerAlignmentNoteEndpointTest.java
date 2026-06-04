package com.st6.wc.commitment;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.st6.wc.enums.RoleType;
import com.st6.wc.enums.WorkType;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.support.AbstractAppBootTest;
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
 * {@code PATCH /api/commitments/{id}} {@code managerAlignmentNote} write (E6, task 5.7) end-to-end
 * through the demo-mode 2.6 chain against real PG16 ({@link AbstractAppBootTest}). Proves the one
 * manager-owned, post-lock-mutable commitment field: the active direct manager sets it
 * (LOCKED/RECONCILING, audited); the §33 manager-capability field-level authz (the IC-owner → 403
 * {@code IC_CANNOT_WRITE_MANAGER_NOTE}, an unrelated / non-direct actor → 404 IDOR, each audited);
 * the mixing rejection (managerAlignmentNote + an IC field → 400 — single-actor-per-patch); the
 * DRAFT-state 409; the note-body-free audit; present-null clears; and the IC path is unaffected (no
 * regression).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class ManagerAlignmentNoteEndpointTest extends AbstractAppBootTest {

  private static final String HEADER = "X-Demo-Employee-Id";
  private static final LocalDate WEEK = LocalDate.of(2026, 6, 1);
  private static final UUID SO = UUID.fromString("c0000000-0000-0000-0000-000000000001");

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
    c.setTitle("Original title");
    c.setDescription("original description");
    c.setSupportingOutcomeId(SO);
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

  private record Fixture(Employee ic, Employee mgr, WeeklyCommitment commitment) {}

  private Fixture fixture(PlanState state) {
    Employee ic = saveEmployee("ic@x.test", RoleType.IC);
    Employee mgr = saveEmployee("mgr@x.test", RoleType.MANAGER);
    saveActiveRelationship(mgr.getId(), ic.getId());
    WeeklyPlan plan = savePlan(ic.getId(), state);
    return new Fixture(ic, mgr, saveCommitment(plan.getId()));
  }

  private String url(UUID id) {
    return "/api/commitments/" + id;
  }

  // --- #1 the direct manager sets managerAlignmentNote on a LOCKED plan → 200 + audit + DB ----
  @Test
  void managerNote_byDirectManager_lockedPlan_200() throws Exception {
    Fixture f = fixture(PlanState.LOCKED);

    mvc.perform(
            patch(url(f.commitment().getId()))
                .header(HEADER, f.mgr().getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"managerAlignmentNote\":\"please tie this to SO-1.1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.managerAlignmentNote").value("please tie this to SO-1.1"));

    assertThat(commitments.findById(f.commitment().getId()).orElseThrow().getManagerAlignmentNote())
        .isEqualTo("please tie this to SO-1.1");
    assertThat(
            auditEvents.findAll().stream()
                .anyMatch(a -> a.getAction().equals("COMMITMENT_ALIGNMENT_NOTED")))
        .isTrue();
  }

  // --- #2 also writable in RECONCILING (post-lock-mutable) ----
  @Test
  void managerNote_reconcilingPlan_200() throws Exception {
    Fixture f = fixture(PlanState.RECONCILING);

    mvc.perform(
            patch(url(f.commitment().getId()))
                .header(HEADER, f.mgr().getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"managerAlignmentNote\":\"noted during reconciliation\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.managerAlignmentNote").value("noted during reconciliation"));
  }

  // --- #3 §33: the IC-owner cannot write the manager-only field → 403 + audit; note untouched ----
  @Test
  void managerNote_byIcOwner_403() throws Exception {
    Fixture f = fixture(PlanState.LOCKED);

    mvc.perform(
            patch(url(f.commitment().getId()))
                .header(HEADER, f.ic().getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"managerAlignmentNote\":\"I am the IC trying to write\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("IC_CANNOT_WRITE_MANAGER_NOTE"));
    assertThat(commitments.findById(f.commitment().getId()).orElseThrow().getManagerAlignmentNote())
        .isNull();
    assertThat(auditEvents.findAll()).hasSize(1);
    assertThat(auditEvents.findAll().get(0).getAction()).isEqualTo("AUTHORIZATION_DENIED");
  }

  // --- #4 an unrelated actor → 404 IDOR + audit ----
  @Test
  void managerNote_byUnrelatedActor_404() throws Exception {
    Fixture f = fixture(PlanState.LOCKED);
    Employee stranger = saveEmployee("stranger@x.test", RoleType.MANAGER);

    mvc.perform(
            patch(url(f.commitment().getId()))
                .header(HEADER, stranger.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"managerAlignmentNote\":\"not my report\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").doesNotExist());
    assertThat(auditEvents.findAll()).hasSize(1);
    assertThat(auditEvents.findAll().get(0).getAction()).isEqualTo("AUTHORIZATION_DENIED");
  }

  // --- #5 a manager of a DIFFERENT report → 404 IDOR + audit ----
  @Test
  void managerNote_byNonDirectManager_404() throws Exception {
    Fixture f = fixture(PlanState.LOCKED);
    Employee otherMgr = saveEmployee("othermgr@x.test", RoleType.MANAGER);
    Employee otherReport = saveEmployee("otherreport@x.test", RoleType.IC);
    saveActiveRelationship(otherMgr.getId(), otherReport.getId());

    mvc.perform(
            patch(url(f.commitment().getId()))
                .header(HEADER, otherMgr.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"managerAlignmentNote\":\"not my report either\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").doesNotExist());
    assertThat(auditEvents.findAll()).hasSize(1);
  }

  // --- #6 state guard: managerAlignmentNote on a DRAFT plan → 409 ILLEGAL_STATE_TRANSITION ----
  @Test
  void managerNote_onDraftPlan_409() throws Exception {
    Fixture f = fixture(PlanState.DRAFT);

    mvc.perform(
            patch(url(f.commitment().getId()))
                .header(HEADER, f.mgr().getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"managerAlignmentNote\":\"too early\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));
  }

  // --- #7 mixing rejection (Q2): managerAlignmentNote + an IC field → 400 ----
  @Test
  void managerNote_mixedWithIcField_400() throws Exception {
    Fixture f = fixture(PlanState.LOCKED);

    mvc.perform(
            patch(url(f.commitment().getId()))
                .header(HEADER, f.mgr().getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"managerAlignmentNote\":\"a note\",\"title\":\"and a title\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  // --- #8 present-null clears the note ----
  @Test
  void managerNote_clearsOnPresentNull() throws Exception {
    Fixture f = fixture(PlanState.LOCKED);
    // first set it
    mvc.perform(
            patch(url(f.commitment().getId()))
                .header(HEADER, f.mgr().getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"managerAlignmentNote\":\"to be cleared\"}"))
        .andExpect(status().isOk());

    mvc.perform(
            patch(url(f.commitment().getId()))
                .header(HEADER, f.mgr().getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"managerAlignmentNote\":null}"))
        .andExpect(status().isOk());

    assertThat(commitments.findById(f.commitment().getId()).orElseThrow().getManagerAlignmentNote())
        .isNull();
  }

  // --- #9 validation: >4000 code points → 400 ----
  @Test
  void managerNote_tooLong_400() throws Exception {
    Fixture f = fixture(PlanState.LOCKED);

    mvc.perform(
            patch(url(f.commitment().getId()))
                .header(HEADER, f.mgr().getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"managerAlignmentNote\":\"" + "x".repeat(4001) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  // --- #10 §15: the COMMITMENT_ALIGNMENT_NOTED audit carries NO note body ----
  @Test
  void managerNote_auditHasNoNoteBody() throws Exception {
    Fixture f = fixture(PlanState.LOCKED);

    mvc.perform(
            patch(url(f.commitment().getId()))
                .header(HEADER, f.mgr().getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"managerAlignmentNote\":\"SENSITIVE-MGR-NOTE-XYZ\"}"))
        .andExpect(status().isOk());

    var noted =
        auditEvents.findAll().stream()
            .filter(a -> a.getAction().equals("COMMITMENT_ALIGNMENT_NOTED"))
            .findFirst()
            .orElseThrow();
    assertThat(noted.getMetadataJson()).doesNotContain("SENSITIVE-MGR-NOTE-XYZ");
    assertThat(noted.getSummary()).doesNotContain("SENSITIVE-MGR-NOTE-XYZ");
  }

  // --- #11 no-regression: the IC still edits their own DRAFT commitment (the IC path is untouched)
  @Test
  void icPatchTitle_onDraft_stillWorks() throws Exception {
    Fixture f = fixture(PlanState.DRAFT);

    mvc.perform(
            patch(url(f.commitment().getId()))
                .header(HEADER, f.ic().getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"IC edited title\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("IC edited title"));
  }

  // --- #12 unauthenticated → 401 ----
  @Test
  void managerNote_unauthenticated_401() throws Exception {
    mvc.perform(
            patch(url(UUID.randomUUID()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"managerAlignmentNote\":\"x\"}"))
        .andExpect(status().isUnauthorized());
  }
}
