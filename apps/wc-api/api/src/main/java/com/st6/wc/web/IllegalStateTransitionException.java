package com.st6.wc.web;

/**
 * A lifecycle action attempted from an illegal source state (task 3.4a, §3/§5) — e.g. creating a
 * commitment on a non-{@code DRAFT} plan. Rendered as {@code 409 ILLEGAL_STATE_TRANSITION} by the
 * 2.6 advice. Distinct from {@code LOCKED_BASELINE_EDIT} (editing an existing frozen baseline
 * field, 3.4b/Appendix E rule 2) — this is a state precondition, not a baseline-field edit.
 */
public class IllegalStateTransitionException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public IllegalStateTransitionException() {
    super("Illegal state transition.");
  }
}
