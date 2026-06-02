package com.st6.wc.relationship.repo;

import com.st6.wc.relationship.ManagerRelationship;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Bare Spring Data repository for {@link ManagerRelationship} (task 1.5). Finders land in 1.6. */
public interface ManagerRelationshipRepository extends JpaRepository<ManagerRelationship, UUID> {}
