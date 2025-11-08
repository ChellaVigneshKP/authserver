package com.chellavignesh.authserver.adminportal.forgotusername.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsernameLookupField {

    private Integer id;
    private String name;
    private String description;

    public static UsernameLookupField fromResult(ResultSet rs) throws SQLException {
        UsernameLookupField settings = new UsernameLookupField();
        settings.setId(rs.getInt("EnumId"));
        settings.setName(rs.getString("Code"));
        settings.setDescription(rs.getString("Description"));
        return settings;
    }
}
