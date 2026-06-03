package com.st6.wc.commitment;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import org.junit.jupiter.api.Test;

/**
 * Pure-function proof of {@link TextNormalizer} (task 3.4a, Appendix E Part 1, §16). The single
 * normalize-at-the-DTO-boundary util every user-text field reuses: single-line (title) = strip +
 * collapse internal whitespace runs to one space + NFC; multi-line (description/notes) = strip +
 * NFC + preserve internal newlines, blank to {@code null}. No HTML stripping (raw store; React
 * escapes on render). Null-safe.
 */
class TextNormalizerTest {

  @Test
  void singleLine_stripsCollapsesAndNfcNormalizes() {
    assertThat(TextNormalizer.normalizeSingleLine("  hello   world  ")).isEqualTo("hello world");
    // tabs + newlines are internal whitespace, collapsed to a single space (single-line title)
    assertThat(TextNormalizer.normalizeSingleLine("a\t\n b")).isEqualTo("a b");
    assertThat(TextNormalizer.normalizeSingleLine(null)).isNull();
    assertThat(TextNormalizer.normalizeSingleLine("   ")).isEmpty(); // empty, @NotBlank catches it
  }

  @Test
  void singleLine_nfcComposesDecomposed() {
    String decomposed = "é"; // e + combining acute accent
    String result = TextNormalizer.normalizeSingleLine(decomposed);
    assertThat(Normalizer.isNormalized(result, Normalizer.Form.NFC)).isTrue();
    assertThat(result).isEqualTo("é"); // NFC composed, single code point
  }

  @Test
  void multiLine_stripsPreservesNewlinesBlankToNull() {
    assertThat(TextNormalizer.normalizeMultiLine("  line1\n\nline2  ")).isEqualTo("line1\n\nline2");
    assertThat(TextNormalizer.normalizeMultiLine("   ")).isNull(); // whitespace-only to NULL
    assertThat(TextNormalizer.normalizeMultiLine("")).isNull();
    assertThat(TextNormalizer.normalizeMultiLine(null)).isNull();
  }

  @Test
  void multiLine_storesRawNoHtmlStrip() {
    String xss = "<img src=x onerror=alert(1)>";
    assertThat(TextNormalizer.normalizeMultiLine(xss)).isEqualTo(xss); // verbatim, no strip (§16)
  }

  // --- Appendix E: description STRIPS C0/C1 controls except newline/tab (keeps \n and \t) ----
  @Test
  void multiLine_stripsControlCharsExceptNewlineAndTab() {
    assertThat(TextNormalizer.normalizeMultiLine("ab\nc\td")).isEqualTo("ab\nc\td");
  }

  // --- Appendix E: title REJECTS C0/C1 controls — the @NoControlChars constraint delegates here
  // ----
  @Test
  void containsControlChars_detectsC0C1() {
    assertThat(TextNormalizer.containsControlChars("hello world")).isFalse();
    assertThat(TextNormalizer.containsControlChars("ab")).isTrue(); // C0 control
    assertThat(TextNormalizer.containsControlChars(null)).isFalse();
  }

  // --- code-point counting (NOT UTF-16) — the @CodePointSize constraint delegates here ----
  @Test
  void codePointCount_countsCodePointsNotUtf16Units() {
    assertThat(TextNormalizer.codePointCount("hello")).isEqualTo(5);
    assertThat(TextNormalizer.codePointCount(null)).isZero();
    // 255 astral chars = 510 UTF-16 units but 255 CODE POINTS (the cap counts these, not units)
    String astral255 = "🚀".repeat(255);
    assertThat(astral255.length()).isEqualTo(510); // UTF-16 units
    assertThat(TextNormalizer.codePointCount(astral255)).isEqualTo(255); // code points
  }
}
