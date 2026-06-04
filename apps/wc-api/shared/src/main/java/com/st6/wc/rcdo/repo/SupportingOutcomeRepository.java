package com.st6.wc.rcdo.repo;

import com.st6.wc.rcdo.SupportingOutcome;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link SupportingOutcome}. {@link #findAllByOrderByIdAsc()} (task 3.1)
 * backs the RCDO tree read (id-asc over the sequential V4 seed UUIDs → logical order; no {@code
 * active} filter). {@code findById} (inherited) backs the {@code
 * RcdoReadService.findSupportingOutcome} commitment→SO linking seam reused by 3.4/3.5.
 */
public interface SupportingOutcomeRepository extends JpaRepository<SupportingOutcome, UUID> {

  List<SupportingOutcome> findAllByOrderByIdAsc();

  /**
   * The Supporting Outcomes under one Defining Objective, id-ascending (task 6.5b, E15 drill-down)
   * — the bounded SO set the drill-down groups the report's commitments by.
   */
  List<SupportingOutcome> findByDefiningObjectiveIdOrderByIdAsc(UUID definingObjectiveId);
}
