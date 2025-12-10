package com.chellavignesh.authserver.adminportal.util;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.reflect.InvocationTargetException;

public class ConfirmPasswordValidator implements ConstraintValidator<ValidConfirmPassword, Object> {
    private String message;

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        String password;
        String confirmPassword;
        boolean validation;

        try {
            if (value == null) {
                return false;
            }

            password = value.getClass().getMethod("getPassword").invoke(value).toString();
            confirmPassword = value.getClass().getMethod("getConfirmPassword").invoke(value).toString();

        } catch (NullPointerException _) {
            return true;
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        validation = confirmPassword.equals(password);

        if (!validation) {
            context.buildConstraintViolationWithTemplate(this.message)
                    .addPropertyNode("confirmPassword")
                    .addConstraintViolation();
        }

        return confirmPassword.equals(password);
    }

    @Override
    public void initialize(ValidConfirmPassword constraintAnnotation) {
        this.message = constraintAnnotation.message();
    }
}
