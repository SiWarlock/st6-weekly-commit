package com.st6.wc.web;

import com.st6.wc.commitment.TextNormalizer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates {@link CodePointSize}: delegates to {@link TextNormalizer#codePointCount} so the cap is
 * counted in Unicode code points (not UTF-16 units). Null is valid (absence is {@code @NotBlank}/
 * {@code @NotNull}'s job).
 */
public class CodePointSizeValidator implements ConstraintValidator<CodePointSize, String> {

  private int max;

  @Override
  public void initialize(CodePointSize constraint) {
    this.max = constraint.max();
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return value == null || TextNormalizer.codePointCount(value) <= max;
  }
}
