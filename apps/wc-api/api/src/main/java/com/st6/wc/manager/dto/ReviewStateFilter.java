package com.st6.wc.manager.dto;

/**
 * The E13 {@code reviewState} query vocabulary (task 6.5a, REQ-F-023) — a <strong>computed filter
 * vocab</strong>, NOT a persisted {@code enums/} member (so it stays out of {@code
 * EnumVocabularyTest}'s 16-enum pin, like {@code AllowedAction}). The three stored statuses map 1:1
 * to {@code ReviewStatus}; {@code OVERDUE} is the derived filter on {@code is_review_overdue=true}
 * (there is no stored {@code OVERDUE} status — rule #6 / §9). A bad value fails binding → {@code
 * 400 VALIDATION_ERROR} via the type-mismatch handler.
 */
public enum ReviewStateFilter {
  NOT_REVIEWED,
  REVIEWED_WITH_DISPUTES,
  REVIEWED,
  OVERDUE
}
