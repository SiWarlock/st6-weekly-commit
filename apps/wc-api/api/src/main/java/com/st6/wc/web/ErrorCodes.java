package com.st6.wc.web;

/**
 * The §5 named error-code vocabulary rendered on RFC-7807 bodies (task 3.4a). A computed
 * response-layer constant set (like {@code AllowedAction}) — not a persisted {@code enums/} member.
 * Starts with the codes E5 needs; later slices add theirs ({@code LOCKED_BASELINE_EDIT} at 3.4b,
 * {@code EMPTY_PLAN_LOCK}/{@code UNLINKED_PLANNED_COMMITMENT} at 3.5, …). {@code
 * MANAGER_ROLE_REQUIRED} / {@code IC_CANNOT_RESOLVE_DISPUTE} already live as constants in the 2.5
 * authorizer.
 */
public final class ErrorCodes {

  private ErrorCodes() {}

  /** Server-side input-validation failure (400; carries {@code fieldErrors[]}). */
  public static final String VALIDATION_ERROR = "VALIDATION_ERROR";

  /** A lifecycle action attempted from an illegal source state (409). */
  public static final String ILLEGAL_STATE_TRANSITION = "ILLEGAL_STATE_TRANSITION";

  /** An edit of a frozen planned-baseline field after lock (409; RISK-002 / rule #2, task 3.4b). */
  public static final String LOCKED_BASELINE_EDIT = "LOCKED_BASELINE_EDIT";

  /** A lock attempted on a plan with no planned commitments (409; rule #1, task 3.5). */
  public static final String EMPTY_PLAN_LOCK = "EMPTY_PLAN_LOCK";

  /** A lock attempted with an unlinked planned commitment (409; rule #1 / REQ-E-001, task 3.5). */
  public static final String UNLINKED_PLANNED_COMMITMENT = "UNLINKED_PLANNED_COMMITMENT";

  /**
   * A close-reconciliation attempted while a commitment is incomplete — a PLANNED missing its
   * outcome, or an UNPLANNED missing its outcome or its Supporting-Outcome link (422; §5/B.21, task
   * 4.5). Carries {@code fieldErrors[]} naming each offending commitment + the per-violation
   * constraint.
   */
  public static final String UNPLANNED_MISSING_LINK_AT_CLOSE = "UNPLANNED_MISSING_LINK_AT_CLOSE";

  /**
   * A second unresolved dispute attempted on a commitment that already has an {@code OPEN}/{@code
   * IC_RESPONDED} dispute (409; safety rule #6, task 5.3). Enforced by the service pre-check + the
   * V2 partial-unique DB backstop.
   */
  public static final String SECOND_OPEN_DISPUTE = "SECOND_OPEN_DISPUTE";
}
