package com.chellavignesh.authserver.config.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.stereotype.Component;

@Component
public class PkceFailureEventListener {

    private static final Logger logger = LoggerFactory.getLogger(PkceFailureEventListener.class);

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {

        Throwable ex = event.getException();

        if (ex instanceof OAuth2AuthenticationException oae) {

            var error = oae.getError();

            boolean invalidGrant = OAuth2ErrorCodes.INVALID_GRANT.equals(error.getErrorCode());

            boolean descriptionHintsPkce = error.getDescription() != null && error.getDescription().toLowerCase().contains("pkce");

            if (invalidGrant && descriptionHintsPkce) {
                logger.warn("[EVENT] PKCE verification failed: error_code={}, description={}", error.getErrorCode(), error.getDescription());
            }
        }
    }
}
