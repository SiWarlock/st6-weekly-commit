package com.st6.wc.me;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.st6.wc.audit.repo.AuditEventRepository;
import com.st6.wc.auth.DomainAuthorizationService;
import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.RoleType;
import com.st6.wc.identity.UserPrincipal;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/me} end-to-end through the demo-mode 2.6 chain (task 2.7, §5 E1 / Appendix B.3 /
 * REQ-F-032) against real PG16 ({@link AbstractAppBootTest}). The first real {@code /api/**}
 * endpoint + first DTO across the boundary. Proves: a resolved demo identity gets its own {@code
 * MeDto} (authoritative role + relationship-driven isManager); the response is the {@code MeDto}
 * shape (no entity leak); unauthenticated → 401; a persona switch yields a different MeDto AND the
 * central authorizer still denies cross-persona access (persona-switch does NOT bypass scoping);
 * and the CORS preflight OPTIONS bypasses auth.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class MeEndpointTest extends AbstractAppBootTest {

  private static final String HEADER = "X-Demo-Employee-Id";
  private static final String DEV_ORIGIN = "http://localhost:5173";
  private static final LocalDate WEEK = LocalDate.parse("2026-09-07");

  @Autowired private MockMvc mvc;
  @Autowired private EmployeeRepository employees;
  @Autowired private ManagerRelationshipRepository relationships;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private AuditEventRepository auditEvents;
  @Autowired private DomainAuthorizationService authz;

  @AfterEach
  void cleanup() {
    auditEvents.deleteAll();
    plans.deleteAll();
    relationships.deleteAll();
    employees.deleteAll();
  }

  private Employee saveEmployee(RoleType role, String email, String tz) {
    Employee e = new Employee();
    e.setId(UUID.randomUUID());
    e.setEmail(email);
    e.setDisplayName("Name " + email);
    e.setRole(role);
    e.setActive(true);
    e.setTimezone(tz);
    return employees.saveAndFlush(e);
  }

  // --- 1 + 4. authenticated IC -> correct MeDto, DTO shape only (no entity leak) ----
  @Test
  void getMe_ic_returnsCorrectDto_notEntity() throws Exception {
    Employee ic = saveEmployee(RoleType.IC, "ada@x.test", "America/New_York");
    mvc.perform(get("/api/me").header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.employeeId").value(ic.getId().toString()))
        .andExpect(jsonPath("$.email").value("ada@x.test"))
        .andExpect(jsonPath("$.displayName").value("Name ada@x.test"))
        .andExpect(jsonPath("$.role").value("IC"))
        .andExpect(jsonPath("$.persona").value("ada@x.test"))
        .andExpect(jsonPath("$.isManager").value(false))
        .andExpect(jsonPath("$.timezone").value("America/New_York"))
        // DTO, never entity (forbidden-pattern #3): no entity-only fields leak.
        .andExpect(jsonPath("$.active").doesNotExist())
        .andExpect(jsonPath("$.externalSubject").doesNotExist())
        .andExpect(jsonPath("$.createdAt").doesNotExist())
        .andExpect(jsonPath("$.version").doesNotExist());
  }

  // --- 2. a manager with an active direct report -> isManager=true (relationship-driven) ----
  @Test
  void getMe_manager_isManagerTrue() throws Exception {
    Employee mgr = saveEmployee(RoleType.MANAGER, "boss@x.test", null);
    Employee report = saveEmployee(RoleType.IC, "rep@x.test", null);
    relationships.saveAndFlush(relationship(mgr.getId(), report.getId(), true));

    mvc.perform(get("/api/me").header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("MANAGER"))
        .andExpect(jsonPath("$.isManager").value(true));
  }

  // --- 3. unauthenticated -> 401 problem+json (composes 2.6) ----
  @Test
  void getMe_unauthenticated_401() throws Exception {
    mvc.perform(get("/api/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.safeMessage").isNotEmpty());
  }

  // --- 5. CORS preflight OPTIONS from an allowed origin -> 200, no auth, reflects the origin ----
  @Test
  void preflightOptions_allowedOrigin_succeedsWithoutAuth() throws Exception {
    mvc.perform(
            options("/api/me")
                .header(HttpHeaders.ORIGIN, DEV_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isOk()) // preflight short-circuits before auth
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, DEV_ORIGIN));
  }

  // --- 6. a persona switch yields a DIFFERENT MeDto (different id + role) ----
  @Test
  void personaSwitch_yieldsDifferentMeDto() throws Exception {
    Employee personaA = saveEmployee(RoleType.IC, "a@x.test", null);
    Employee personaB = saveEmployee(RoleType.MANAGER, "b@x.test", null);

    mvc.perform(get("/api/me").header(HEADER, personaA.getId().toString()))
        .andExpect(jsonPath("$.employeeId").value(personaA.getId().toString()))
        .andExpect(jsonPath("$.role").value("IC"));
    mvc.perform(get("/api/me").header(HEADER, personaB.getId().toString()))
        .andExpect(jsonPath("$.employeeId").value(personaB.getId().toString()))
        .andExpect(jsonPath("$.role").value("MANAGER"));
  }

  // --- 7. REQ-F-032: persona switch does NOT bypass the central authorizer's scoping ----
  @Test
  void personaSwitch_authorizerStillDenies() {
    Employee personaA = saveEmployee(RoleType.IC, "a@x.test", null);
    Employee personaB = saveEmployee(RoleType.IC, "b@x.test", null);
    WeeklyPlan planB = plans.saveAndFlush(plan(personaB.getId()));

    // acting AS persona-A, the central authorizer (2.5) still denies persona-B's resource (404) —
    // switching personas does not widen scope (composes 2.5 directly; no Phase-3 endpoint needed).
    UserPrincipal principalA = new UserPrincipal(personaA.getId(), RoleType.IC, false);
    assertThatThrownBy(() -> authz.authorizePlanAccess(principalA, planB.getId()))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);
  }

  private ManagerRelationship relationship(UUID managerId, UUID reportId, boolean active) {
    ManagerRelationship r = new ManagerRelationship();
    r.setId(UUID.randomUUID());
    r.setManagerEmployeeId(managerId);
    r.setDirectReportEmployeeId(reportId);
    r.setActive(active);
    return r;
  }

  private WeeklyPlan plan(UUID ownerIc) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(ownerIc);
    p.setWeekStartDate(WEEK);
    p.setWeekEndDate(WEEK.plusDays(6));
    p.setState(PlanState.DRAFT);
    return p;
  }
}
