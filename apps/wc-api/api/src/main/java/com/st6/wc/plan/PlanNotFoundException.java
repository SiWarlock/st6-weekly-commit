package com.st6.wc.plan;

/**
 * The caller's own current-week plan does not exist yet (task 3.3a, §5 E3). Rendered as a
 * <strong>named, coded</strong> {@code 404 PLAN_NOT_FOUND} by the 2.6 RFC-7807 advice — distinct
 * from the codeless IDOR {@code ResourceNotFoundOrUnauthorizedException}: E3 is self-scoped, so
 * this is the caller's <em>own</em> missing plan (no cross-resource existence disclosure). A GET
 * never creates a shell — plan-shell generation (3.2) owns shell creation.
 */
public class PlanNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** The named §5 error code rendered on the RFC-7807 body. */
  public static final String CODE = "PLAN_NOT_FOUND";

  public PlanNotFoundException() {
    super("No plan found for the current week.");
  }
}
