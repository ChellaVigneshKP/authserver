package com.chellavignesh.authserver.adminportal.admin;

import com.chellavignesh.authserver.adminportal.admin.entity.AdminConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class AdminService {
    private final AdminConfig adminConfig;

    @Autowired
    public AdminService(AdminRepository adminRepository) {
        Optional<AdminConfig> adminConfigOpt = adminRepository.loadAdminConfig();

        if (adminConfigOpt.isEmpty()) {
            log.error("AdminConfig not found");
            this.adminConfig = null;
            return;
        }
        this.adminConfig = adminConfigOpt.get();
        log.info("AdminConfig loaded successfully");
    }

    public Integer getAdminOrgId() {
        return adminConfig.getAdminOrgId();
    }

    public Integer getAdminPortalAppId() {
        return adminConfig.getAdminPortalAppId();
    }

    public String getAdminPortalAppClientId() {
        return adminConfig.getAdminPortalAppClientId();
    }

    public Integer getAdminGroupId() {
        return adminConfig.getAdminGroupId();
    }

    public Integer getAdminProfileId() {
        return adminConfig.getAdminProfileId();
    }

    public String toString() {
        return String.format("AdminConfig{adminOrgId=%d, adminPortalAppId=%d, adminPortalAppClientId=%s, adminGroupId=%d, adminProfileId=%d}",
                adminConfig.getAdminOrgId(), adminConfig.getAdminPortalAppId(), adminConfig.getAdminPortalAppClientId(), adminConfig.getAdminGroupId(), adminConfig.getAdminProfileId());
    }
}
