package com.chellavignesh.authserver.security.branding;

import com.chellavignesh.authserver.config.ApplicationConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

@Slf4j
public class BrandingAwareRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String[]> immutableParametersMap;
    private final String branding;

    /**
     * Constructs a request object wrapping the given request.
     *
     * @param request  the request to wrap
     * @param branding the active branding
     * @throws IllegalArgumentException if request is null
     */
    public BrandingAwareRequestWrapper(HttpServletRequest request, final String branding) {
        super(request);
        this.branding = branding;
        this.immutableParametersMap = this.prepareUsernameInParameterMap();
    }

    private Map<String, String[]> prepareUsernameInParameterMap() {
        final var mutableParameterMap = new HashMap<>(super.getParameterMap());

        if (!mutableParameterMap.containsKey(ApplicationConstants.LOGIN_FORM_USERNAME_PARAMETER)
                || ArrayUtils.isEmpty(mutableParameterMap.get(ApplicationConstants.LOGIN_FORM_USERNAME_PARAMETER))) {

            return super.getParameterMap();
        }

        final var usernameWithBrandArray =
                Arrays.stream(mutableParameterMap.get(ApplicationConstants.LOGIN_FORM_USERNAME_PARAMETER))
                        .map(this::toUsernameWithBrand)
                        .toList()
                        .toArray(new String[0]);

        log.debug("After processing usernames are: {}", Arrays.toString(usernameWithBrandArray));

        mutableParameterMap.put(
                ApplicationConstants.LOGIN_FORM_USERNAME_PARAMETER,
                usernameWithBrandArray
        );

        return Collections.unmodifiableMap(mutableParameterMap);
    }

    @Override
    public String getParameter(String name) {
        return Optional.of(immutableParametersMap)
                .map(paramMap -> paramMap.get(name))
                .filter(ArrayUtils::isNotEmpty)
                .map(pMap -> pMap[0])
                .orElse(null);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return immutableParametersMap;
    }

    @Override
    public String[] getParameterValues(String name) {
        return immutableParametersMap.get(name);
    }

    private String toUsernameWithBrand(final String username) {
        return StringUtils.join(username, System.lineSeparator(), branding);
    }
}
