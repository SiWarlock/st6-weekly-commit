package com.st6.wc.dispute.repo;

import com.st6.wc.dispute.AlignmentDispute;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Bare Spring Data repository for {@link AlignmentDispute} (task 1.5). Finders land in 1.6. */
public interface AlignmentDisputeRepository extends JpaRepository<AlignmentDispute, UUID> {}
