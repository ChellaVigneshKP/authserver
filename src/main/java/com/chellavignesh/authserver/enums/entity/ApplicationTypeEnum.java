package com.chellavignesh.authserver.enums.entity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public enum ApplicationTypeEnum {
    MOBILE, WEB, SERVER;

    private static final Map<ApplicationTypeEnum, Integer> enumToVal = new HashMap<>();
    private static final Map<Integer, ApplicationTypeEnum> valToEnum = new HashMap<>();

    public static void setValues(ResultSet rs) {
        enumToVal.clear();
        valToEnum.clear();
        var newValues = new HashMap<ApplicationTypeEnum, Integer>();
        try {
            newValues.put(MOBILE, rs.getInt("MobileEnumId"));
            newValues.put(WEB, rs.getInt("WebEnumId"));
            newValues.put(SERVER, rs.getInt("ServerEnumId"));
        } catch (SQLException _) {
            return;
        }
        enumToVal.putAll(newValues);
        enumToVal.forEach((k, v) -> valToEnum.put(v, k));
    }

    public static ApplicationTypeEnum fromInt(Integer enumId) {
        return valToEnum.get(enumId);
    }

    public Integer getValue() {
        return enumToVal.get(this);
    }
}
