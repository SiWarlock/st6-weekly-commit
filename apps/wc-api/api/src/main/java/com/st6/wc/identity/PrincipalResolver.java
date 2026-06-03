package com.st6.wc.identity;

import com.st6.wc.config.Auth0Identity;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves an authenticated identity to a domain {@link UserPrincipal} (task 2.4, §6 / REQ-S-007 /
 * REQ-F-031/032). This is <strong>identity resolution</strong>, deliberately separated from
 * <strong>domain authorization</strong> (2.5's {@code DomainAuthorizationService} reads the
 * resolved principal as its single input).
 *
 * <p>Two entry points, both returning {@link Optional} ({@code empty} = unresolved): a
 * validated-JWT {@link Auth0Identity} (resolved by {@code externalSubject}) and a demo employee id
 * (resolved by primary key). The resolved principal's {@code role} is the authoritative {@code
 * Employee.role} (the JWT role hint is discarded), and {@code isManager} is relationship-driven —
 * {@code true} iff an active {@code manager_relationship} exists with the employee as manager
 * (never the role claim).
 *
 * <p>A valid identity that maps to no {@code Employee} resolves to {@link Optional#empty()}
 * IDOR-safely (no existence disclosure, no further DB access); the 2.6 filter chain translates that
 * to a generic {@code 401} (§5).
 */
@Component
public class PrincipalResolver {

  private final EmployeeRepository employees;
  private final ManagerRelationshipRepository relationships;

  public PrincipalResolver(
      EmployeeRepository employees, ManagerRelationshipRepository relationships) {
    this.employees = employees;
    this.relationships = relationships;
  }

  /** JWT mode: resolve a validated {@link Auth0Identity} by its {@code externalSubject}. */
  public Optional<UserPrincipal> resolve(Auth0Identity identity) {
    return employees
        .findByExternalSubject(identity.externalSubject())
        .filter(Employee::isActive)
        .map(this::toPrincipal);
  }

  /**
   * Demo mode: resolve a demo employee id (the {@code DemoAuthFilter} principal) by primary key.
   */
  public Optional<UserPrincipal> resolve(UUID demoEmployeeId) {
    return employees.findById(demoEmployeeId).filter(Employee::isActive).map(this::toPrincipal);
  }

  private UserPrincipal toPrincipal(Employee employee) {
    boolean isManager = relationships.existsByManagerEmployeeIdAndActiveTrue(employee.getId());
    return new UserPrincipal(employee.getId(), employee.getRole(), isManager);
  }
}
