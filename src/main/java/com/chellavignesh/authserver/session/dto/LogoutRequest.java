package com.chellavignesh.authserver.session.dto;

import jakarta.validation.constraints.NotEmpty;

public record LogoutRequest(
        @NotEmpty(message = "id_token_hint must not be empty") String id_token_hint,
        @NotEmpty(message = "client_id must not be empty") String client_id,
        @NotEmpty(message = "post_logout_redirect_uri must not be empty") String post_logout_redirect_uri,
        String error_code
) {
}
