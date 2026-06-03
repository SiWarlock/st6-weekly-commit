package com.st6.wc.commitment;

import java.text.Normalizer;

/**
 * The single user-text normalization util applied <strong>once at the DTO boundary</strong> (task
 * 3.4a, Appendix E Part 1, §16) — reused by every text field (title/description/notes/comments).
 *
 * <ul>
 *   <li><b>single-line</b> (title): NFC, strip lead/trail, collapse internal whitespace runs (incl.
 *       tabs/newlines) to a single space. Non-whitespace C0/C1 controls survive and are
 *       <em>rejected</em> by {@code @NoControlChars} ({@link #containsControlChars}).
 *   <li><b>multi-line</b> (description): NFC, preserve internal newlines/tabs, <em>strip</em> other
 *       C0/C1 controls, strip lead/trail; blank/whitespace-only → {@code null}.
 * </ul>
 *
 * <p>No HTML stripping (user text is stored RAW; React escapes on render — §16). {@link
 * #codePointCount} counts Unicode code points (not UTF-16 units) for the size caps. Null-safe.
 */
public final class TextNormalizer {

  private TextNormalizer() {}

  /** NFC + strip + collapse internal whitespace runs to a single space. Null in → null out. */
  public static String normalizeSingleLine(String value) {
    if (value == null) {
      return null;
    }
    return Normalizer.normalize(value, Normalizer.Form.NFC).strip().replaceAll("\\s+", " ");
  }

  /** NFC + strip C0/C1 controls except {@code \n}/{@code \t} + strip ends; blank → null. */
  public static String normalizeMultiLine(String value) {
    if (value == null) {
      return null;
    }
    String nfc = Normalizer.normalize(value, Normalizer.Form.NFC);
    StringBuilder sb = new StringBuilder(nfc.length());
    nfc.codePoints()
        .forEach(
            cp -> {
              if (cp == '\n' || cp == '\t' || !isControl(cp)) {
                sb.appendCodePoint(cp);
              }
            });
    String stripped = sb.toString().strip();
    return stripped.isEmpty() ? null : stripped;
  }

  /** Unicode code-point length (NOT UTF-16 {@code String.length()}). Null → 0. */
  public static int codePointCount(String value) {
    return value == null ? 0 : value.codePointCount(0, value.length());
  }

  /** True iff the value contains any C0 (U+0000–U+001F) or C1 (U+007F–U+009F) control char. */
  public static boolean containsControlChars(String value) {
    return value != null && value.codePoints().anyMatch(TextNormalizer::isControl);
  }

  private static boolean isControl(int cp) {
    return cp <= 0x1F || (cp >= 0x7F && cp <= 0x9F);
  }
}
