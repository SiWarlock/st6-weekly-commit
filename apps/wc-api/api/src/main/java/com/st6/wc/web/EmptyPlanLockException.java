package com.st6.wc.web;

/**
 * A lock attempted on a plan with no planned commitments (task 3.5, §3 / §5 B.21 / REQ-E-001).
 * Rendered as {@code 409 EMPTY_PLAN_LOCK} by the 2.6 advice. Distinct from {@link
 * UnlinkedPlannedCommitmentException} (the plan has commitments, but one is unlinked).
 */
public class EmptyPlanLockException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public EmptyPlanLockException() {
    super("A plan must have at least one planned commitment to lock.");
  }
}
