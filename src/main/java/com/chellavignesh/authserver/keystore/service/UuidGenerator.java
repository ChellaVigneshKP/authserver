package com.chellavignesh.authserver.keystore.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UuidGenerator {
    public UUID random() {
        return UUID.randomUUID();
    }
}
