package com.st6.wc.enums;

/** Outbox state of an Outlook sync record (Appendix B.1 / §10 outbox lifecycle). */
public enum SyncStatus {
  PENDING_PUBLISH,
  QUEUED,
  SYNCING,
  SYNCED,
  FAILED,
  RETRY_REQUESTED
}
