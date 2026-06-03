package com.st6.wc.web;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.st6.wc.auth.AuthorizationDeniedException;
import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.plan.PlanNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The {@code @RestControllerAdvice} (task 2.6, extended 3.3a/3.4a, §5 / Appendix B.21 / §16) —
 * renders exceptions thrown <em>during controller invocation</em> as RFC-7807 {@code
 * application/problem+json}. (Chain-filter authn/authz exceptions never reach here; those are the
 * {@code ProblemDetailsAuthenticationEntryPoint} / {@code ProblemDetailsAccessDeniedHandler}.)
 *
 * <ul>
 *   <li>{@link ResourceNotFoundOrUnauthorizedException} → {@code 404} (IDOR-safe, codeless);
 *   <li>{@link PlanNotFoundException} → {@code 404} with the named {@code PLAN_NOT_FOUND} (E3
 *       self-scoped);
 *   <li>{@link AuthorizationDeniedException} → {@code 403} with its named {@code code};
 *   <li>{@link MethodArgumentNotValidException} / {@link HttpMessageNotReadableException} / {@link
 *       ValidationException} → {@code 400 VALIDATION_ERROR} with {@code fieldErrors[]} (Appendix E
 *       / §16); the unreadable path covers an unknown enum value (→ 400, never 500) and extracts
 *       ONLY the safe field NAME, never the offending value (§15);
 *   <li>{@link IllegalStateTransitionException} → {@code 409 ILLEGAL_STATE_TRANSITION};
 *   <li>anything else → {@code 500} with a generic safe message (never the exception detail).
 * </ul>
 */
@RestControllerAdvice
public class ProblemDetailsExceptionHandler {

  @ExceptionHandler(ResourceNotFoundOrUnauthorizedException.class)
  ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundOrUnauthorizedException ex) {
    return render(HttpStatus.NOT_FOUND, "The requested resource was not found.", null);
  }

  @ExceptionHandler(PlanNotFoundException.class)
  ResponseEntity<ProblemDetail> handlePlanNotFound(PlanNotFoundException ex) {
    // self-scoped (E3): the caller's own week has no plan yet — a NAMED 404 (no existence leak).
    return render(
        HttpStatus.NOT_FOUND, "No plan found for the current week.", PlanNotFoundException.CODE);
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

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> handleBeanValidation(MethodArgumentNotValidException ex) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
      fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
    }
    return renderValidation(fieldErrors);
  }

  @ExceptionHandler(ValidationException.class)
  ResponseEntity<ProblemDetail> handleDomainValidation(ValidationException ex) {
    return renderValidation(ex.fieldErrors());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex) {
    // Unknown enum / malformed JSON → 400 VALIDATION_ERROR (never 500). Surface ONLY the field
    // NAME from the Jackson path — never the offending value (rule #7 / §15: no untrusted echo).
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    if (ex.getCause() instanceof InvalidFormatException ife && !ife.getPath().isEmpty()) {
      String field = ife.getPath().get(ife.getPath().size() - 1).getFieldName();
      if (field != null) {
        fieldErrors.put(field, "invalid value");
      }
    }
    return renderValidation(fieldErrors);
  }

  @ExceptionHandler(IllegalStateTransitionException.class)
  ResponseEntity<ProblemDetail> handleIllegalState(IllegalStateTransitionException ex) {
    return render(
        HttpStatus.CONFLICT,
        "The action is not allowed from the current state.",
        ErrorCodes.ILLEGAL_STATE_TRANSITION);
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

  private ResponseEntity<ProblemDetail> renderValidation(Map<String, String> fieldErrors) {
    ProblemDetail body =
        ProblemDetailFactory.of(
            HttpStatus.BAD_REQUEST, "Validation failed.", ErrorCodes.VALIDATION_ERROR);
    body.setProperty("fieldErrors", fieldErrors);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(body);
  }
}
