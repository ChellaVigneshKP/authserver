package com.chellavignesh.authserver.session.entity;

import com.chellavignesh.authserver.enums.entity.AuthFlowEnum;
import com.chellavignesh.authserver.enums.entity.AuthSessionStatusEnum;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.ResultSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@Slf4j
public class AuthSession {
    private Integer authSessionId;
    private Integer applicationId;
    private UUID sessionId;
    private String subjectId;
    private Set<String> scopes;
    private AuthSessionStatusEnum authSessionStatus;
    private AuthFlowEnum authFlow;
    private byte[] clientFingerprint;
    private String branding;
    private String redirectUri;

    public static AuthSession fromResult(ResultSet result) {
        try {
            AuthSession authSession = new AuthSession();
            authSession.setAuthSessionId(result.getInt("AuthSessionId"));
            authSession.setApplicationId(result.getInt("ApplicationId"));
            authSession.setSessionId(UUID.fromString(result.getString("SessionId")));
            authSession.setSubjectId(result.getString("SubjectId"));
            authSession.setScopes(new HashSet<>(List.of(result.getString("Scope").split(" "))));
            authSession.setAuthSessionStatus(AuthSessionStatusEnum.fromInt(result.getInt("AuthSessionStatusId")));
            authSession.setAuthFlow(AuthFlowEnum.fromInt(result.getInt("AuthFlowId")));
            authSession.setClientFingerprint(result.getBytes("ClientFingerprint"));
            authSession.setBranding(result.getString("Branding"));
            String redirectUri = result.getString("RedirectUri");
            authSession.setRedirectUri(redirectUri);
            log.debug("[SESSION-DEBUG] AuthSession loaded → sessionId: {}, applicationId: {}, redirectUri: {}, branding: {}",
                    authSession.getSessionId(), authSession.getApplicationId(), redirectUri, authSession.getBranding());
            return authSession;
        } catch (Exception ex) {
            log.error("Failed to create AuthSession from ResultSet: {}", ex.getMessage());
            return null;
        }
    }
}

