package com.chellavignesh.authserver.adminportal.util;

import com.chellavignesh.authserver.security.PasswordValidatorService;
import com.chellavignesh.authserver.security.exception.PasswordValidationConditionsNotMetException;
import com.chellavignesh.authserver.security.exception.PasswordValidationException;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

public class PasswordUsagesValidator implements ConstraintValidator<ValidPasswordPreviousUsages, Object> {

    @Autowired
    private final PasswordValidatorService passwordValidatorService;
    private String message;

    public PasswordUsagesValidator(PasswordValidatorService passwordValidatorService) {
        this.passwordValidatorService = passwordValidatorService;
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {

        Object userGuid;
        String password;
        boolean validation;

        try {
            userGuid = value.getClass().getMethod("getUserGuid").invoke(value);
            password = value.getClass().getMethod("getPassword").invoke(value).toString();

            if (userGuid == null || password == null) {
                throw new PasswordValidationConditionsNotMetException("Missing required parameters to proceed with validation");
            }

            validation = passwordValidatorService.validateRecentUsages(password, (UUID) userGuid);
        }

        catch (NullPointerException | PasswordValidationConditionsNotMetException e) {
            validation = true;
        }

        catch (PasswordValidationException e) {
            validation = false;
            this.message = e.getMessage();
        }

        catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        if (!validation) {
            context.buildConstraintViolationWithTemplate(this.message)
                    .addPropertyNode("password")
                    .addConstraintViolation();
        }

        return validation;
    }

    @Override
    public void initialize(ValidPasswordPreviousUsages constraintAnnotation) {
        this.message = constraintAnnotation.message();
    }
}
