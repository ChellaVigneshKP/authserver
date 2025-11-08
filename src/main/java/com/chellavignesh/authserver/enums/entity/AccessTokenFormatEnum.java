package com.chellavignesh.authserver.enums.entity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public enum AccessTokenFormatEnum {
    SELF_CONTAINED,
    REFERENCE;

    private static final Map<AccessTokenFormatEnum, Integer> enumToVal = new HashMap<>();
    private static final Map<Integer, AccessTokenFormatEnum> valToEnum = new HashMap<>();

    public static void setValues(ResultSet rs) {
        enumToVal.clear();
        valToEnum.clear();
        var newValues = new HashMap<AccessTokenFormatEnum, Integer>();
        try {
            newValues.put(SELF_CONTAINED, rs.getInt("SelfContainedAccessTokenFormatEnumId"));
            newValues.put(REFERENCE, rs.getInt("ReferenceAccessTokenFormatEnumId"));
        } catch (SQLException _) {
            return;
        }
        enumToVal.putAll(newValues);
        enumToVal.forEach((k, v) -> valToEnum.put(v, k));
    }

    public static AccessTokenFormatEnum fromInt(Integer enumId) {
        return valToEnum.get(enumId);
    }

    public Integer getValue() {
        return enumToVal.get(this);
    }
}
