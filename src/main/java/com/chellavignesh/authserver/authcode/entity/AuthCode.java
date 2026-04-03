package com.chellavignesh.authserver.authcode.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;
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

    private static final Calendar UTC_CAL = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    public static AuthCode fromResult(ResultSet rs) throws SQLException {
        AuthCode code = new AuthCode();
        code.setId(rs.getInt("AuthCodeId"));
        code.setApplicationId(rs.getInt("ApplicationId"));
        code.setSessionId(UUID.fromString(rs.getString("SessionId")));
        code.setData(rs.getString("Data"));
        var consumedOnTs = rs.getTimestamp("ConsumedOn", UTC_CAL);
        code.setConsumedOn(consumedOnTs != null ? consumedOnTs.toInstant() : null);
        return code;
    }
}
