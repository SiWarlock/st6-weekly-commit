package com.st6.wc.commitment.repo;

import com.st6.wc.commitment.WeeklyCommitment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link WeeklyCommitment}. {@link
 * #findByWeeklyPlanIdOrderByIdAsc(UUID)} (task 3.3a) lists a plan's commitments for the {@code
 * WeeklyPlanDto}. Ordered by {@code id} for a <strong>deterministic</strong> response (semantic
 * insertion-order via {@code createdAt} follows once the JPA-auditing populator lands — it is not
 * yet wired, so {@code createdAt} is unreliable). {@link
 * #findByCarryForwardSourceCommitmentId(UUID)} (task 4.4, E12) backs the carry-forward idempotency
 * pre-filter — at most one successor per source by the self-link.
 */
public interface WeeklyCommitmentRepository extends JpaRepository<WeeklyCommitment, UUID> {

  List<WeeklyCommitment> findByWeeklyPlanIdOrderByIdAsc(UUID weeklyPlanId);

  Optional<WeeklyCommitment> findByCarryForwardSourceCommitmentId(
      UUID carryForwardSourceCommitmentId);
}
