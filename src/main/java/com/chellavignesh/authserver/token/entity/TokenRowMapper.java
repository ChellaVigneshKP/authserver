package com.chellavignesh.authserver.token.entity;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class TokenRowMapper implements RowMapper<Token> {
    public Token mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Token.fromResult(rs);
    }
}
