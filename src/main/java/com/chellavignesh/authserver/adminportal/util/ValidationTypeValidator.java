package com.chellavignesh.authserver.adminportal.util;

import com.chellavignesh.authserver.adminportal.user.entity.PasswordValidationType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidationTypeValidator implements ConstraintValidator<ValidValidationType, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {

        try {
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException("");
            }

            PasswordValidationType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return false;
        }

        return true;
    }
}
