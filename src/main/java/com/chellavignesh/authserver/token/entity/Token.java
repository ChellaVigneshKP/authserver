package com.chellavignesh.authserver.token.entity;

import com.chellavignesh.authserver.enums.entity.TokenTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.UUID;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Token {

    private Integer id;
    private TokenTypeEnum tokenType;
    private String subjectId;
    private UUID sessionId;
    private Integer applicationId;
    private String data;
    private boolean isOpaque;
    private Date createdOn;
    private Date expiration;
    private Date consumedOn;
    private Set<String> scopes;

    /**
     * Signing key for token signature verification.
     * Now safely serialized to Redis cache using Base64 encoding.
     * Custom serializers prevent binary data corruption.
     */
    private byte[] signingKey;

    public static Token fromResult(ResultSet rs) throws SQLException {
        Token token = new Token();
            token.setId(rs.getInt("TokenId"));
            token.setTokenType(TokenTypeEnum.fromInt(rs.getInt("TokenTypeId")));
            token.setSubjectId(rs.getString("SubjectId"));
            token.setSessionId(UUID.fromString(rs.getString("SessionId")));
            token.setApplicationId(rs.getInt("ApplicationId"));
            token.setData(rs.getString("Data"));
            token.setOpaque(rs.getBoolean("isOpaque"));
            token.setCreatedOn(rs.getTimestamp("CreatedOn", java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))));
            token.setExpiration(rs.getTimestamp("Expiration", java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))));
            token.setSigningKey(rs.getBytes("SigningKey"));
            String scope = rs.getString("Scope");
            token.setScopes(scope == null ? null : scope.isBlank() ? Set.of()
                    : Arrays.stream(scope.trim().split("\\s+")).collect(Collectors.toSet()));
            token.setConsumedOn(rs.getTimestamp("ConsumedOn", java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))));
            return token;
    }
}
