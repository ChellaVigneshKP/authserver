package com.chellavignesh.authserver.mfa.mfarealm;

import com.chellavignesh.authserver.mfa.mfarealm.entity.MFARealm;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MFARealmService {
    private final MFARealmRepository mfaRealmRepository;

    public MFARealmService(MFARealmRepository mfaRealmRepository) {
        this.mfaRealmRepository = mfaRealmRepository;
    }

    public List<MFARealm> getAll() {
        return mfaRealmRepository.getAll();
    }
}
