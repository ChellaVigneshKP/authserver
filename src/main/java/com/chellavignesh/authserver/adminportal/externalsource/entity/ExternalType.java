package com.chellavignesh.authserver.adminportal.externalsource.entity;

import lombok.extern.slf4j.Slf4j;

import java.sql.ResultSet;
import java.sql.SQLException;

@Slf4j
public record ExternalType(
        int id,
        String name,
        String searchSchema,
        String syncSchema
) {
    public static ExternalType fromResult(ResultSet result) throws SQLException {
        try {
            return new ExternalType(
                    result.getInt("ExternalTypeId"),
                    result.getString("ExternalTypeName"),
                    result.getString("ForgetUserSchema"),
                    result.getString("SyncUserSchema")
            );
        } catch (IllegalArgumentException e) {
            log.error("Could not map ExternalType row");
            return null;
        }
    }
}
