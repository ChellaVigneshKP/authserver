package com.chellavignesh.authserver.adminportal.admin.entity;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.sql.ResultSet;

@NoArgsConstructor
@Getter
@Setter
@Slf4j
public class AdminConfig {
    private Integer adminOrgId;
    private Integer adminPortalAppId;
    private String adminPortalAppClientId;
    private Integer adminGroupId;
    private Integer adminProfileId;

    public static AdminConfig fromResult(ResultSet result) {
        try {
            AdminConfig adminConfig = new AdminConfig();
            adminConfig.setAdminOrgId(result.getInt("AdminOrgId"));
            adminConfig.setAdminPortalAppId(result.getInt("AdminPortalAppId"));
            adminConfig.setAdminPortalAppClientId(result.getString("AdminPortalClientId"));
            adminConfig.setAdminGroupId(result.getInt("AdminGroupId"));
            adminConfig.setAdminProfileId(result.getInt("AdminProfileId"));
            return adminConfig;
        } catch (Exception e) {
            log.error("Error while converting result to AdminConfig", e);
            return null;
        }
    }
}
