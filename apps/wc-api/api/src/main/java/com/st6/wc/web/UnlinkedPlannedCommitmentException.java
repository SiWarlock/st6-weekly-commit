package com.st6.wc.web;

import java.util.Map;

/**
 * A lock attempted while at least one PLANNED commitment is not linked to a Supporting Outcome
 * (task 3.5, safety rule #1 / §5 B.21 / REQ-E-001 — the demo's headline gate). Rendered as {@code
 * 409 UNLINKED_PLANNED_COMMITMENT} with {@code fieldErrors[]} naming each unlinked commitment's
 * {@code supportingOutcomeId} (B.21). The messages are safe constants (never echo user input —
 * §15).
 */
public class UnlinkedPlannedCommitmentException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient Map<String, String> fieldErrors;

  public UnlinkedPlannedCommitmentException(Map<String, String> fieldErrors) {
    super("Every planned commitment must link a Supporting Outcome before lock.");
    this.fieldErrors = Map.copyOf(fieldErrors);
  }

  public Map<String, String> fieldErrors() {
    return fieldErrors;
  }
}
