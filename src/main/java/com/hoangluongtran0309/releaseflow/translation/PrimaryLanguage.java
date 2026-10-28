package com.hoangluongtran0309.releaseflow.translation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Locale;
import java.util.Set;

/** An ISO 639 primary language code, such as {@code en} or {@code vi}. */
@Documented
@Constraint(validatedBy = PrimaryLanguage.Validator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@interface PrimaryLanguage {

    String message() default "{validation.glossaryLanguage.invalid}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<PrimaryLanguage, String> {

        private static final Set<String> ISO_LANGUAGES = Set.of(Locale.getISOLanguages());

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value != null && ISO_LANGUAGES.contains(value);
        }
    }
}
