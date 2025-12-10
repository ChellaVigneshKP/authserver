package com.chellavignesh.authserver.adminportal.util;

import com.chellavignesh.authserver.security.PasswordValidatorService;
import com.chellavignesh.authserver.security.exception.PasswordValidationException;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.reflect.InvocationTargetException;

public class PasswordSemanticValidator implements ConstraintValidator<ValidPasswordSemantic, Object> {

    private String message;

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {

        String firstName = "", lastName = "", username = "", password = "", email = "";
        boolean isValid;
        context.disableDefaultConstraintViolation();

        try {
            firstName = value.getClass().getMethod("getFirstName").invoke(value).toString();
            lastName = value.getClass().getMethod("getLastName").invoke(value).toString();
            username = value.getClass().getMethod("getUsername").invoke(value).toString();
            password = value.getClass().getMethod("getPassword").invoke(value).toString();
            email = value.getClass().getMethod("getEmail").invoke(value).toString();

            isValid = PasswordValidatorService.validSemantic(password, firstName, lastName, username, email);
        } catch (NullPointerException e) {
            return true; // return true, abort execution, not all variables are set, pass validation responsibility to other validator
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (PasswordValidationException e) {
            isValid = false;
        }

        if (!isValid) {
            context.buildConstraintViolationWithTemplate(this.message)
                    .addPropertyNode("password")
                    .addConstraintViolation();
        }

        return isValid;
    }

    @Override
    public void initialize(ValidPasswordSemantic constraintAnnotation) {
        this.message = constraintAnnotation.message();
    }
}
