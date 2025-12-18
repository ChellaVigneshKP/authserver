package com.chellavignesh.authserver.adminportal.user.dto.validator;

import com.chellavignesh.authserver.adminportal.user.dto.*;
import com.chellavignesh.authserver.config.OutputMessagesConstants;
import com.chellavignesh.authserver.enums.entity.SuffixType;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.util.regex.Pattern;

@Component
public class UserDtoValidator implements Validator {

    private final String emailRegexPattern;

    private static final String phoneNumberRegexPattern = "^\\+[1-9]\\d{1,14}$";

    private static final String REQUIRED_CIPHER_INDEX = "Cipher index is required.";
    private static final String EMPTY_OR_BLANK_BRANDING_ID = "At least one of 'brandingIds' is empty or blank.";
    public static final String REQUIRED_REDIRECT_URI = "redirect_uri parameter is required";

    public UserDtoValidator(@Value("${validation.email.regexp}") String emailRegexPattern) {
        this.emailRegexPattern = emailRegexPattern;
    }

    @Override
    public boolean supports(@NonNull Class<?> clazz) {
        return CreateUserProfileDto.class.isAssignableFrom(clazz) || UpdateUserDto.class.isAssignableFrom(clazz) || UpdateUserEmailDto.class.isAssignableFrom(clazz) || UpdateUserSecuritySettingsDto.class.isAssignableFrom(clazz) || UpdateUserMetadataDto.class.isAssignableFrom(clazz) || UpdateUserStatusDto.class.isAssignableFrom(clazz) || BrandingDto.class.isAssignableFrom(clazz)  || String.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, @NonNull Errors errors) {

        switch (target.getClass().getSimpleName()) {

            case "CreateUserProfileDto":
                validateCreateUserProfileDto((CreateUserProfileDto) target, errors);
                break;

            case "UpdateUserDto":
                validateUpdateUserDto((UpdateUserDto) target, errors);
                break;

            case "UpdateUserEmailDto":
                validateUpdateUserEmailDto((UpdateUserEmailDto) target, errors);
                break;

            case "UpdateUserSecuritySettingsDto":
                validateUpdateUserSecuritySettingsDto((UpdateUserSecuritySettingsDto) target, errors);
                break;

            case "UpdateUserMetadataDto":
                validateUpdateUserMetadataDto((UpdateUserMetadataDto) target, errors);
                break;

            case "BrandingDto":
                validateBrandingDto((BrandingDto) target, errors);
                break;

            case "ChangeProfileViewDto":
                validateChangeProfileViewDto((ChangeProfileViewDto) target, errors);
                break;

            case "UpdateUserStatusDto":
                break;

            default:
                throw new IllegalArgumentException("Unsupported DTO type: " + target.getClass().getSimpleName());
        }
    }

    /* ---------------------------------------------------
       CREATE USER
     --------------------------------------------------- */

    private void validateCreateUserProfileDto(CreateUserProfileDto dto, Errors errors) {

        if (StringUtils.isEmpty(dto.getBranding())) {
            errors.rejectValue("branding", "Invalid", OutputMessagesConstants.REQUIRED_BRANDING);
        }

        if (StringUtils.isEmpty(dto.getIntent())) {
            errors.rejectValue("intent", "Invalid", "Intent is required.");
        }

        if (StringUtils.isEmpty(dto.getTimestamp())) {
            errors.rejectValue("timestamp", "Invalid", "Timestamp is required.");
        }

        if (dto.getOrgId() == null) {
            errors.rejectValue("orgId", "Invalid", OutputMessagesConstants.REQUIRED_ORG_ID);
        }

        validateCredential(dto.getCredential(), errors);
        validateProfile(dto.getProfile(), errors);
        validateMetadata(dto.getMetadata(), errors);
    }

    /* ---------------------------------------------------
       UPDATE USER
     --------------------------------------------------- */

    private void validateUpdateUserDto(UpdateUserDto dto, Errors errors) {
        validateCommonFields(dto, errors);

        validatePhoneNumber("phoneNumber", false, dto.getPhoneNumber(), errors);

        validatePhoneNumber("secondaryPhoneNumber", false, dto.getSecondaryPhoneNumber(), errors);

        if (!StringUtils.isEmpty(dto.getTitle()) && dto.getTitle().length() > 5) {
            errors.rejectValue("title", "Invalid", "Title length is longer than expected.");
        }

        if (!StringUtils.isEmpty(dto.getMiddleInitial()) && dto.getMiddleInitial().length() > 1) {

            errors.rejectValue("middleInitial", "Invalid", "Middle Initial length is longer than expected.");
        }

        if (dto.getSuffix() != null && !isValidSuffix(dto.getSuffix())) {
            errors.rejectValue("suffix", "Invalid", "Suffix is not a valid value.");
        }
    }

    private void validateUpdateUserEmailDto(UpdateUserEmailDto dto, Errors errors) {
        validateEmail(dto.getEmail(), errors);
    }

    private void validateUpdateUserSecuritySettingsDto(UpdateUserSecuritySettingsDto dto, Errors errors) {
        if (dto.getTwoFactorEnabled() == null) {
            errors.rejectValue("twoFactorEnabled", "Invalid", "Two Factor Enabled is required.");
        }
    }

    private void validateUpdateUserMetadataDto(UpdateUserMetadataDto dto, Errors errors) {
        if (dto.getMetaData() == null) {
            errors.rejectValue("metaData", "Invalid", "Metadata is required.");
        }
    }

