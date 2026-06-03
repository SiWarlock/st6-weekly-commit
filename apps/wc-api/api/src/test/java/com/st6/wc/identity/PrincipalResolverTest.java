package com.st6.wc.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.st6.wc.config.Auth0Identity;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.RoleType;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * {@code PrincipalResolver} unit proof (task 2.4, §6 / REQ-S-007 / REQ-F-031/032). Identity
 * resolution is exercised directly with mock repos (no DB — that's {@code IdentityFinderTest}).
 * Pins the load-bearing rules 2.5's IDOR authorizer builds on:
 *
 * <ul>
 *   <li>{@code role} is the <em>authoritative</em> {@code Employee.role} (DB row), never the
 *       nullable {@code Auth0Identity.role} JWT hint;
 *   <li>{@code isManager} is <em>relationship-driven</em> — true iff an active {@code
 *       manager_relationship} exists with the employee as manager — never the role claim;
 *   <li>a valid identity that maps to no {@code Employee} resolves IDOR-safely to empty (no
 *       existence leak, no further DB access);
 *   <li>{@code RoleType} admits only {@code {IC, MANAGER}} (no admin/skip-level principal);
 *   <li>{@code SystemPrincipal} is a distinct no-HTTP boundary type.
 * </ul>
 */
class PrincipalResolverTest {

  private final EmployeeRepository employees = mock(EmployeeRepository.class);
  private final ManagerRelationshipRepository relationships =
      mock(ManagerRelationshipRepository.class);
  private final PrincipalResolver resolver = new PrincipalResolver(employees, relationships);

  private static Employee employee(UUID id, RoleType role) {
    Employee e = new Employee();
    e.setId(id);
    e.setRole(role);
    return e;
  }

  // --- 1. JWT mode: externalSubject -> matching IC principal (and getName() pins employeeId) ----
  @Test
  void jwtMode_resolvesToCorrectIcPrincipal() {
    UUID id = UUID.randomUUID();
    when(employees.findByExternalSubject("auth0|ic-1"))
        .thenReturn(Optional.of(employee(id, RoleType.IC)));
    when(relationships.existsByManagerEmployeeIdAndActiveTrue(id)).thenReturn(false);

    Optional<UserPrincipal> result =
        resolver.resolve(new Auth0Identity("auth0|ic-1", RoleType.IC, "ic@x.test"));

    assertThat(result)
        .get()
        .satisfies(
            p -> {
              assertThat(p.employeeId()).isEqualTo(id);
              assertThat(p.role()).isEqualTo(RoleType.IC);
              assertThat(p.isManager()).isFalse();
              // UserPrincipal implements Spring's AuthenticatedPrincipal: getName() = employee id
              // (so 2.6's authentication.getName() resolves to it).
              assertThat(p.getName()).isEqualTo(id.toString());
            });
  }

  // --- 2. demo mode: employeeId (UUID) -> matching principal by id ----
  @Test
  void demoMode_resolvesMatchingEmployee() {
    UUID id = UUID.randomUUID();
    when(employees.findById(id)).thenReturn(Optional.of(employee(id, RoleType.IC)));
    when(relationships.existsByManagerEmployeeIdAndActiveTrue(id)).thenReturn(false);

    Optional<UserPrincipal> result = resolver.resolve(id);

    assertThat(result)
        .get()
        .satisfies(
            p -> {
              assertThat(p.employeeId()).isEqualTo(id);
              assertThat(p.role()).isEqualTo(RoleType.IC);
            });
  }

  // --- 3. active managed report -> isManager = true (relationship-driven) ----
  @Test
  void employeeWithActiveManagedReport_isManagerTrue() {
    UUID id = UUID.randomUUID();
    when(employees.findByExternalSubject("auth0|mgr-1"))
        .thenReturn(Optional.of(employee(id, RoleType.MANAGER)));
    when(relationships.existsByManagerEmployeeIdAndActiveTrue(id)).thenReturn(true);

    Optional<UserPrincipal> result =
        resolver.resolve(new Auth0Identity("auth0|mgr-1", RoleType.MANAGER, "m@x.test"));

    assertThat(result).get().extracting(UserPrincipal::isManager).isEqualTo(true);
  }

  // --- 4. MANAGER role but NO active relationship -> isManager = false (NOT role-driven) ----
  @Test
  void managerRoleClaimButNoActiveRelationship_isManagerFalse() {
    UUID id = UUID.randomUUID();
    when(employees.findByExternalSubject("auth0|mgr-2"))
        .thenReturn(Optional.of(employee(id, RoleType.MANAGER)));
    when(relationships.existsByManagerEmployeeIdAndActiveTrue(id)).thenReturn(false);

    Optional<UserPrincipal> result =
        resolver.resolve(new Auth0Identity("auth0|mgr-2", RoleType.MANAGER, "m@x.test"));

    // the load-bearing rule-#3 foundation: a MANAGER-role employee with no active managed report
    // is isManager=false for relationship-gated reads.
    assertThat(result).get().extracting(UserPrincipal::isManager).isEqualTo(false);
  }

  // --- 5. authoritative role comes from the Employee row, not the JWT hint ----
  @Test
  void roleIsAuthoritativeFromEmployeeRow_notJwtHint() {
    UUID id = UUID.randomUUID();
    when(employees.findByExternalSubject("auth0|x"))
        .thenReturn(Optional.of(employee(id, RoleType.MANAGER)));
    when(relationships.existsByManagerEmployeeIdAndActiveTrue(id)).thenReturn(true);

    // JWT hint says IC; the DB row says MANAGER -> the DB row wins.
    Optional<UserPrincipal> result =
        resolver.resolve(new Auth0Identity("auth0|x", RoleType.IC, "x@x.test"));

    assertThat(result).get().extracting(UserPrincipal::role).isEqualTo(RoleType.MANAGER);
  }

  // --- 6. identity mapping to no Employee -> IDOR-safe empty (both entry points), no DB echo ----
  @Test
  void identityMappingToNoEmployee_isDeniedIdorSafe() {
    UUID unknownId = UUID.randomUUID();
    when(employees.findByExternalSubject("auth0|ghost")).thenReturn(Optional.empty());
    when(employees.findById(unknownId)).thenReturn(Optional.empty());

    Optional<UserPrincipal> jwtResult =
        resolver.resolve(new Auth0Identity("auth0|ghost", RoleType.IC, null));
    Optional<UserPrincipal> demoResult = resolver.resolve(unknownId);

    // unresolved = empty (the 2.6 chain translates empty -> 401 IDOR-safe); no existence
    // disclosure.
    assertThat(jwtResult).isEmpty();
    assertThat(demoResult).isEmpty();
    // no Employee -> the relationship/isManager probe never runs (no DB-state echo on a denial).
    verifyNoInteractions(relationships);
  }

  // --- 7. no admin / HR / skip-level role is resolvable (REQ-F-031) ----
  @Test
  void noAdminOrSkipLevelRoleResolvable() {
    assertThat(RoleType.values()).containsExactlyInAnyOrder(RoleType.IC, RoleType.MANAGER);
    assertThatThrownBy(() -> RoleType.valueOf("ADMIN"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RoleType.valueOf("SKIP_LEVEL"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // --- 8. SystemPrincipal is a distinct, no-HTTP, singleton boundary type ----
  @Test
  void systemPrincipal_isDistinctNoHttpActor() {
    SystemPrincipal system = SystemPrincipal.INSTANCE;

    assertThat(system).isNotNull();
    // singleton: the same canonical instance the worker/cron phases consume.
    assertThat(SystemPrincipal.INSTANCE).isSameAs(system);
    // distinct from the resolved user principal type — carries no user employeeId.
    assertThat(system).isNotInstanceOf(UserPrincipal.class);
    // and is NOT a Spring authenticated principal either — the no-HTTP SYSTEM-actor boundary pin
    // (only UserPrincipal carries an authenticated identity; SystemPrincipal is exempt from the
    // self/direct-report checks 2.5 applies to AuthenticatedPrincipal-bearing requests).
    assertThat(system).isNotInstanceOf(AuthenticatedPrincipal.class);
  }
}
