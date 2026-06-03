package com.st6.wc.audit;

import com.st6.wc.audit.repo.AuditEventRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Minimal central audit writer (task 2.3; §15). Persists an {@link AuditEvent} with
 * <strong>safe-only metadata</strong> (rule #7 — callers pass an action + generic summary +
 * already-safe JSON; never tokens/secrets/PII/raw untrusted input). {@code created_at} is set from
 * the injectable {@code :shared} {@link Clock} (no JPA auditing populator yet — Phase 2 identity);
 * {@code actorEmployeeId} is nullable (SYSTEM). The 2.3 demo-rejection audit is the first caller;
 * 2.5/§15 extend this.
 *
 * <p>{@code @Transactional} so the audit persists in its own transaction even when written from a
 * filter (pre-controller, no ambient transaction) — e.g. the demo-backdoor rejection.
 */
@Service
public class AuditService {

  private final AuditEventRepository repository;
  private final Clock clock;

  public AuditService(AuditEventRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional
  public void record(
      String action,
      String entityType,
      UUID entityId,
      UUID actorEmployeeId,
      String summary,
      String safeMetadataJson) {
    AuditEvent event = new AuditEvent();
    event.setId(UUID.randomUUID());
    event.setAction(action);
    event.setEntityType(entityType);
    event.setEntityId(entityId);
    event.setActorEmployeeId(actorEmployeeId);
    event.setSummary(summary);
    event.setMetadataJson(safeMetadataJson);
    event.setCreatedAt(clock.instant());
    repository.save(event);
  }
}
