package com.chellavignesh.authserver.enums.entity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public enum SuffixType {
    Mr, Mrs, Miss, Sr, Jr;

    private static final Map<SuffixType, Integer> enumToVal = new HashMap<>();
    private static final Map<Integer, SuffixType> valToEnum = new HashMap<>();

    public static void setValues(ResultSet rs) {
        enumToVal.clear();
        valToEnum.clear();
        var newValues = new HashMap<SuffixType, Integer>();
        try {
            newValues.put(Mr, rs.getInt("MrEnumId"));
            newValues.put(Mrs, rs.getInt("MrsEnumId"));
            newValues.put(Miss, rs.getInt("MissEnumId"));
            newValues.put(Sr, rs.getInt("SrEnumId"));
            newValues.put(Jr, rs.getInt("JrEnumId"));
        } catch (SQLException _) {
            return;
        }
        enumToVal.putAll(newValues);
        enumToVal.forEach((k, v) -> valToEnum.put(v, k));
    }

    public static SuffixType fromInt(Integer enumId) {
        return valToEnum.get(enumId);
    }

    public Integer getValue() {
        return enumToVal.get(this);
    }
}
