package com.chellavignesh.authserver.mfa.mfarealm;

import com.chellavignesh.authserver.mfa.mfarealm.entity.MFARealm;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MFARealmCache {

    private final MFARealmService mfaRealmService;

    private volatile Map<Integer, MFARealm> realmMap = null;

    public MFARealmCache(MFARealmService mfaRealmService) {
        this.mfaRealmService = mfaRealmService;
    }

    public MFARealm getMfaRealm(Integer realmId) {
        return getAllAsMap().get(realmId);
    }

    public Map<Integer, MFARealm> getAllAsMap() {
        if (realmMap == null) {
            synchronized (this) {
                if (realmMap == null) {
                    Map<Integer, MFARealm> tempMap = new ConcurrentHashMap<>();
                    List<MFARealm> mfaRealms = mfaRealmService.getAll();
                    for (MFARealm mfaRealm : mfaRealms) {
                        tempMap.put(mfaRealm.getMfaRealmId(), mfaRealm);
                    }
                    realmMap = tempMap;
                }
            }
        }
        return realmMap;
    }
}
