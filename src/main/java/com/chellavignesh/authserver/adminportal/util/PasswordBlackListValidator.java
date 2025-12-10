package com.chellavignesh.authserver.adminportal.util;

import com.chellavignesh.authserver.security.PasswordValidatorService;
import com.chellavignesh.authserver.security.exception.PasswordValidationException;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Base64;

public class PasswordBlackListValidator implements ConstraintValidator<ValidPasswordBlackListCheck, String> {

    private final PasswordValidatorService passwordValidatorService;

    // TODO: Some calls of this validator are made with a Base64 encoded value, and others are
    // made with a value that is not Base64 encoded. WIDP-462 will make things consistent.
    private boolean isBase64Encoded;

    @Autowired
    public PasswordBlackListValidator(PasswordValidatorService passwordValidatorService) {
        this.passwordValidatorService = passwordValidatorService;
    }

    @Override
    public void initialize(ValidPasswordBlackListCheck constraintAnnotation) {
        this.isBase64Encoded = constraintAnnotation.isBase64Encoded();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        try {
            String password = value != null
                    ? (isBase64Encoded ? new String(Base64.getDecoder().decode(value)) : value)
                    : null;

            return passwordValidatorService.passwordNotBlackListed(password);

        } catch (PasswordValidationException | IllegalArgumentException e) {
            return false;
        }
    }
}
