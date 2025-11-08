package com.chellavignesh.authserver.adminportal.application.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MfaExpiry {
    private Long timeToExpireMS;
    private String issueTime;
    private String expiryTime;

    public static MfaExpiry fromResult(ResultSet rs) throws SQLException {
        MfaExpiry expiry = new MfaExpiry();
        expiry.setTimeToExpireMS(rs.getLong("TimeToExpireMS"));
        expiry.setIssueTime(rs.getString("IssueTime"));
        expiry.setExpiryTime(rs.getString("ExpiryTime"));
        return expiry;
    }
}
