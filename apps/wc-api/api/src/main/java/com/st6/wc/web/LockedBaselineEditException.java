package com.st6.wc.web;

/**
 * An edit of a frozen planned-baseline field on a {@code LOCKED}+ plan's commitment (task 3.4b, §3
 * RISK-002 / rule #2 / REQ-F-008). The planned baseline — {@code title}, {@code description},
 * {@code supportingOutcomeId}, {@code priority}, {@code workType}, {@code confidence}, {@code
 * commitmentKind} — is immutable once the plan locks; an attempt to change any of them is rendered
 * as {@code 409 LOCKED_BASELINE_EDIT} by the 2.6 advice. Distinct from {@link
 * IllegalStateTransitionException} ({@code alignmentStatus} read-only post-lock is a state
 * precondition, NOT a baseline-field edit — Appendix E rule 2).
 */
public class LockedBaselineEditException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public LockedBaselineEditException() {
    super("Locked baseline edit.");
  }
}
