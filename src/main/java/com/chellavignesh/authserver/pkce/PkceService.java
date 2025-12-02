package com.chellavignesh.authserver.pkce;

import com.chellavignesh.authserver.pkce.dto.CreatePkceDto;
import com.chellavignesh.authserver.pkce.entity.Pkce;
import com.chellavignesh.authserver.pkce.exception.PkceCreationFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class PkceService {
    private final PkceRepository pkceRepository;

    @Autowired
    public PkceService(PkceRepository pkceRepository) {
        this.pkceRepository = pkceRepository;
    }

    public Pkce create(CreatePkceDto dto) throws PkceCreationFailedException {
        return pkceRepository.create(dto);
    }

    public Optional<Pkce> getBySessionId(UUID sessionId) {
        return pkceRepository.getBySessionId(sessionId);
    }
}
