package com.st6.wc.dispute.repo;

import com.st6.wc.dispute.AlignmentDispute;
import com.st6.wc.enums.DisputeStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link AlignmentDispute}. The unresolved-dispute finder (callers pass
 * the unresolved bucket {@code {OPEN, IC_RESPONDED}}) returns ≤1 row by the V2 partial unique
 * (safety rule #6); dispute/lifecycle services (Phase 3/5) use it.
 */
public interface AlignmentDisputeRepository extends JpaRepository<AlignmentDispute, UUID> {

  Optional<AlignmentDispute> findByCommitmentIdAndStatusIn(
      UUID commitmentId, Collection<DisputeStatus> statuses);

  /**
   * Counts disputes across a set of commitments in the given status bucket — {@code
   * ReviewStatusDeriver} (task 5.2, {@code :api}) passes a plan's commitment ids + the unresolved
   * bucket {@code {OPEN, IC_RESPONDED}} to derive {@code REVIEWED_WITH_DISPUTES}. The caller
   * short-circuits an empty commitment set (no query) — an empty SQL {@code IN} is invalid.
   */
  int countByCommitmentIdInAndStatusIn(
      Collection<UUID> commitmentIds, Collection<DisputeStatus> statuses);
}
