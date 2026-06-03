package com.st6.wc.plan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.st6.wc.support.AbstractAppBootTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/plans/current} end-to-end through the demo-mode 2.6 chain (task 3.3a, §5 E3 / §6
 * / Appendix B.5/B.6) against real PG16 + the V4 RCDO seed ({@link AbstractAppBootTest}). Proves
 * the IC self-read returns its own current-week {@link com.st6.wc.plan.dto.WeeklyPlanDto} (records,
 * no entity leak), with the linked commitment's RC→DO→SO breadcrumb resolved from the seed and
 * {@code managerReview} null while DRAFT; an empty shell yields no {@code LOCK} affordance;
 * unauthenticated → 401; an absent current-week shell → {@code 404 PLAN_NOT_FOUND} (no
 * create-on-GET). Self-scoped: no per-resource authorizer call (the {@code {id}} IDOR surface is
 * 3.3b).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class PlanCurrentEndpointTest extends AbstractAppBootTest {

  private static final String HEADER = "X-Demo-Employee-Id";
  // Tue 2026-06-02 12:00 America/Chicago → current week Mon 2026-06-01 … Sun 2026-06-07.
  private static final Instant ANCHOR = Instant.parse("2026-06-02T17:00:00Z");
  private static final LocalDate WEEK_MONDAY = LocalDate.of(2026, 6, 1);
  // V4 seed SO-1.1 (under DO-1) — the linked Supporting Outcome for the breadcrumb assertion.
  private static final UUID SEED_SO_1_1 = UUID.fromString("c0000000-0000-0000-0000-000000000001");
  private static final String SEED_SO_1_1_TITLE = "Lift activated-team weekly-active rate to 70%";
  private static final String SEED_DO_1_TITLE = "Win customer adoption & expansion";

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(ANCHOR, ZoneOffset.UTC);
    }
  }

  @Autowired private MockMvc mvc;
  @Autowired private EmployeeRepository employees;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private WeeklyCommitmentRepository commitments;

  @AfterEach
  void cleanup() {
    commitments.deleteAll();
    plans.deleteAll();
    employees.deleteAll();
  }

  private Employee saveIc(String email, String display) {
    Employee e = new Employee();
    e.setId(UUID.randomUUID());
    e.setEmail(email);
    e.setDisplayName(display);
    e.setRole(RoleType.IC);
    e.setActive(true);
    return employees.saveAndFlush(e);
  }

  private WeeklyPlan saveDraftShell(UUID ownerId) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(ownerId);
    p.setWeekStartDate(WEEK_MONDAY);
    p.setWeekEndDate(WEEK_MONDAY.plusDays(6));
    p.setState(PlanState.DRAFT);
    return plans.saveAndFlush(p);
  }

  private void savePlannedCommitment(UUID planId, UUID soId) {
    WeeklyCommitment c = new WeeklyCommitment();
    c.setId(UUID.randomUUID());
    c.setWeeklyPlanId(planId);
    c.setCommitmentKind(CommitmentKind.PLANNED);
    c.setTitle("Ship the thing");
    c.setSupportingOutcomeId(soId);
    c.setPriority(Priority.P1);
    c.setWorkType(WorkType.STRATEGIC);
    c.setConfidence(Confidence.MEDIUM);
    c.setAlignmentStatus(AlignmentStatus.NEEDS_REVIEW);
    commitments.saveAndFlush(c);
  }

  // --- RED #1: own current-week DRAFT shell -> full B.5 DTO, linked breadcrumb, review null ----
  @Test
  void current_returnsOwnDraftShell_fullDto() throws Exception {
    Employee ic = saveIc("ada@x.test", "Ada Lovelace");
    WeeklyPlan plan = saveDraftShell(ic.getId());
    savePlannedCommitment(plan.getId(), SEED_SO_1_1); // linked to a seeded SO

    mvc.perform(get("/api/plans/current").header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(plan.getId().toString()))
        .andExpect(jsonPath("$.employeeId").value(ic.getId().toString()))
        .andExpect(jsonPath("$.employeeDisplayName").value("Ada Lovelace"))
        .andExpect(jsonPath("$.weekStartDate").value("2026-06-01"))
        .andExpect(jsonPath("$.weekEndDate").value("2026-06-07"))
        .andExpect(jsonPath("$.state").value("DRAFT"))
        .andExpect(jsonPath("$.plannedCount").value(1))
        .andExpect(jsonPath("$.unplannedCount").value(0))
        .andExpect(jsonPath("$.managerReview").doesNotExist()) // null while DRAFT (B.5)
        .andExpect(jsonPath("$.commitments.length()").value(1))
        .andExpect(jsonPath("$.commitments[0].supportingOutcomeId").value(SEED_SO_1_1.toString()))
        // breadcrumb resolved from the V4 seed (RC→DO→SO labels)
        .andExpect(
            jsonPath("$.commitments[0].supportingOutcomeBreadcrumb.supportingOutcomeTitle")
                .value(SEED_SO_1_1_TITLE))
        .andExpect(
            jsonPath("$.commitments[0].supportingOutcomeBreadcrumb.definingObjectiveTitle")
                .value(SEED_DO_1_TITLE));
  }

  // --- RED #2: empty DRAFT shell -> empty commitments, plannedCount 0, no LOCK affordance ----
  @Test
  void current_emptyShell_emptyCommitmentsAndNoLock() throws Exception {
    Employee ic = saveIc("ic@x.test", "IC");
    saveDraftShell(ic.getId());

    mvc.perform(get("/api/plans/current").header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.commitments.length()").value(0))
        .andExpect(jsonPath("$.plannedCount").value(0))
        // no planned commitments → LOCK not offered (the empty-shell case; B.1/F.4 LOCK predicate)
        .andExpect(jsonPath("$.allowedActions").isArray())
        .andExpect(jsonPath("$.allowedActions[?(@ == 'LOCK')]").isEmpty());
  }

  // --- RED #3: records, not entities — no entity-only field leaks (forbidden-pattern #3) ----
  @Test
  void current_returnsRecords_notEntities() throws Exception {
    Employee ic = saveIc("ic@x.test", "IC");
    WeeklyPlan plan = saveDraftShell(ic.getId());
    savePlannedCommitment(plan.getId(), null);

    mvc.perform(get("/api/plans/current").header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        // entity audit quartet never crosses the boundary
        .andExpect(jsonPath("$.createdAt").doesNotExist())
        .andExpect(jsonPath("$.createdBy").doesNotExist())
        .andExpect(jsonPath("$.commitments[0].createdAt").doesNotExist())
        .andExpect(jsonPath("$.commitments[0].active").doesNotExist());
  }

  // --- RED #4: unauthenticated -> 401 problem+json (composes 2.6) ----
  @Test
  void current_unauthenticated_401() throws Exception {
    mvc.perform(get("/api/plans/current"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.safeMessage").isNotEmpty());
  }

  // --- RED #8: no current-week shell for the caller -> 404 PLAN_NOT_FOUND (no create-on-GET) ----
  @Test
  void current_absentShell_404PlanNotFound() throws Exception {
    Employee ic = saveIc("ada@x.test", "Ada"); // employee exists, but no plan shell for this week
    mvc.perform(get("/api/plans/current").header(HEADER, ic.getId().toString()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));
  }
}
