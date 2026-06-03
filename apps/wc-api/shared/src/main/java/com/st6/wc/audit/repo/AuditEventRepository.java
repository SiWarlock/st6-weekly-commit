package com.st6.wc.audit.repo;

import com.st6.wc.audit.AuditEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Bare Spring Data repository for {@link AuditEvent} (task 1.5). Finder queries land in 1.6. */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {}
