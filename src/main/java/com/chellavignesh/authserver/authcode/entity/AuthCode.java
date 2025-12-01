package com.chellavignesh.authserver.authcode.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthCode {
    private Integer id;
    private Integer applicationId;
    private UUID sessionId;
    private String data;
    private Instant consumedOn;

    public static AuthCode fromResult(ResultSet rs) throws SQLException {
        AuthCode code = new AuthCode();
        try {
            code.setId(rs.getInt("AuthCodeId"));
            code.setApplicationId(rs.getInt("ApplicationId"));
            code.setSessionId(UUID.fromString(rs.getString("SessionId")));
            code.setData(rs.getString("Data"));
            code.setConsumedOn(Instant.ofEpochMilli(rs.getInt("ConsumedOn")));
            return code;
        } catch (SQLException _) {
            return null;
        }
    }
}
