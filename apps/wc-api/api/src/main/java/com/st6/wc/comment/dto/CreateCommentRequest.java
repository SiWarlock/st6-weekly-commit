package com.st6.wc.comment.dto;

import com.st6.wc.commitment.TextNormalizer;
import com.st6.wc.enums.CommentTargetType;
import com.st6.wc.web.CodePointSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * The E21 {@code POST /api/comments} request (Appendix B.9 / Appendix E Part 1 / §16). The compact
 * constructor <strong>normalizes {@code body} once at the boundary</strong> via {@link
 * TextNormalizer#normalizeMultiLine} (NFC + strip control chars except {@code \n}/{@code \t} +
 * trim; blank → {@code null}), so the Bean-Validation constraints below validate the
 * <em>normalized</em> value: {@code body} required ({@code @NotBlank} — a blank/whitespace-only
 * body normalizes to {@code null} → {@code 400 VALIDATION_ERROR}) and ≤ 4000 code points
 * ({@code @CodePointSize}). The normalized {@code body} is stored RAW (React-escapes, §16) and
 * never logged/audited (§15). An unknown {@code targetType} fails JSON deserialization → {@code
 * 400} (never 500).
 */
public record CreateCommentRequest(
    @NotNull CommentTargetType targetType,
    @NotNull UUID targetId,
    @NotBlank @CodePointSize(max = 4000) String body) {

  public CreateCommentRequest {
    body = TextNormalizer.normalizeMultiLine(body);
  }
}
