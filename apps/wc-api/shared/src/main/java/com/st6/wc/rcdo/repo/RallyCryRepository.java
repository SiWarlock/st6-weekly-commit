package com.st6.wc.rcdo.repo;

import com.st6.wc.rcdo.RallyCry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link RallyCry}. {@link #findAllByOrderByIdAsc()} (task 3.1) backs
 * the RCDO tree read — id-asc over the deliberately sequential V4 seed UUIDs yields logical
 * strategy order; no {@code active} filter (inactive nodes are the contract, REQ-D-003).
 */
public interface RallyCryRepository extends JpaRepository<RallyCry, UUID> {

  List<RallyCry> findAllByOrderByIdAsc();
}
