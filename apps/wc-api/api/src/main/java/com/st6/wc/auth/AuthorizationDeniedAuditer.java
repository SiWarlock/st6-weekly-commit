package com.st6.wc.auth;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.st6.wc.audit.AuditService;
import com.st6.wc.identity.DomainPrincipal;
import com.st6.wc.identity.UserPrincipal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the single authorization-denial {@code audit_event} per denial (task 2.5, §15 / rule #3 /
 * rule #7) in a <strong>new transaction</strong> ({@link Propagation#REQUIRES_NEW}) — so the denial
 * record <strong>survives</strong> even when the surrounding (denied) mutation's transaction rolls
 * back. Default {@code REQUIRED} would roll the denial record back <em>with</em> the denied
 * mutation, losing the rule-#3 breadcrumb (this consumes the 2.3 carry-forward). A separate
 * {@code @Service} so the proxy boundary genuinely opens the new transaction.
 *
 * <p>Metadata is safe-only (rule #7): {@code action}/{@code entityType} are fixed constants, {@code
 * entityId} is the attempted id (safe in the <em>internal</em> audit — never disclosed to the
 * caller), {@code actorEmployeeId} is the {@link UserPrincipal}'s id or {@code null} for SYSTEM,
 * and the {@code reason} is a caller-supplied fixed constant — never resource text, notes, or
 * tokens.
 */
@Service
public class AuthorizationDeniedAuditer {

  static final String ACTION = "AUTHORIZATION_DENIED";

  private final AuditService auditService;

  public AuthorizationDeniedAuditer(AuditService auditService) {
    this.auditService = auditService;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordDenial(
      DomainPrincipal principal, String entityType, UUID entityId, String reason) {
    UUID actor = (principal instanceof UserPrincipal up) ? up.employeeId() : null;
    // Build the metadata via a JSON node (properly escaped) rather than string concat — the safety
    // of the rule-#7 audit path must not rely on every caller passing a quote-free constant.
    String safeMetadata = JsonNodeFactory.instance.objectNode().put("reason", reason).toString();
    auditService.record(
        ACTION, entityType, entityId, actor, "Authorization denied: " + entityType, safeMetadata);
  }
}
