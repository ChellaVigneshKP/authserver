package com.chellavignesh.authserver.adminportal.globalconfig;

import com.chellavignesh.authserver.adminportal.globalconfig.entity.GlobalConfig;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GlobalConfigService {

    private final GlobalConfigRepository globalConfigRepository;

    public GlobalConfigService(GlobalConfigRepository globalConfigRepository) {
        this.globalConfigRepository = globalConfigRepository;
    }

    public List<GlobalConfig> getALL() {
        return globalConfigRepository.getAll();
    }
}
