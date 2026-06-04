package com.st6.wc.dispute.dto;

import com.st6.wc.commitment.TextNormalizer;
import com.st6.wc.web.CodePointSize;
import java.util.UUID;

/**
 * The E18 {@code POST /api/disputes/{id}/respond} request (task 5.4, Appendix B.2 / Appendix E Part
 * 1). The IC responds to an alignment dispute by adding rationale and/or revising the disputed
 * commitment's Supporting Outcome — <strong>at least one of</strong> {@code icResponse} /{@code
 * supportingOutcomeId} is required (both absent → {@code 400 VALIDATION_ERROR}, enforced
 * service-side as a cross-field rule). {@code icResponse} ≤4000 code points, normalized multi-line
 * (NFC/trim/control-strip, blank → null); it is stored RAW (React-escaped downstream, §16) + never
 * logged or audited (§15). {@code supportingOutcomeId} (when provided) must be a known SO (unknown
 * → 400) and revises ONLY the commitment's SO link (the rule-#2 dispute-gated exception).
 */
public record RespondDisputeRequest(
    @CodePointSize(max = 4000) String icResponse, UUID supportingOutcomeId) {

  public RespondDisputeRequest {
    icResponse = TextNormalizer.normalizeMultiLine(icResponse);
  }
}
