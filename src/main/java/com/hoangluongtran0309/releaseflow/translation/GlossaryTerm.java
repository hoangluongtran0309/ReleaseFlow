package com.hoangluongtran0309.releaseflow.translation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A glossary term: 1 to {@value GlossaryService#MAX_TERM_LENGTH} characters on one line,
 * with no control characters, which keeps it far below DeepL's 1024 bytes per phrase and
 * out of the separators of the dictionary it is sent in.
 */
@Documented
@Constraint(validatedBy = {})
@NotBlank(message = "{validation.glossaryTerm.required}")
@Size(max = GlossaryService.MAX_TERM_LENGTH, message = "{validation.glossaryTerm.tooLong}")
@Pattern(regexp = "[^\\p{Cntrl}\\u0085\\u2028\\u2029]*", message = "{validation.glossaryTerm.oneLine}")
@Target({ElementType.FIELD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@interface GlossaryTerm {

    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
