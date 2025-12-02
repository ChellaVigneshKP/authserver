package com.chellavignesh.authserver.mfa.mfarealm.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MFARealm {
    private Integer mfaRealmId;
    private String name;
    private String description;
    private String uri;
    private Integer orgId;

    public static MFARealm fromResult(ResultSet rs) throws SQLException {
        MFARealm mfaRealm = new MFARealm();
        mfaRealm.setMfaRealmId(rs.getInt("MfaRealmId"));
        mfaRealm.setName(rs.getString("Name"));
        mfaRealm.setDescription(rs.getString("Description"));
        mfaRealm.setUri(rs.getString("Uri"));
        mfaRealm.setOrgId(rs.getInt("OrganizationId"));
        return mfaRealm;
    }
}