    private void validateBrandingDto(BrandingDto dto, Errors errors) {
        if (dto.getBrandingIds().stream().anyMatch(StringUtils::isBlank)) {
            errors.rejectValue("brandingIds", "Invalid", EMPTY_OR_BLANK_BRANDING_ID);
        }
    }

    private void validateChangeProfileViewDto(ChangeProfileViewDto dto, Errors errors) {
        if (StringUtils.isEmpty(dto.getBranding())) {
            errors.rejectValue("branding", "Invalid", OutputMessagesConstants.REQUIRED_BRANDING);
        }

        if (StringUtils.isEmpty(dto.getRedirectUri())) {
            errors.rejectValue("redirectUri", "Invalid", REQUIRED_REDIRECT_URI);
        }
    }

    /* ---------------------------------------------------
       COMMON VALIDATIONS
     --------------------------------------------------- */

    private void validateCommonFields(UpdateUserDto dto, Errors errors) {

        if (StringUtils.isEmpty(dto.getFirstName())) {
            errors.rejectValue("firstName", "Invalid", OutputMessagesConstants.REQUIRED_FIRSTNAME);
        }

        if (StringUtils.isEmpty(dto.getLastName())) {
            errors.rejectValue("lastName", "Invalid", OutputMessagesConstants.REQUIRED_LASTNAME);
        }
    }

    private void validatePhoneNumber(String field, boolean required, String phoneNumber, Errors errors) {

        if (required) {
            if (StringUtils.isEmpty(phoneNumber)) {
                errors.rejectValue(field, "Invalid", OutputMessagesConstants.REQUIRED_PHONE_NUMBER);
            } else if (!Pattern.compile(phoneNumberRegexPattern).matcher(phoneNumber).matches()) {

                errors.rejectValue(field, "Invalid", OutputMessagesConstants.INVALID_PHONE_SYNTAX);
            }
        } else {
            if (!StringUtils.isEmpty(phoneNumber) && !Pattern.compile(phoneNumberRegexPattern).matcher(phoneNumber).matches()) {

                errors.rejectValue(field, "Invalid", OutputMessagesConstants.INVALID_PHONE_SYNTAX);
            }
        }
    }

    private void validateEmail(String email, Errors errors) {

        if (StringUtils.isEmpty(email)) {
            errors.rejectValue("email", "Invalid", OutputMessagesConstants.REQUIRED_EMAIL);
        } else if (!Pattern.compile(emailRegexPattern).matcher(email).matches()) {

            errors.rejectValue("email", "Invalid", OutputMessagesConstants.INVALID_EMAIL_SYNTAX);
        }
    }

    /* ---------------------------------------------------
       CREATE USER SUB-OBJECTS
     --------------------------------------------------- */

    private void validateCredential(CreateUserProfileDto.Credential credential, Errors errors) {

        if (credential == null) {
            errors.rejectValue("credential", "Invalid", "Credential is required.");
            return;
        }

        if (credential.getLoginId() == null) {
            errors.rejectValue("credential.loginId", "Invalid", "Login Id is required.");
        }

        if (StringUtils.isEmpty(credential.getUsername())) {
            errors.rejectValue("credential.username", "Invalid", "Username is required.");
        }

        if (StringUtils.isEmpty(credential.getPasswordHash())) {
            errors.rejectValue("credential.passwordHash", "Invalid", OutputMessagesConstants.REQUIRED_PASSWORD);
        }

        if (StringUtils.isEmpty(credential.getcIndex())) {
            errors.rejectValue("credential.cIndex", "Invalid", REQUIRED_CIPHER_INDEX);
        }
    }

    private void validateProfile(CreateUserProfileDto.Profile profile, Errors errors) {

        if (profile == null) {
            errors.rejectValue("profile", "Invalid", "Profile is required.");
            return;
        }

        if (profile.getMemberId() == null) {
            errors.rejectValue("profile.memberId", "Invalid", "Member Id is required.");
        }

        if (StringUtils.isEmpty(profile.getFirstName())) {
            errors.rejectValue("profile.firstName", "Invalid", OutputMessagesConstants.REQUIRED_FIRSTNAME);
        }

        if (StringUtils.isEmpty(profile.getLastName())) {
            errors.rejectValue("profile.lastName", "Invalid", OutputMessagesConstants.REQUIRED_LASTNAME);
        }

        validateEmail(profile.getEmail(), errors);

        validatePhoneNumber("profile.phoneNumber", false, profile.getPhoneNumber(), errors);

        validatePhoneNumber("profile.secondaryPhoneNumber", false, profile.getSecondaryPhoneNumber(), errors);
    }

    private void validateMetadata(CreateUserProfileDto.Metadata metadata, Errors errors) {

        if (metadata == null) {
            errors.rejectValue("metadata", "Invalid", "Metadata is required.");
            return;
        }

        if (metadata.getIdp() == null) {
            errors.rejectValue("metadata.idp", "Invalid", "Idp is required.");
        }

        if (metadata.getUnite() == null) {
            errors.rejectValue("metadata.unite", "Invalid", "Unite is required.");
        }
    }

    private boolean isValidSuffix(SuffixType suffixType) {
        return suffixType == SuffixType.Mr || suffixType == SuffixType.Mrs || suffixType == SuffixType.Miss || suffixType == SuffixType.Sr || suffixType == SuffixType.Jr;
    }
}
