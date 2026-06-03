package com.st6.wc.auth;

/**
 * IDOR-safe denial (task 2.5, rule #3 / §5 / §16): a resource is either genuinely absent or the
 * caller is not authorized to see it — the two are <strong>indistinguishable to the caller</strong>
 * (both render {@code 404}, never revealing existence). Thrown by {@code
 * DomainAuthorizationService} for cross-owner / cross-team / not-found access; 2.6's {@code
 * ProblemDetailsExceptionHandler} renders it as an RFC-7807 {@code 404}. Carries no resource detail
 * (rule #7).
 */
public class ResourceNotFoundOrUnauthorizedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ResourceNotFoundOrUnauthorizedException() {
    super("Resource not found or not authorized");
  }
}
