package com.chellavignesh.authserver.adminportal.credential;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public enum CredentialStatus {
    Inactive, Active, Disabled;

    private static final Map<CredentialStatus, Integer> enumToVal = new HashMap<>();
    private static final Map<Integer, CredentialStatus> valToEnum = new HashMap<>();

    public static void setValues(ResultSet rs) {
        enumToVal.clear();
        valToEnum.clear();
        var newValues = new HashMap<CredentialStatus, Integer>();
        try {
            newValues.put(Inactive, rs.getInt("InactiveEnumId"));
            newValues.put(Active, rs.getInt("ActiveEnumId"));
            newValues.put(Disabled, rs.getInt("DisabledEnumId"));
        } catch (SQLException _) {
            return;
        }
        enumToVal.putAll(newValues);
        enumToVal.forEach((k, v) -> valToEnum.put(v, k));
    }

    public static CredentialStatus fromString(String enumName) {
        return switch (enumName) {
            case "Inactive" -> Inactive;
            case "Active" -> Active;
            case "Disabled" -> Disabled;
            default -> throw new IllegalArgumentException("Invalid enum name given");
        };
    }

    public static CredentialStatus fromInt(Integer enumId) {
        return valToEnum.get(enumId);
    }

    public Integer getValue() {
        return enumToVal.get(this);
    }
}
