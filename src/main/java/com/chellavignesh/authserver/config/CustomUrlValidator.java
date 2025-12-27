package com.chellavignesh.authserver.config;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.UrlValidator;

import java.util.regex.Pattern;

@Slf4j
public class CustomUrlValidator extends UrlValidator {

    private static final String CUSTOM_SCHEME_REGEX =
            "(?i)^(https?|ftp|com\\.ascensus\\.readysave529\\.qa4)://" +
                    "((localhost)|" +
                    "([a-z0-9-]+\\.)+[a-z]{2,6}|" +
                    "(\\d{1,3}(\\.\\d{1,3}){3}))" +
                    "(:\\d{1,5})?" +
                    "(/.*)?$";

    private final Pattern customSchemePattern;

    public CustomUrlValidator() {
        super(new String[]{"http", "https", "ftp", "com.ascensus.readysave529.qa4"});
        this.customSchemePattern = Pattern.compile(CUSTOM_SCHEME_REGEX);
    }

    @Override
    public boolean isValid(String url) {
        if (url == null) {
            log.debug("CustomUrlValidator isValid - url is null");
            return false;
        }

        if (customSchemePattern.matcher(url).matches()) {
            log.debug("CustomUrlValidator isValid - url is valid");
            return true;
        }

        return super.isValid(url);
    }
}

