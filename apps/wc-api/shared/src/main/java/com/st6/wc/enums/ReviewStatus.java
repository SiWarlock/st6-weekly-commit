package com.st6.wc.enums;

/**
 * Stored manager-review status (Appendix B.1). {@code OVERDUE} is intentionally absent — it is
 * <em>derived</em> at read time (§3: {@code now > reviewDueAt AND NOT_REVIEWED}), never stored.
 */
public enum ReviewStatus {
  NOT_REVIEWED,
  REVIEWED_WITH_DISPUTES,
  REVIEWED
}
