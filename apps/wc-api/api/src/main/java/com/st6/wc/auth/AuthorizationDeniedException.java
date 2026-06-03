package com.st6.wc.auth;

/**
 * Capability denial (task 2.5, §5 / §6): the caller can legitimately <em>see</em> the resource (or
 * the surface is categorically role-gated) but lacks the capability for this action — rendered as a
 * {@code 403}, NOT existence-hidden. Carries a stable {@code code} (e.g. {@code
 * IC_CANNOT_RESOLVE_DISPUTE}, {@code MANAGER_ROLE_REQUIRED}) that 2.6's {@code
 * ProblemDetailsExceptionHandler} maps into the RFC-7807 body. Distinct from {@link
 * ResourceNotFoundOrUnauthorizedException} (the IDOR-safe {@code 404}).
 */
public class AuthorizationDeniedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String code;

  public AuthorizationDeniedException(String code) {
    super("Authorization denied");
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
