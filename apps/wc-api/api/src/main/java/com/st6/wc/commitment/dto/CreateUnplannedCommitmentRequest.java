package com.st6.wc.commitment.dto;

import com.st6.wc.commitment.TextNormalizer;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.Priority;
import com.st6.wc.web.CodePointSize;
import com.st6.wc.web.NoControlChars;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * The E11 {@code POST /api/plans/{id}/unplanned-commitments} request (task 4.3, Appendix B.6 /
 * Appendix E Part 1) — the <strong>inverse of {@link CreateCommitmentRequest}</strong> (E5). Same
 * normalize-once-at-the-boundary discipline (title strip+collapse+NFC; description
 * strip+NFC+control-strip, blank → null; {@code alignmentStatus} defaults to {@code NEEDS_REVIEW}),
 * but <strong>{@code workType} and {@code commitmentKind} are deliberately ABSENT</strong>: they
 * are server-owned and always forced to {@code UNPLANNED} by {@code
 * CommitmentService.createUnplanned} (a client value can't conflict — unknown JSON properties are
 * ignored). {@code title} required (≤255 code points, no control chars); {@code description}
 * optional (≤4000 code points). {@code supportingOutcomeId} is optional at create (REQ-F-026 — the
 * link is enforced only at close, §4.5); a provided value is resolved/validated in the service
 * (unknown → 400). An unknown enum value fails JSON deserialization → 400 via the {@code
 * HttpMessageNotReadable} handler.
 */
public record CreateUnplannedCommitmentRequest(
    @NotBlank @NoControlChars @CodePointSize(max = 255) String title,
    @CodePointSize(max = 4000) String description,
    UUID supportingOutcomeId,
    @NotNull Priority priority,
    @NotNull Confidence confidence,
    AlignmentStatus alignmentStatus) {

  public CreateUnplannedCommitmentRequest {
    title = TextNormalizer.normalizeSingleLine(title);
    description = TextNormalizer.normalizeMultiLine(description);
    alignmentStatus = alignmentStatus == null ? AlignmentStatus.NEEDS_REVIEW : alignmentStatus;
  }
}
