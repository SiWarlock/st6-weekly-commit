package com.st6.wc.web;

/**
 * A lifecycle action attempted from an illegal source state (task 3.4a, §3/§5) — e.g. creating or
 * deleting a commitment on a non-{@code DRAFT} plan, or editing {@code alignmentStatus} after lock.
 * Rendered as {@code 409 ILLEGAL_STATE_TRANSITION} by the 2.6 advice. Distinct from {@code
 * LOCKED_BASELINE_EDIT} (editing an existing frozen baseline field, 3.4b/Appendix E rule 2) — this
 * is a state precondition, not a baseline-field edit. The optional {@code constraint} discriminator
 * (e.g. {@code alignment_status_read_only_post_lock}, Appendix E rule 2) is surfaced as a {@code
 * constraint} property on the RFC-7807 body when present.
 */
public class IllegalStateTransitionException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String constraint;

  public IllegalStateTransitionException() {
    this(null);
  }

  public IllegalStateTransitionException(String constraint) {
    super("Illegal state transition.");
    this.constraint = constraint;
  }

  /** The specific constraint that was violated (Appendix E rule 2), or {@code null} if generic. */
  public String getConstraint() {
    return constraint;
  }
}
