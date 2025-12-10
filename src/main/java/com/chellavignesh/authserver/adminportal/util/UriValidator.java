package com.chellavignesh.authserver.adminportal.util;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriTemplate;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class UriValidator implements ConstraintValidator<ValidUri, String> {

    private static final Set<String> ILLEGAL_REGEX_CHARACTERS = Set.of("/");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (Objects.isNull(value)) {
            return true;
        }

        try {
            var uri = UriComponentsBuilder.fromHttpUrl(value).build();
            var path = uri.getPath();

            if (Objects.nonNull(path) && StringUtils.hasText(path)) {
                var pathTemplate = new UriTemplate(uri.getPath());
                for (String s : pathTemplate.getVariableNames()) {
                    var containsIllegalCharacter = ILLEGAL_REGEX_CHARACTERS.stream().anyMatch(s::contains);
                    if (containsIllegalCharacter) {
                        return false;
                    }
                    Pattern.compile(s);
                }
            }

        } catch (Exception e) {
            return false;
        }

        return true;
    }
}
