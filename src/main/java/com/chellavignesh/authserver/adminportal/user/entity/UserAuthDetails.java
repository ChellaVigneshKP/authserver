package com.chellavignesh.authserver.adminportal.user.entity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = UserAuthDetails.class, name = "userAuthDetails")
})
public class UserAuthDetails {

    private Integer id;
    private UUID rowGuid;
    private String userName;
    private Integer userStatus;
    private String password;
    private Integer credentialStatus;
    private Integer version;
    private Boolean credentialLocked;
    private Boolean twoFactorEnabled;
    private String phoneNumber;
    private Date lastLogin;

    public static UserAuthDetails fromResult(ResultSet result) throws SQLException {
        UserAuthDetails userAuthDetails = new UserAuthDetails();

        userAuthDetails.setId(result.getInt("ProfileId"));
        userAuthDetails.setRowGuid(UUID.fromString(result.getString("RowGuid")));
        userAuthDetails.setUserName(result.getString("UserName"));
        userAuthDetails.setPassword(result.getString("Password"));
        userAuthDetails.setVersion(result.getInt("Version"));
        userAuthDetails.setUserStatus(result.getInt("Status"));
        userAuthDetails.setCredentialStatus(result.getInt("CredentialStatus"));
        userAuthDetails.setCredentialLocked(result.getBoolean("CredentialLocked"));

        boolean twoFactorEnabled = result.getBoolean("TwoFactorEnabled");
        userAuthDetails.setTwoFactorEnabled(twoFactorEnabled);

        userAuthDetails.setPhoneNumber(result.getString("PhoneNumber"));

        // Debug MFA field loading
        log.debug("[MFA-DEBUG] UserAuthDetails loaded - Username: {}, TwoFactorEnabled: {}, ProfileId: {}",
                result.getString("UserName"), twoFactorEnabled, result.getInt("ProfileId"));

        // Convert java.sql.Date to java.util.Date
        var sqlDate = result.getDate("LastLogin");
        Date date = (sqlDate == null ? null : new Date(sqlDate.getTime()));
        userAuthDetails.setLastLogin(date);

        return userAuthDetails;
    }
}
