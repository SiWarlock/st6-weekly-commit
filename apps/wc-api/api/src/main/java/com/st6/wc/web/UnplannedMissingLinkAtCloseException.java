package com.st6.wc.web;

import java.util.Map;

/**
 * A close-reconciliation (E10, task 4.5) attempted while at least one commitment is incomplete: a
 * PLANNED commitment missing its {@code reconciliationOutcome}, or an UNPLANNED commitment missing
 * its outcome or its Supporting-Outcome link (REQ-F-026/029, §5/B.21). Rendered as {@code 422
 * UNPLANNED_MISSING_LINK_AT_CLOSE} with {@code fieldErrors[]} keyed by {@code
 * commitments[<id>].<field>} → a per-violation constraint ({@code planned_missing_outcome} / {@code
 * unplanned_missing_outcome} / {@code unplanned_missing_supporting_outcome}). The field-path key
 * (vs a bare commitment id) lets one commitment carry two distinct violations without collision.
 * The messages are safe constants (never echo user input — §15). Mirrors {@link
 * UnlinkedPlannedCommitmentException} (lock-time, 409), but close is the 422 close gate.
 */
public class UnplannedMissingLinkAtCloseException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient Map<String, String> fieldErrors;

  public UnplannedMissingLinkAtCloseException(Map<String, String> fieldErrors) {
    super(
        "Every commitment must have an outcome (and every unplanned a Supporting Outcome) to close.");
    this.fieldErrors = Map.copyOf(fieldErrors);
  }

  public Map<String, String> fieldErrors() {
    return fieldErrors;
  }
}
