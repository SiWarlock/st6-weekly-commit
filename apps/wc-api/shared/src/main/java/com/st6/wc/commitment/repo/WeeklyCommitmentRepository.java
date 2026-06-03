package com.st6.wc.commitment.repo;

import com.st6.wc.commitment.WeeklyCommitment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Bare Spring Data repository for {@link WeeklyCommitment} (task 1.5). Finders land in 1.6. */
public interface WeeklyCommitmentRepository extends JpaRepository<WeeklyCommitment, UUID> {}
