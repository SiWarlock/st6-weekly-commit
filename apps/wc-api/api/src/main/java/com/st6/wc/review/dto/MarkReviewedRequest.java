package com.st6.wc.review.dto;

import com.st6.wc.commitment.TextNormalizer;
import com.st6.wc.web.CodePointSize;

/**
 * The E16 {@code POST /api/manager/reviews/{reviewId}/mark-reviewed} request body (task 5.2,
 * Appendix B.7 / Appendix E Part 1). The whole body is <strong>optional</strong> (the controller
 * declares {@code required = false}); when present it carries only an optional {@code summaryNote}
 * (≤4000 code points, normalized multi-line + blank → null). The review <em>status</em> is never
 * client-supplied — it is server-derived from the unresolved-dispute count (B.7 / §3).
 */
public record MarkReviewedRequest(@CodePointSize(max = 4000) String summaryNote) {

  public MarkReviewedRequest {
    summaryNote = TextNormalizer.normalizeMultiLine(summaryNote);
  }
}
