package com.st6.wc.employee.repo;

import com.st6.wc.employee.Employee;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Employee}. The {@code externalSubject} finder (task 2.4) drives
 * JWT-mode identity resolution: {@code PrincipalResolver} maps a validated {@code
 * Auth0Identity.externalSubject} to the authoritative {@code Employee} row (§6 / REQ-S-007).
 */
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

  Optional<Employee> findByExternalSubject(String externalSubject);
}
