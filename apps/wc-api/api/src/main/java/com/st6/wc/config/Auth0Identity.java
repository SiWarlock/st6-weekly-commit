package com.st6.wc.config;

import com.st6.wc.enums.RoleType;

/**
 * The pure identity extracted from a validated Auth0 {@link
 * org.springframework.security.oauth2.jwt.Jwt} by {@link Auth0ClaimMapper} (task 2.2, §6 / Appendix
 * F.1). {@code role} is the coarse JWT hint (nullable — the authoritative role + {@code isManager}
 * are relationship-derived in 2.4); {@code email} is nullable (display/reconciliation only). 2.4's
 * {@code PrincipalResolver} resolves {@code externalSubject} → the {@code Employee} row.
 */
public record Auth0Identity(String externalSubject, RoleType role, String email) {}
