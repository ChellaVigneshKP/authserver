package com.chellavignesh.authserver.adminportal.externalsource.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExternalSource {

    private UUID sourceId;
    private String sourceCode;
    private boolean syncFlag;
    private ExternalType externalType;

    public static ExternalSource fromResult(ResultSet result) throws SQLException {
        ExternalSource externalSource = new ExternalSource();
        try {
            externalSource.setSourceId(UUID.fromString(result.getString("SourceId")));
            externalSource.setSourceCode(result.getString("SourceCode"));
            externalSource.setSyncFlag(result.getBoolean("SyncFlag"));
            externalSource.setExternalType(ExternalType.fromResult(result));
        } catch (IllegalArgumentException _) {
            log.error("Could not map ExternalSource row");
            return null;
        }
        return externalSource;
    }
}
