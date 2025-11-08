package com.chellavignesh.authserver.adminportal.application.entity;

import com.chellavignesh.authserver.enums.entity.ApplicationTypeEnum;
import com.chellavignesh.authserver.enums.entity.AuthFlowEnum;
import com.chellavignesh.authserver.enums.entity.UsernameTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Application {
    private Integer id;
    private Integer orgId;
    private String clientId;
    private String name;
    private String description;
    private String uri;
    private ApplicationTypeEnum type;
    private AuthFlowEnum authFlow;
    private UsernameTypeEnum usernameType;
    private Boolean allowForgotUsername;
    private UUID rowGuid;
    private Boolean active = true;
    private String cmsContext;

    public static Application fromResultSet(ResultSet result) throws SQLException {
        Application app = new Application();
        try {
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
            app.setCmsContext(result.getString("CMSContext"));
            app.setAllowForgotUsername(result.getBoolean("AllowForgotUsername"));
            int retrievedUsernameType = result.getInt("UsernameType");
            if (result.wasNull()) app.setUsernameType(UsernameTypeEnum.USERNAME);
            else app.setUsernameType(UsernameTypeEnum.fromInt(retrievedUsernameType));
            return app;
        } catch (SQLException _) {
            return null;
        }
    }
}
