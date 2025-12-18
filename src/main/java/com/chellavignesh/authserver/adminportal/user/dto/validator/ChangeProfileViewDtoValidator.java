package com.chellavignesh.authserver.adminportal.user.dto.validator;

import com.chellavignesh.authserver.adminportal.user.dto.ChangeProfileViewDto;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class ChangeProfileViewDtoValidator implements Validator {

    public static final String REQUIRED_BRANDING = "Branding parameter is required";
    public static final String REQUIRED_REDIRECT_URI = "redirect_uri parameter is required";

    @Override
    public boolean supports(Class<?> clazz) {
        return clazz.equals(ChangeProfileViewDto.class);
    }

    @Override
    public void validate(@NonNull Object target, @NonNull Errors errors) {
        if (target instanceof ChangeProfileViewDto changeProfileViewDto) {

            if (StringUtils.isEmpty(changeProfileViewDto.getBranding())) {
                errors.rejectValue(
                        "branding",
                        "Invalid",
                        REQUIRED_BRANDING
                );
            }

            if (StringUtils.isEmpty(changeProfileViewDto.getRedirectUri())) {
                errors.rejectValue(
                        "redirectUri",
                        "Invalid",
                        REQUIRED_REDIRECT_URI
                );
            }
        }
    }
}
