package com.st6.wc.web;

import com.st6.wc.auth.AuthorizationDeniedException;
import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The first {@code @RestControllerAdvice} (task 2.6, §5 / Appendix B.21 / §16 RISK-016) — renders
 * exceptions thrown <em>during controller invocation</em> as RFC-7807 {@code
 * application/problem+json}. (Chain-filter authn/authz exceptions never reach here; those are the
 * {@code ProblemDetailsAuthenticationEntryPoint} / {@code ProblemDetailsAccessDeniedHandler}.)
 *
 * <ul>
 *   <li>{@link ResourceNotFoundOrUnauthorizedException} → {@code 404} (IDOR-safe, no existence
 *       disclosure);
 *   <li>{@link AuthorizationDeniedException} → {@code 403} with its named {@code code};
 *   <li>Spring's method-security {@link AccessDeniedException} (coarse {@code @PreAuthorize} gate)
 *       → {@code 403} (no code);
 *   <li>anything else → {@code 500} with a generic safe message — <strong>never</strong> the
 *       exception detail or a stack trace (RISK-016).
 * </ul>
 *
 * Phase-3 domain-validation codes (e.g. {@code 409 EMPTY_PLAN_LOCK}) land with their slices.
 */
@RestControllerAdvice
public class ProblemDetailsExceptionHandler {

  @ExceptionHandler(ResourceNotFoundOrUnauthorizedException.class)
  ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundOrUnauthorizedException ex) {
    return render(HttpStatus.NOT_FOUND, "The requested resource was not found.", null);
  }

  @ExceptionHandler(AuthorizationDeniedException.class)
  ResponseEntity<ProblemDetail> handleDenied(AuthorizationDeniedException ex) {
    return render(
        HttpStatus.FORBIDDEN, "You are not permitted to perform this action.", ex.getCode());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
    return render(HttpStatus.FORBIDDEN, "Access denied.", null);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleFallback(Exception ex) {
    return render(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", null);
  }

  private ResponseEntity<ProblemDetail> render(HttpStatus status, String safeMessage, String code) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(ProblemDetailFactory.of(status, safeMessage, code));
  }
}
