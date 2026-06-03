package com.st6.wc.web;

/**
 * A second unresolved alignment dispute attempted on a commitment that already has an {@code OPEN}
 * or {@code IC_RESPONDED} dispute (task 5.3, §3 / safety rule #6 — at most one unresolved dispute
 * per commitment). Rendered as {@code 409 SECOND_OPEN_DISPUTE} by the 2.6 advice. Thrown by the
 * service pre-check ({@code findByCommitmentIdAndStatusIn}) and by the DB-backstop catch on the
 * partial-unique {@code uq_one_unresolved_dispute_per_commitment} (a concurrent race).
 */
public class SecondOpenDisputeException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public SecondOpenDisputeException() {
    super("A second unresolved dispute is not allowed on this commitment.");
  }
}
