package com.st6.wc.web;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Bean-Validation constraint rejecting C0/C1 control characters (task 3.4a, Appendix E Part 1) —
 * for <strong>single-line</strong> fields (title), where the spec rejects controls (vs description,
 * which strips them in {@link com.st6.wc.commitment.TextNormalizer}). Applied to the normalized
 * value, where whitespace controls are already collapsed to spaces, so only genuinely-stray
 * controls fail. Null is valid.
 */
@Documented
@Constraint(validatedBy = NoControlCharsValidator.class)
@Target({FIELD, METHOD, PARAMETER, CONSTRUCTOR, ANNOTATION_TYPE, RECORD_COMPONENT, TYPE_USE})
@Retention(RUNTIME)
public @interface NoControlChars {

  String message() default "must not contain control characters";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
