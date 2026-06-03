package com.st6.wc.dispute.dto;

import com.st6.wc.commitment.TextNormalizer;
import com.st6.wc.enums.FlagType;
import com.st6.wc.web.CodePointSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * The E17 {@code POST /api/commitments/{id}/disputes} request (task 5.3, Appendix B.2 / Appendix E
 * Part 1). {@code flagType} required ({@code NEEDS_REVISION} | {@code MISALIGNED}; unknown → 400
 * via the {@code HttpMessageNotReadable} handler). {@code managerNote} required + ≤4000 code points
 * + ≥1 cp after normalize — normalized multi-line in the compact constructor
 * (NFC/trim/control-strip, blank → null), then {@code @NotBlank} on the normalized value rejects a
 * missing/blank note → 400 {@code VALIDATION_ERROR}. The note is stored RAW (React-escaped
 * downstream, §16) + never logged or audited (§15).
 */
public record OpenDisputeRequest(
    @NotNull FlagType flagType, @NotBlank @CodePointSize(max = 4000) String managerNote) {

  public OpenDisputeRequest {
    managerNote = TextNormalizer.normalizeMultiLine(managerNote);
  }
}
