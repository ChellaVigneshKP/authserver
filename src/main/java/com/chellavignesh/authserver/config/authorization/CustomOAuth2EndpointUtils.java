package com.chellavignesh.authserver.config.authorization;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

final class CustomOAuth2EndpointUtils {

    private CustomOAuth2EndpointUtils() {
    }

    static MultiValueMap<String, String> getFormParameters(HttpServletRequest request) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();

        // For GET requests, all parameters are query parameters, so return empty for form parameters
        if ("GET".equals(request.getMethod())) {
            return parameters;
        }

        String queryString = StringUtils.hasText(request.getQueryString())
                ? request.getQueryString()
                : "";

        parameterMap.forEach((String key, String[] values) -> {
            // If not query parameter then it's a form parameter
            if (!isParameterInQueryString(queryString, key)) {
                for (String value : values) {
                    parameters.add(key, value);
                }
            }
        });

        return parameters;
    }

    static MultiValueMap<String, String> getQueryParameters(HttpServletRequest request) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();

        // For GET requests, all parameters are query parameters
        if ("GET".equals(request.getMethod())) {
            parameterMap.forEach((String key, String[] values) -> {
                for (String value : values) {
                    parameters.add(key, value);
                }
            });
            return parameters;
        }

        String queryString = StringUtils.hasText(request.getQueryString())
                ? request.getQueryString()
                : "";

        parameterMap.forEach((String key, String[] values) -> {
            // Check if parameter exists in query string using proper URL parameter matching
            // This handles URL-encoded parameters correctly
            if (isParameterInQueryString(queryString, key)) {
                for (String value : values) {
                    parameters.add(key, value);
                }
            }
        });

        return parameters;
    }

    /**
     * Check if a parameter exists in the query string by looking for proper parameter boundaries.
     * This handles URL-encoded parameters correctly by using the parameter map which is already decoded.
     */
    private static boolean isParameterInQueryString(String queryString, String parameterName) {
        if (!StringUtils.hasText(queryString) || !StringUtils.hasText(parameterName)) {
            return false;
        }

        // Look for parameter at start of query string or after &
        String paramPattern = parameterName + "=";
        boolean found = queryString.startsWith(paramPattern)
                || queryString.contains("&" + paramPattern);

        // If not found with exact name, also check for URL-encoded version
        if (!found) {
            String encodedParamName =
                    java.net.URLEncoder.encode(parameterName, StandardCharsets.UTF_8);
            String encodedParamPattern = encodedParamName + "=";

            found = queryString.startsWith(encodedParamPattern)
                    || queryString.contains("&" + encodedParamPattern);
        }

        return found;
    }
}

