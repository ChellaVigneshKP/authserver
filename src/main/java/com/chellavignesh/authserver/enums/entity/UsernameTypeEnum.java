package com.chellavignesh.authserver.enums.entity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public enum UsernameTypeEnum {
    USERNAME,
    EMAIL;

    private static final Map<UsernameTypeEnum, Integer> enumToVal = new HashMap<>();
    private static final Map<Integer, UsernameTypeEnum> valToEnum = new HashMap<>();

    public static void setValues(ResultSet rs) {
        enumToVal.clear();
        valToEnum.clear();
        var newValues = new HashMap<UsernameTypeEnum, Integer>();
        try {
            newValues.put(USERNAME, rs.getInt("UsernameEnumId"));
            newValues.put(EMAIL, rs.getInt("EmailEnumId"));
        } catch (SQLException _) {
            return;
        }
        enumToVal.putAll(newValues);
        enumToVal.forEach((k, v) -> valToEnum.put(v, k));
    }

    public static UsernameTypeEnum fromInt(Integer enumId) {
        return valToEnum.get(enumId);
    }

    public Integer getValue() {
        return enumToVal.get(this);
    }
}
