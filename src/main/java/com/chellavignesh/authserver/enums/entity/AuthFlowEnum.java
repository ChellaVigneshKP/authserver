package com.chellavignesh.authserver.enums.entity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public enum AuthFlowEnum {
    CLIENT_SECRET_JWT,
    PRIVATE_KEY_JWT,
    PKCE;

    private static final Map<AuthFlowEnum, Integer> enumToVal = new HashMap<>();
    private static final Map<Integer, AuthFlowEnum> valToEnum = new HashMap<>();

    public static void setValues(ResultSet rs) {
        enumToVal.clear();
        valToEnum.clear();
        var newValues = new HashMap<AuthFlowEnum, Integer>();
        try {
            newValues.put(CLIENT_SECRET_JWT, rs.getInt("ClientSecretJwtEnumId"));
            newValues.put(PRIVATE_KEY_JWT, rs.getInt("PrivateKeyJwtEnumId"));
            newValues.put(PKCE, rs.getInt("AuthCodeEnumId"));
        } catch (SQLException _) {
            return;
        }
        enumToVal.putAll(newValues);
        enumToVal.forEach((k, v) -> valToEnum.put(v, k));
    }

    public static AuthFlowEnum fromInt(Integer enumId) {
        return valToEnum.get(enumId);
    }

    public Integer getValue() {
        return enumToVal.get(this);
    }
}
