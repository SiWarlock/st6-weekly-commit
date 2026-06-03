package com.st6.wc.web;

import java.util.Map;

/**
 * A service-layer input-validation failure (task 3.4a, §5/§16) — for business rules Bean Validation
 * can't express on the request alone (e.g. {@code workType=UNPLANNED} on the planned-only E5, or an
 * unknown {@code supportingOutcomeId}). Rendered as {@code 400 VALIDATION_ERROR} with {@code
 * fieldErrors[]} by the 2.6 advice — uniform with the {@code @Valid} path. Messages are safe
 * constants (never echo untrusted input — §15).
 */
public class ValidationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient Map<String, String> fieldErrors;

  public ValidationException(Map<String, String> fieldErrors) {
    super("Validation failed");
    this.fieldErrors = Map.copyOf(fieldErrors);
  }

  /** Convenience for a single field → safe-message error. */
  public static ValidationException field(String field, String message) {
    return new ValidationException(Map.of(field, message));
  }

  public Map<String, String> fieldErrors() {
    return fieldErrors;
  }
}
