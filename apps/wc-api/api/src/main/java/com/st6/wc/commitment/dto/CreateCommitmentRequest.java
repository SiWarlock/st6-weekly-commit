package com.st6.wc.commitment.dto;

import com.st6.wc.commitment.TextNormalizer;
import com.st6.wc.enums.AlignmentStatus;
import com.st6.wc.enums.Confidence;
import com.st6.wc.enums.Priority;
import com.st6.wc.enums.WorkType;
import com.st6.wc.web.CodePointSize;
import com.st6.wc.web.NoControlChars;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * The E5 {@code POST /api/plans/{id}/commitments} request (task 3.4a, Appendix B.6 / Appendix E
 * Part 1). The compact constructor <strong>normalizes once at the DTO boundary</strong> via {@link
 * TextNormalizer} (title strip+collapse+NFC; description strip+NFC+control-strip, blank → null) and
 * defaults {@code alignmentStatus} to {@code NEEDS_REVIEW} — so the Bean-Validation constraints
 * below validate the <em>normalized</em> value. {@code title} required (≤255 code points, no
 * control chars); {@code description} optional (≤4000 code points). Enum fields are required; an
 * unknown enum value fails JSON deserialization → 400 (never 500) via the {@code
 * HttpMessageNotReadable} handler. {@code supportingOutcomeId} optional at create (lock-time
 * linkage is 3.5).
 */
public record CreateCommitmentRequest(
    @NotBlank @NoControlChars @CodePointSize(max = 255) String title,
    @CodePointSize(max = 4000) String description,
    UUID supportingOutcomeId,
    @NotNull Priority priority,
    @NotNull WorkType workType,
    @NotNull Confidence confidence,
    AlignmentStatus alignmentStatus) {

  public CreateCommitmentRequest {
    title = TextNormalizer.normalizeSingleLine(title);
    description = TextNormalizer.normalizeMultiLine(description);
    alignmentStatus = alignmentStatus == null ? AlignmentStatus.NEEDS_REVIEW : alignmentStatus;
  }
}
