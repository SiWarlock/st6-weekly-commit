package com.st6.wc.rcdo.repo;

import com.st6.wc.rcdo.DefiningObjective;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link DefiningObjective}. {@link #findAllByOrderByIdAsc()} (task 3.1)
 * backs the RCDO tree read — id-asc over the sequential V4 seed UUIDs yields logical order; the
 * mapper nests by {@code rallyCryId}. No {@code active} filter (inactive nodes are the contract).
 */
public interface DefiningObjectiveRepository extends JpaRepository<DefiningObjective, UUID> {

  List<DefiningObjective> findAllByOrderByIdAsc();
}
