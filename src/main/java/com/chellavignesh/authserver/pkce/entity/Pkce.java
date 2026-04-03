package com.chellavignesh.authserver.pkce.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class Pkce {
    private Integer id;
    private UUID sessionId;
    private Integer applicationId;
    private String data;
    private String algorithm;
    private Date createdOn;
    private String redirectUri;
    private Date expiration;   // TODO: Ensure this matches expiration of AuthCode - currently both null
    private Date consumedOn;   // TODO: Connect this value in part 2 of PKCE ticket

    private static final Calendar UTC_CAL = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    public static Pkce fromResult(ResultSet rs) throws SQLException {
        Pkce pkce = new Pkce();
        pkce.setId(rs.getInt("PkceId"));
        pkce.setSessionId(UUID.fromString(rs.getString("SessionId")));
        pkce.setApplicationId(rs.getInt("ApplicationId"));
        pkce.setData(rs.getString("Data"));
        pkce.setAlgorithm(rs.getString("Algorithm"));
        pkce.setRedirectUri(rs.getString("RedirectUri"));
        pkce.setCreatedOn(rs.getTimestamp("CreatedOn", UTC_CAL));
        pkce.setExpiration(rs.getTimestamp("Expiration", UTC_CAL));
        pkce.setConsumedOn(rs.getTimestamp("ConsumedOn", UTC_CAL));
        return pkce;
    }
}
