package com.chellavignesh.authserver.enums.entity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public enum AuthSessionStatusEnum {
    INACTIVE, ACTIVE;

    private static final Map<AuthSessionStatusEnum, Integer> enumToVal = new HashMap<>();
    private static final Map<Integer, AuthSessionStatusEnum> valToEnum = new HashMap<>();

    public static void setValues(ResultSet rs) {
        enumToVal.clear();
        valToEnum.clear();
        var newValues = new HashMap<AuthSessionStatusEnum, Integer>();
        try {
            newValues.put(INACTIVE, rs.getInt("AuthSessionInactiveEnumId"));
            newValues.put(ACTIVE, rs.getInt("AuthSessionActiveEnumId"));
        } catch (SQLException _) {
            return;
        }
        enumToVal.putAll(newValues);
        enumToVal.forEach((k, v) -> valToEnum.put(v, k));
    }

    public static AuthSessionStatusEnum fromInt(Integer enumId) {
        return valToEnum.get(enumId);
    }

    public Integer getValue() {
        return enumToVal.get(this);
    }
}
