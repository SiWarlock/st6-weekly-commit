package com.st6.wc.rcdo.repo;

import com.st6.wc.rcdo.RallyCry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Bare Spring Data repository for {@link RallyCry} (task 1.5). Finder queries land in 1.6. */
public interface RallyCryRepository extends JpaRepository<RallyCry, UUID> {}
