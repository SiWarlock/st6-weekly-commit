package com.st6.wc.web;

import com.st6.wc.commitment.TextNormalizer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates {@link NoControlChars}: rejects any C0/C1 control char ({@link
 * TextNormalizer#containsControlChars}).
 */
public class NoControlCharsValidator implements ConstraintValidator<NoControlChars, String> {

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return !TextNormalizer.containsControlChars(value);
  }
}
