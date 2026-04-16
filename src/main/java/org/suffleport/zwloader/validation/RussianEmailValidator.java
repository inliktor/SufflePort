package org.suffleport.zwloader.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class RussianEmailValidator implements ConstraintValidator<RussianEmail, String> {

    private static final Pattern RUSSIAN_EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.(RU|SU|XN--P1AI|РФ)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return RUSSIAN_EMAIL_PATTERN.matcher(value.trim()).matches();
    }
}