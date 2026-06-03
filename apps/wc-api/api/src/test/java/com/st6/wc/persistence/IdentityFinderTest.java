package com.st6.wc.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.RoleType;
import com.st6.wc.relationship.ManagerRelationship;
import com.st6.wc.relationship.repo.ManagerRelationshipRepository;
import com.st6.wc.support.AbstractJpaIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@code @DataJpaTest} proof for the two identity finders added in task 2.4 (LESSONS §11 harness:
 * real PG16 via {@link AbstractJpaIntegrationTest}, {@code saveAndFlush}, present + absent
 * branches).
 *
 * <ul>
 *   <li>{@code EmployeeRepository.findByExternalSubject} — drives JWT-mode resolution ({@code
 *       Auth0Identity.externalSubject} -> the {@code Employee} row);
 *   <li>{@code ManagerRelationshipRepository.existsByManagerEmployeeIdAndActiveTrue} — the inverse
 *       of the existing direct-report finder; drives {@code isManager} (true only with an
 *       <em>active</em> relationship as manager — the partial-scope branch is the load-bearing
 *       one).
 * </ul>
 */
class IdentityFinderTest extends AbstractJpaIntegrationTest {

  @Autowired private EmployeeRepository employees;
  @Autowired private ManagerRelationshipRepository relationships;

  // --- 1. findByExternalSubject: returns the seeded row; empty when no match ----
  @Test
  void findByExternalSubject_present_and_absent() {
    Employee seeded = saveEmployee(RoleType.IC, "auth0|present");

    assertThat(employees.findByExternalSubject("auth0|present"))
        .get()
        .extracting(Employee::getId)
        .isEqualTo(seeded.getId());
    assertThat(employees.findByExternalSubject("auth0|absent")).isEmpty();
  }

  // --- 2. existsByManagerEmployeeIdAndActiveTrue: true active / false inactive-only / false none -
  @Test
  void existsByManagerEmployeeIdAndActiveTrue_matrix() {
    Employee report = saveEmployee(RoleType.IC, null);

    // (a) active relationship as manager -> true
    Employee activeManager = saveEmployee(RoleType.MANAGER, null);
    relationships.saveAndFlush(newRelationship(activeManager.getId(), report.getId(), true));
    assertThat(relationships.existsByManagerEmployeeIdAndActiveTrue(activeManager.getId()))
        .isTrue();

    // (b) only an INACTIVE relationship as manager -> false (the partial-scope branch)
    Employee inactiveManager = saveEmployee(RoleType.MANAGER, null);
    relationships.saveAndFlush(newRelationship(inactiveManager.getId(), report.getId(), false));
    assertThat(relationships.existsByManagerEmployeeIdAndActiveTrue(inactiveManager.getId()))
        .isFalse();

    // (c) no relationship at all -> false
    Employee neverManager = saveEmployee(RoleType.MANAGER, null);
    assertThat(relationships.existsByManagerEmployeeIdAndActiveTrue(neverManager.getId()))
        .isFalse();
  }

  // ===== fixtures =====

  private Employee saveEmployee(RoleType role, String externalSubject) {
    Employee e = new Employee();
    e.setId(UUID.randomUUID());
    e.setExternalSubject(externalSubject);
    e.setEmail(UUID.randomUUID() + "@x.test");
    e.setDisplayName("N");
    e.setRole(role);
    e.setActive(true);
    return employees.saveAndFlush(e);
  }

  private ManagerRelationship newRelationship(UUID managerId, UUID reportId, boolean active) {
    ManagerRelationship r = new ManagerRelationship();
    r.setId(UUID.randomUUID());
    r.setManagerEmployeeId(managerId);
    r.setDirectReportEmployeeId(reportId);
    r.setActive(active);
    return r;
  }
}
