package com.chellavignesh.authserver.token.entity;

import com.chellavignesh.authserver.enums.entity.TokenTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.UUID;

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

    /**
     * Signing key for token signature verification.
     * Now safely serialized to Redis cache using Base64 encoding.
     * Custom serializers prevent binary data corruption.
     */
    private byte[] signingKey;

    public static Token fromResult(ResultSet rs) throws SQLException {
        Token token = new Token();
        try {
            token.setId(rs.getInt("TokenId"));
            token.setTokenType(TokenTypeEnum.fromInt(rs.getInt("TokenTypeId")));
            token.setSubjectId(rs.getString("SubjectId"));
            token.setSessionId(UUID.fromString(rs.getString("SessionId")));
            token.setApplicationId(rs.getInt("ApplicationId"));
            token.setData(rs.getString("Data"));
            token.setOpaque(rs.getBoolean("isOpaque"));
            token.setCreatedOn(rs.getTimestamp("CreatedOn"));
            token.setExpiration(rs.getTimestamp("Expiration"));
            token.setSigningKey(rs.getBytes("SigningKey"));
            return token;
        } catch (SQLException _) {
            return null;
        }
    }
}
