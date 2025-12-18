package com.chellavignesh.authserver.adminportal.user.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.ResultSet;
import java.sql.SQLException;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Profile {

    private Integer id;
    private String name;
    private String email;
    private String phoneNumber;

    public static Profile fromResult(ResultSet result) throws SQLException {
        try {
            Profile profile = new Profile();
            profile.setId(result.getInt("OrganizationId"));
            profile.setName(result.getString("Name"));
            profile.setEmail(result.getString("Email"));
            profile.setPhoneNumber(result.getString("PhoneNumber"));
            return profile;
        } catch (SQLException _) {
            return null;
        }
    }
}
