package com.st6.wc.relationship.repo;

import com.st6.wc.relationship.ManagerRelationship;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link ManagerRelationship}. The active-manager finder is guaranteed
 * to return ≤1 row by the V2 single-active-manager partial unique (§6); {@code
 * DomainAuthorizationService} (Phase 2) uses it to scope manager access and to deny when empty. The
 * {@code existsBy…} finder (task 2.4) is the inverse — keyed on the manager side — and drives the
 * relationship-driven {@code isManager} in {@code PrincipalResolver}: {@code true} iff the employee
 * has ≥1 active relationship <em>as manager</em> (never the role claim).
 */
public interface ManagerRelationshipRepository extends JpaRepository<ManagerRelationship, UUID> {

  Optional<ManagerRelationship> findByDirectReportEmployeeIdAndActiveTrue(
      UUID directReportEmployeeId);

  boolean existsByManagerEmployeeIdAndActiveTrue(UUID managerEmployeeId);
}
