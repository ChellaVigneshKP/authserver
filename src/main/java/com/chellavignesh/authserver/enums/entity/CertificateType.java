package com.chellavignesh.authserver.enums.entity;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

public enum CertificateType {
    ORGANIZATION, PUBLIC_KEY;

    private static final Map<CertificateType, Integer> enumToVal = new HashMap<>();
    private static final Map<Integer, CertificateType> valToEnum = new HashMap<>();

    public static void setValues(ResultSet rs) {
        enumToVal.clear();
        valToEnum.clear();
        var newValues = new HashMap<CertificateType, Integer>();
        try {
            newValues.put(ORGANIZATION, rs.getInt("OrganizationEnumId"));
            newValues.put(PUBLIC_KEY, rs.getInt("PublicKeyEnumId"));
        } catch (Exception _) {
            return;
        }
        enumToVal.putAll(newValues);
        enumToVal.forEach((k, v) -> valToEnum.put(v, k));
    }

    public static CertificateType fromInt(Integer enumId) {
        return valToEnum.get(enumId);
    }

    public Integer getValue() {
        return enumToVal.get(this);
    }
}