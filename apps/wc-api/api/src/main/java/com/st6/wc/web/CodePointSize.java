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
 * Bean-Validation constraint capping a string's length in <strong>Unicode code points</strong>
 * (task 3.4a, Appendix E Part 1) — NOT UTF-16 units like {@code @Size}, so a 255-code-point title
 * built from astral chars (surrogate pairs) is accepted. Applied to the <em>normalized</em> value
 * (the request record normalizes in its compact constructor), so the cap counts the stored form.
 * Null is valid (let {@code @NotBlank}/{@code @NotNull} handle absence).
 */
@Documented
@Constraint(validatedBy = CodePointSizeValidator.class)
@Target({FIELD, METHOD, PARAMETER, CONSTRUCTOR, ANNOTATION_TYPE, RECORD_COMPONENT, TYPE_USE})
@Retention(RUNTIME)
public @interface CodePointSize {

  int max();

  String message() default "must be at most {max} code points";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
