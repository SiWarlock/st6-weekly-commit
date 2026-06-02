package com.st6.wc.rcdo.repo;

import com.st6.wc.rcdo.SupportingOutcome;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Bare Spring Data repository for {@link SupportingOutcome} (task 1.5). Finders land in 1.6. */
public interface SupportingOutcomeRepository extends JpaRepository<SupportingOutcome, UUID> {}
