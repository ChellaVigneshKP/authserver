package com.chellavignesh.authserver.keystore.entity;

public record KeyStorePairDao(byte[] passwordKeyStoreBytes, byte[] mainKeyStoreBytes, String passwordAlias) {
}
