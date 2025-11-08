package com.chellavignesh.authserver.adminportal.application.entity;


import com.chellavignesh.authserver.adminportal.forgotusername.entity.UsernameLookupCriteria;
import com.chellavignesh.authserver.enums.entity.ApplicationTypeEnum;
import com.chellavignesh.authserver.enums.entity.AuthFlowEnum;
import com.chellavignesh.authserver.enums.entity.UsernameTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApplicationDetail {
    private Integer id;
    private Integer orgId;
    private String clientId;
    private String name;
    private String description;
    private String uri;
    private ApplicationTypeEnum type;
    private AuthFlowEnum authFlow;
    private UUID rowGuid;
    private Boolean active = true;
    private Integer authCodeTimeToLive;
    private Integer accessTokenTimeToLive;
    private Integer deviceCodeTimeToLive;
    private Integer refreshTokenTimeToLive;
    private Integer maxRequestTransitTime;
    private UsernameTypeEnum usernameType;
    private Boolean allowForgotUsername;
    private ArrayList<UsernameLookupCriteria> usernameLookupCriteria;
    private Integer mfaRealmId;
    private String cmsContext;
    private Integer pinTimeToLive;

    public static ApplicationDetail fromResultSet(ResultSet result) throws SQLException {
        ApplicationDetail app = new ApplicationDetail();
        app.setId(result.getInt("ApplicationId"));
        app.setOrgId(result.getInt("OrganizationId"));
        app.setClientId(result.getString("ClientId"));
        app.setName(result.getString("Name"));
        app.setDescription(result.getString("Description"));
        app.setUri(result.getString("Uri"));
        app.setRowGuid(UUID.fromString(result.getString("RowGuid")));
        app.setType(ApplicationTypeEnum.fromInt(result.getInt("ApplicationTypeId")));
        app.setAuthFlow(AuthFlowEnum.fromInt(result.getInt("AuthFlowId")));
        app.setActive(result.getBoolean("Active"));
        app.setAuthCodeTimeToLive(result.getInt("AuthCodeTimeToLive"));
        app.setAccessTokenTimeToLive(result.getInt("AccessTokenTimeToLive"));
        app.setDeviceCodeTimeToLive(result.getInt("DeviceCodeTimeToLive"));
        app.setRefreshTokenTimeToLive(result.getInt("RefreshTokenTimeToLive"));
        app.setMaxRequestTransitTime(result.getInt("MaxRequestTransitTime"));
        app.setPinTimeToLive(result.getInt("PinTimeToLive"));

        int retrievedUsernameType = result.getInt("UsernameType");
        if (result.wasNull()) app.setUsernameType(UsernameTypeEnum.USERNAME);
        else app.setUsernameType(UsernameTypeEnum.fromInt(retrievedUsernameType));
        app.setAllowForgotUsername(result.getBoolean("AllowForgotUsername"));
        app.setMfaRealmId(result.getInt("MfaRealmId"));
        app.setCmsContext(result.getString("CMSContext"));
        return app;
    }
}

