package com.chellavignesh.authserver.enums.entity;

import org.springframework.security.oauth2.jose.jws.JwsAlgorithm;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public enum AlgorithmEnum {
    RS256,
    ES256,
    HS256;

    private static final Map<AlgorithmEnum, Integer> enumToVal = new EnumMap<>(AlgorithmEnum.class);
    private static final Map<Integer, AlgorithmEnum> valToEnum = new HashMap<>();

    public static void setValues(ResultSet rs) {
        enumToVal.clear();
        valToEnum.clear();
        Map<AlgorithmEnum, Integer> newValues = new EnumMap<>(AlgorithmEnum.class);
        try {
            newValues.put(RS256, rs.getInt("RS256EnumId"));
            newValues.put(ES256, rs.getInt("ES256EnumId"));
            newValues.put(HS256, rs.getInt("HS256EnumId"));
        } catch (SQLException _) {
        }
        enumToVal.putAll(newValues);
        enumToVal.forEach((k, v) -> valToEnum.put(v, k));
    }

    public static AlgorithmEnum fromInt(Integer enumId) {
        return valToEnum.get(enumId);
    }

    public static AlgorithmEnum fromString(String algorithm) {
        return switch (algorithm) {
            case "RS256" -> RS256;
            case "ES256" -> ES256;
            case "HS256" -> HS256;
            default -> throw new IllegalArgumentException("Invalid algorithm given");
        };
    }

    public JwsAlgorithm toJwsAlgorithm() {
        return switch (this) {
            case HS256 -> MacAlgorithm.HS256;
            case ES256 -> SignatureAlgorithm.ES256;
            case RS256 -> SignatureAlgorithm.RS256;
        };
    }

    public Integer getValue() {
        return enumToVal.get(this);
    }
}
