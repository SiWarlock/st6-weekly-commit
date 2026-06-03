package com.st6.wc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.st6.wc.audit.repo.AuditEventRepository;
import com.st6.wc.auth.DomainAuthorizationService;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.RoleType;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.support.AbstractAppBootTest;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo-mode {@code SecurityFilterChain} integration proof (task 2.6, SAFETY-touching — rule #3
 * operational half) against the real chain + real PG16 ({@link AbstractAppBootTest}). With {@code
 * demo-auth.enabled=true} the {@code DemoAuthFilter} authenticates an {@code X-Demo-Employee-Id} to
 * a {@link UserPrincipal} (no JWT path), and every {@code /api/**} request requires that principal.
 * Proves: a resolved demo identity reaches a protected endpoint as a {@code UserPrincipal}; health
 * is public; unauthenticated {@code /api/**} → 401 problem+json; the coarse manager gate (403); an
 * inactive employee → 401 (Q-B); a cross-owner request flows through {@code
 * DomainAuthorizationService} → 404 problem+json + one denial audit; and the context is cleared
 * per-request (no bleed).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class SecurityChainDemoModeTest extends AbstractAppBootTest {

  @TestConfiguration
  static class Endpoints {
    @Bean
    SecuredTestController securedTestController(DomainAuthorizationService authz) {
      return new SecuredTestController(authz);
    }
  }

  @RestController
  static class SecuredTestController {
    private final DomainAuthorizationService authz;

    SecuredTestController(DomainAuthorizationService authz) {
      this.authz = authz;
    }

    @GetMapping("/api/test/ping")
    String ping(@AuthenticationPrincipal UserPrincipal principal) {
      return principal == null ? "anonymous" : principal.getClass().getSimpleName();
    }

    @GetMapping("/api/test/manager")
    @PreAuthorize("hasRole('MANAGER')")
    String managerOnly() {
      return "ok";
    }

    @GetMapping("/api/test/plan/{id}")
    String plan(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
      authz.authorizePlanAccess(principal, id); // throws 404 (IDOR-safe) + audits on cross-owner
      return "ok";
    }
  }

  private static final String HEADER = "X-Demo-Employee-Id";
  private static final LocalDate WEEK = LocalDate.parse("2026-09-07");

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

  private Employee saveEmployee(RoleType role, boolean active) {
    Employee e = new Employee();
    e.setId(UUID.randomUUID());
    e.setEmail(UUID.randomUUID() + "@x.test");
    e.setDisplayName("N");
    e.setRole(role);
    e.setActive(active);
    return employees.saveAndFlush(e);
  }

  private WeeklyPlan savePlan(UUID ownerIc) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(ownerIc);
    p.setWeekStartDate(WEEK);
    p.setWeekEndDate(WEEK.plusDays(6));
    p.setState(PlanState.DRAFT);
    return plans.saveAndFlush(p);
  }

  // --- 1. a resolved demo identity reaches a protected endpoint AS a UserPrincipal ----
  @Test
  void authenticatedDemoRequest_reachesEndpoint_principalIsUserPrincipal() throws Exception {
    Employee ic = saveEmployee(RoleType.IC, true);
    mvc.perform(get("/api/test/ping").header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(content().string("UserPrincipal"));
  }

  // --- 2. health public; /api/** requires an authenticated principal ----
  @Test
  void healthPublic_apiRequiresAuth() throws Exception {
    mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    mvc.perform(get("/api/test/ping")).andExpect(status().isUnauthorized());
  }

  // --- 3. unauthenticated /api/** -> 401 application/problem+json, safeMessage + traceId ----
  @Test
  void unauthenticatedApiRequest_401ProblemJson() throws Exception {
    mvc.perform(get("/api/test/ping"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.safeMessage").isNotEmpty())
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }

  // --- 4. coarse manager gate: an IC principal on a manager-only endpoint -> 403 ----
  @Test
  void managerGatedEndpoint_icPrincipal_coarse403() throws Exception {
    Employee ic = saveEmployee(RoleType.IC, true);
    mvc.perform(get("/api/test/manager").header(HEADER, ic.getId().toString()))
        .andExpect(status().isForbidden());
  }

  // --- 4b. a MANAGER principal passes the coarse gate ----
  @Test
  void managerGatedEndpoint_managerPrincipal_200() throws Exception {
    Employee mgr = saveEmployee(RoleType.MANAGER, true);
    mvc.perform(get("/api/test/manager").header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk());
  }

  // --- 5 + 11. cross-owner request -> 404 problem+json AND exactly one denial audit (composes 2.5)
  // -
  @Test
  void crossOwnerRequest_404ProblemJson_andOneAudit() throws Exception {
    Employee ic1 = saveEmployee(RoleType.IC, true);
    Employee ic2 = saveEmployee(RoleType.IC, true);
    WeeklyPlan ic2Plan = savePlan(ic2.getId());
    assertThat(auditEvents.count()).isZero();

    mvc.perform(get("/api/test/plan/" + ic2Plan.getId()).header(HEADER, ic1.getId().toString()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.safeMessage").isNotEmpty())
        .andExpect(jsonPath("$.traceId").isNotEmpty());

    assertThat(auditEvents.count())
        .isEqualTo(1); // the rule-#3 denial audit fired through the chain
  }

  // --- 10. an inactive employee's demo id -> 401 IDOR-safe (Q-B end-to-end) ----
  @Test
  void inactiveEmployee_demoId_denied401() throws Exception {
    Employee inactive = saveEmployee(RoleType.IC, false);
    mvc.perform(get("/api/test/ping").header(HEADER, inactive.getId().toString()))
        .andExpect(status().isUnauthorized());
  }

  // --- 7. the SecurityContext is cleared per request (no principal bleed) ----
  @Test
  void securityContextClearedPerRequest() throws Exception {
    Employee ic = saveEmployee(RoleType.IC, true);
    // an authenticated request...
    mvc.perform(get("/api/test/ping").header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk());
    // ...does NOT leak its principal into a following unauthenticated request.
    mvc.perform(get("/api/test/ping")).andExpect(status().isUnauthorized());
  }
}
