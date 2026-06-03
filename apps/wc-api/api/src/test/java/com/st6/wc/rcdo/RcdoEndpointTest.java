package com.st6.wc.rcdo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.RoleType;
import com.st6.wc.support.AbstractAppBootTest;
import java.util.Set;
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
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * {@code GET /api/rcdo} end-to-end through the demo-mode 2.6 chain (task 3.1, §5 E2 / Appendix B.4
 * / §6 / REQ-D-003) against real PG16 + the {@code V4__seed_rcdo.sql} 1/3/9 seed ({@link
 * AbstractAppBootTest}). Proves: the read returns the nested {@link
 * com.st6.wc.rcdo.dto.RcdoTreeDto} object-wrapper (NOT a bare array) with correct parent ids;
 * records only (no entity-only field leak, forbidden-pattern #3); org-wide read — authenticated IC
 * <em>and</em> Manager both 200 (no per-resource {@code DomainAuthorizationService} call),
 * unauthenticated → 401 (composes 2.6); and <strong>no RCDO mutation endpoint is registered
 * anywhere</strong> (REQ-D-003).
 *
 * <p>The RCDO rows come from the V4 Flyway seed (present at boot, read-only) — this test never
 * mutates them; it only creates/cleans the demo {@code Employee} rows needed to authenticate.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "demo-auth.enabled=true")
@AutoConfigureMockMvc
class RcdoEndpointTest extends AbstractAppBootTest {

  private static final String HEADER = "X-Demo-Employee-Id";
  private static final String SEED_RALLY_CRY_TITLE =
      "Become the system of record every execution-driven team trusts by end of FY26.";
  // logical-order anchors (Appendix E Part 2): DO-1 first, SO-1.1 first — pinned via id-asc seed
  // UUIDs
  private static final String SEED_DO_1_TITLE = "Win customer adoption & expansion";
  private static final String SEED_SO_1_1_TITLE = "Lift activated-team weekly-active rate to 70%";

  @Autowired private MockMvc mvc;
  @Autowired private EmployeeRepository employees;

  // qualify by name: actuator also registers a RequestMappingHandlerMapping (controllerEndpoint…)
  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  @AfterEach
  void cleanup() {
    employees.deleteAll(); // never touch the read-only V4 RCDO seed
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

  // --- RED #1 + #9: seeded 1/3/9 tree -> nested object wrapper, parent ids correct, all active
  // ----
  @Test
  void getRcdo_seeded_returnsNestedWrapperWithParentIds() throws Exception {
    Employee ic = saveEmployee(RoleType.IC, "ada@x.test");

    mvc.perform(get("/api/rcdo").header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        // object wrapper, never a bare array (B.4 / §5 envelope)
        .andExpect(jsonPath("$.rallyCries").isArray())
        .andExpect(jsonPath("$.rallyCries.length()").value(1))
        .andExpect(jsonPath("$.rallyCries[0].title").value(SEED_RALLY_CRY_TITLE))
        .andExpect(jsonPath("$.rallyCries[0].active").value(true))
        // 3 Defining Objectives, each pointing back at the Rally Cry (parent id on the child),
        // in LOGICAL order (DO-1 first) — pinned by the id-asc finders + sequential seed UUIDs
        .andExpect(jsonPath("$.rallyCries[0].definingObjectives.length()").value(3))
        .andExpect(jsonPath("$.rallyCries[0].definingObjectives[0].title").value(SEED_DO_1_TITLE))
        .andExpect(
            jsonPath("$.rallyCries[0].definingObjectives[0].rallyCryId")
                .value(org.hamcrest.Matchers.notNullValue()))
        // 3 Supporting Outcomes under DO-1, in logical order (SO-1.1 first), each with its parent
        // id
        .andExpect(
            jsonPath("$.rallyCries[0].definingObjectives[0].supportingOutcomes.length()").value(3))
        .andExpect(
            jsonPath("$.rallyCries[0].definingObjectives[0].supportingOutcomes[0].title")
                .value(SEED_SO_1_1_TITLE))
        .andExpect(
            jsonPath(
                    "$.rallyCries[0].definingObjectives[0].supportingOutcomes[0].definingObjectiveId")
                .value(org.hamcrest.Matchers.notNullValue()));
  }

  // --- RED #4: response is the B.4 DTO shape — records only, no entity-only field leak ----
  @Test
  void getRcdo_returnsDtoRecords_notEntities() throws Exception {
    Employee ic = saveEmployee(RoleType.IC, "ada@x.test");

    mvc.perform(get("/api/rcdo").header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        // top level is an OBJECT, not a bare array (a bare array would expose $[0])
        .andExpect(jsonPath("$[0]").doesNotExist())
        // entity audit quartet must never cross the boundary (forbidden-pattern #3)
        .andExpect(jsonPath("$.rallyCries[0].createdAt").doesNotExist())
        .andExpect(jsonPath("$.rallyCries[0].updatedAt").doesNotExist())
        .andExpect(jsonPath("$.rallyCries[0].createdBy").doesNotExist())
        .andExpect(jsonPath("$.rallyCries[0].definingObjectives[0].createdAt").doesNotExist())
        .andExpect(
            jsonPath("$.rallyCries[0].definingObjectives[0].supportingOutcomes[0].createdAt")
                .doesNotExist());
  }

  // --- RED #7a: unauthenticated -> 401 problem+json (composes 2.6) ----
  @Test
  void getRcdo_unauthenticated_401() throws Exception {
    mvc.perform(get("/api/rcdo"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.safeMessage").isNotEmpty());
  }

  // --- RED #7b: org-wide read -> authenticated IC AND Manager both 200 (no per-resource authz)
  // ----
  @Test
  void getRcdo_authenticatedIc_andManager_bothOk() throws Exception {
    Employee ic = saveEmployee(RoleType.IC, "ic@x.test");
    Employee mgr = saveEmployee(RoleType.MANAGER, "mgr@x.test");

    mvc.perform(get("/api/rcdo").header(HEADER, ic.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rallyCries").isArray());
    mvc.perform(get("/api/rcdo").header(HEADER, mgr.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rallyCries").isArray());
  }

  // --- RED #8: structural — NO mutation endpoint exists under /api/rcdo* (REQ-D-003) ----
  @Test
  void noRcdoMutationEndpoint_registered() {
    Set<RequestMethod> mutating =
        Set.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);

    for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
      var patterns = info.getPathPatternsCondition(); // nullable — read once, then null-check
      if (patterns == null) {
        continue;
      }
      boolean touchesRcdo =
          patterns.getPatternValues().stream().anyMatch(p -> p.startsWith("/api/rcdo"));
      if (touchesRcdo) {
        assertThat(info.getMethodsCondition().getMethods())
            .as("no mutating verb may be mapped under /api/rcdo (read-only, REQ-D-003)")
            .doesNotContainAnyElementsOf(mutating);
      }
    }
  }
}
