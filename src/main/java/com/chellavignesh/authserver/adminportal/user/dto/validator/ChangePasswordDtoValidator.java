package com.chellavignesh.authserver.adminportal.user.dto.validator;

import com.chellavignesh.authserver.adminportal.user.dto.ChangePasswordDto;
import com.chellavignesh.authserver.security.PasswordValidatorService;
import com.chellavignesh.authserver.security.exception.PasswordValidationException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class ChangePasswordDtoValidator implements Validator {

    private static final String INCORRECT_PASSWORD = "Password must be a minimun of 8 characters long and include 3 of the following: lower characters, upper characters, special characters or numbers.";
    private static final String REQUIRED_PASSWORD = "Password is required.";
    private static final String INVALID_CONFIRM_PASSWORD = "Entered password values do not match.";
    private static final String INVALID_CURRENT_PASSWORD = "Current password is invalid.";

    private final PasswordValidatorService passwordValidatorService;

    public ChangePasswordDtoValidator(PasswordValidatorService passwordValidatorService) {
        this.passwordValidatorService = passwordValidatorService;
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return clazz.equals(ChangePasswordDto.class);
    }

    @Override
    public void validate(Object target, Errors errors) {

        if (target instanceof ChangePasswordDto dto) {

            if (StringUtils.isEmpty(dto.getCurrentPassword())) {
                errors.rejectValue("currentPassword", "Missing", REQUIRED_PASSWORD);
            }

            if (StringUtils.isEmpty(dto.getNewPassword())) {
                errors.rejectValue("newPassword", "Missing", REQUIRED_PASSWORD);
            }

            if (StringUtils.isEmpty(dto.getConfirmPassword())) {
                errors.rejectValue("confirmPassword", "Missing", REQUIRED_PASSWORD);
            }

            try {
                passwordValidatorService.validatePassword(dto.getNewPassword());
            } catch (PasswordValidationException e) {
                errors.rejectValue("newPassword", "Invalid", e.getMessage());
            }

            if (!StringUtils.isEmpty(dto.getNewPassword())
                    && !StringUtils.isEmpty(dto.getConfirmPassword())
                    && !dto.getNewPassword().equals(dto.getConfirmPassword())) {

                errors.rejectValue("confirmPassword", "Mismatch", INVALID_CONFIRM_PASSWORD);
            }
        }
    }
}
