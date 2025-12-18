package com.chellavignesh.authserver.adminportal.user.dto.validator;


import com.chellavignesh.authserver.adminportal.user.dto.ChangeProfileDto;
import com.chellavignesh.authserver.config.OutputMessagesConstants;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.util.regex.Pattern;

@Component
public class ChangeProfileDtoValidator implements Validator {

    private final String emailRegexPattern;
    private final String phoneNumberRegexPattern;

    public ChangeProfileDtoValidator(@Value("${validation.email.regexp}") String emailRegexPattern, @Value("${validation.phone.regexp}") String phoneNumberRegexPattern) {
        this.emailRegexPattern = emailRegexPattern;
        this.phoneNumberRegexPattern = phoneNumberRegexPattern;
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return clazz.equals(ChangeProfileDto.class);
    }

    @Override
    public void validate(@NonNull Object target, @NonNull Errors errors) {
        if (target instanceof ChangeProfileDto dto) {

            // Email validation
            if (StringUtils.isEmpty(dto.getEmail())) {
                errors.rejectValue("email", "Invalid", OutputMessagesConstants.REQUIRED_EMAIL);
            } else if (!Pattern.compile(emailRegexPattern).matcher(dto.getEmail()).matches()) {

                errors.rejectValue("email", "Invalid", OutputMessagesConstants.INVALID_EMAIL_SYNTAX);
            }

            // Confirm email validation
            if (StringUtils.isEmpty(dto.getConfirmEmail())) {
                errors.rejectValue("confirmEmail", "Invalid", OutputMessagesConstants.REQUIRED_EMAIL);
            } else if (!dto.getConfirmEmail().equals(dto.getEmail())) {
                errors.rejectValue("confirmEmail", "Invalid", "Emails do not match.");
            }

            // Phone number validation
            if (StringUtils.isEmpty(dto.getPhoneNumber())) {
                errors.rejectValue("phoneNumber", "Invalid", OutputMessagesConstants.REQUIRED_PHONE_NUMBER);
            } else if (!Pattern.compile(phoneNumberRegexPattern).matcher(dto.getPhoneNumber()).matches()) {

                errors.rejectValue("phoneNumber", "Invalid", OutputMessagesConstants.INVALID_PHONE_SYNTAX);
            }

            // Secondary phone number validation (optional)
            if (StringUtils.isNotEmpty(dto.getSecondaryPhoneNumber()) && !Pattern.compile(phoneNumberRegexPattern).matcher(dto.getSecondaryPhoneNumber()).matches()) {

                errors.rejectValue("secondaryPhoneNumber", "Invalid", OutputMessagesConstants.INVALID_PHONE_SYNTAX);
            }
        }
    }
}
