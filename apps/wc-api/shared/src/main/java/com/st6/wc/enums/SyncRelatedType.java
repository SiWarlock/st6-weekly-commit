package com.st6.wc.enums;

/**
 * Subject type of an Outlook sync record (Appendix B.1). The manager review block keys on {@code
 * MANAGER_REVIEW_WEEK} (§10 per-manager/week), not {@code MANAGER_REVIEW}.
 */
public enum SyncRelatedType {
  WEEKLY_PLAN,
  MANAGER_REVIEW_WEEK
}
