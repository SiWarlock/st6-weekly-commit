package com.st6.wc.web;

/**
 * A manual Outlook-sync retry (E23) attempted on a record whose status is not {@code FAILED} — only
 * a {@code FAILED} record is the retryable terminal (§10). Rendered as {@code 409
 * SYNC_NOT_RETRYABLE} by the 2.6 advice. Thrown by {@code SyncRetryService} when {@code
 * AllowedActionResolver.canRetrySync} is false (the same precondition the {@code RETRY_SYNC}
 * affordance gates on — affordance↔enforcement single-source).
 */
public class SyncNotRetryableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public SyncNotRetryableException() {
    super("This sync record is not in a retryable state.");
  }
}
