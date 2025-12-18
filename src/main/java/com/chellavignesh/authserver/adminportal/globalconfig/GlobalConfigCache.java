package com.chellavignesh.authserver.adminportal.globalconfig;

import com.chellavignesh.authserver.adminportal.globalconfig.entity.GlobalConfig;
import com.chellavignesh.authserver.enums.entity.GlobalConfigTypeEnum;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GlobalConfigCache {

    private final GlobalConfigService globalConfigService;

    // THREAD-SAFETY FIX: Use volatile for lazy initialization
    private volatile Map<String, Integer> globalConfigMap = null;

    public GlobalConfigCache(GlobalConfigService globalConfigService) {
        this.globalConfigService = globalConfigService;
    }

    public Integer getGlobalConfig(GlobalConfigTypeEnum globalConfigEnum) {
        return getAllAsMap().get(globalConfigEnum.getGlobalConfigType());
    }

    // THREAD-SAFETY FIX: Double-checked locking for lazy initialization
    public Map<String, Integer> getAllAsMap() {
        if (globalConfigMap == null) {
            synchronized (this) {
                if (globalConfigMap == null) {
                    Map<String, Integer> tempMap = new ConcurrentHashMap<>();
                    List<GlobalConfig> globalConfigs = globalConfigService.getALL();

                    for (GlobalConfig config : globalConfigs) {
                        tempMap.put(config.getName(), config.getValue());
                    }

                    // Atomic assignment after full initialization
                    globalConfigMap = tempMap;
                }
            }
        }
        return globalConfigMap;
    }
}
