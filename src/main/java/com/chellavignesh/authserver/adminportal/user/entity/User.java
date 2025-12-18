package com.chellavignesh.authserver.adminportal.user.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class User {

    private Integer id;
    private String firstName;
    private String lastName;
    private String username;
    private String password;
    private String orgId;
    private String groupId;
    private Map<String, Object> metaData;
    private UUID rowGuid;
    private UUID loginId;

    public static User fromResult(ResultSet result) throws SQLException {
        User user = new User();
        try {
            user.setId(result.getInt("ProfileId"));
            user.setFirstName(result.getString("FirstName"));
            user.setLastName(result.getString("LastName"));
            user.setRowGuid(UUID.fromString(result.getString("RowGuid")));
            user.setLoginId(UUID.fromString(result.getString("LoginId")));
        } catch (SQLException e) {
            user = null;
        }
        return user;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("firstName", firstName)
                .append("lastName", lastName)
                .append("username", username)
                .append("orgId", orgId)
                .append("groupId", groupId)
                .append("rowGuid", rowGuid)
                .append("loginId", loginId)
                .toString();
    }
}
