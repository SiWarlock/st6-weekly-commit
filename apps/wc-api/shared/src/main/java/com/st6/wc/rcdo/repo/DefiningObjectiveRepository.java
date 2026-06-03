package com.st6.wc.rcdo.repo;

import com.st6.wc.rcdo.DefiningObjective;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Bare Spring Data repository for {@link DefiningObjective} (task 1.5). Finders land in 1.6. */
public interface DefiningObjectiveRepository extends JpaRepository<DefiningObjective, UUID> {}
