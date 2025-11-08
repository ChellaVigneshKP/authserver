package com.chellavignesh.authserver.enums.entity;

import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

public enum TokenTypeEnum {
    ACCESS_TOKEN, REFRESH_TOKEN, ID_TOKEN, CODE;

    private static final Map<TokenTypeEnum, Integer> enumToVal = new HashMap<>();
    private static final Map<Integer, TokenTypeEnum> valToEnum = new HashMap<>();

    public static void setValues(ResultSet rs) {
        enumToVal.clear();
        valToEnum.clear();
        var newValues = new HashMap<TokenTypeEnum, Integer>();
        try {
            newValues.put(ACCESS_TOKEN, rs.getInt("AccessTokenEnumId"));
            newValues.put(REFRESH_TOKEN, rs.getInt("RefreshTokenEnumId"));
            newValues.put(ID_TOKEN, rs.getInt("IdTokenEnumId"));
            newValues.put(CODE, rs.getInt("AuthCodeTokenEnumId"));
        } catch (Exception _) {
            return;
        }
        enumToVal.putAll(newValues);
        enumToVal.forEach((k, v) -> valToEnum.put(v, k));
    }

    public static TokenTypeEnum fromOAuth2TokenType(OAuth2TokenType tokenType) {
        if (tokenType == OAuth2TokenType.ACCESS_TOKEN) {
            return TokenTypeEnum.ACCESS_TOKEN;
        } else if (tokenType == OAuth2TokenType.REFRESH_TOKEN) {
            return TokenTypeEnum.REFRESH_TOKEN;
        } else if ("code".equals(tokenType.getValue())) {
            return TokenTypeEnum.CODE;
        }
        return null;
    }

    public static TokenTypeEnum fromInt(Integer enumId) {
        return valToEnum.get(enumId);
    }

    public Integer getValue() {
        return enumToVal.get(this);
    }
}
