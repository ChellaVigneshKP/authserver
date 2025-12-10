package com.chellavignesh.authserver.adminportal.util;

import com.chellavignesh.authserver.security.PasswordValidatorService;
import com.chellavignesh.authserver.security.exception.PasswordValidationException;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Base64;

public class PasswordSyntaxValidator implements ConstraintValidator<ValidPasswordSyntax, String> {

    @Autowired
    private final PasswordValidatorService passwordValidatorService;

    // TODO: Some calls of this validator are made with a Base64 encoded value,
    // and others are made with a value that is not Base64 encoded. WIIDP-462 will make things consistent.
    private boolean isBase64Encoded;

    public PasswordSyntaxValidator(PasswordValidatorService passwordValidatorService) {
        this.passwordValidatorService = passwordValidatorService;
    }

    @Override
    public void initialize(ValidPasswordSyntax constraintAnnotation) {
        this.isBase64Encoded = constraintAnnotation.isBase64Encoded();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {

        try {
            String password = value != null
                    ? (isBase64Encoded ? new String(Base64.getDecoder().decode(value)) : value)
                    : null;

            return passwordValidatorService.validatePasswordBasicRegexp(password);

        } catch (PasswordValidationException | IllegalArgumentException e) {
            return false;
        }
    }
}
