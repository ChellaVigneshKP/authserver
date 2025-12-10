package com.chellavignesh.authserver.adminportal.user.entity;

import com.chellavignesh.authserver.adminportal.user.UserStatus;
import com.chellavignesh.authserver.enums.entity.SuffixType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public record UserDetails(
        Integer id,
        String firstName,
        String lastName,
        String username,
        UUID rowGuid,
        String email,
        String title,
        String middleInitial,
        String suffix,
        String phoneNumber,
        Boolean twoFactorEnabled,
        UUID groupId,
        String groupName,
        UUID memberId,
        UUID loginId,
        UUID orgId,
        Integer results,
        UserStatus status,
        String branding,
        String secondaryPhoneNumber,
        UUID profileOrgId,
        Boolean credSyncFlag,
        Boolean profileSyncFlag
) {

    public static UserDetails fromResult(ResultSet result) throws SQLException {

        return new UserDetails(
                result.getInt("ProfileId"),
                result.getString("FirstName"),
                result.getString("LastName"),
                result.getString("UserName"),
                UUID.fromString(result.getString("RowGuid")),
                result.getString("Email"),
                result.getString("Title"),
                result.getString("MiddleInitial"),
                SuffixType.fromInt(result.getInt("Suffix")) != null ? SuffixType.fromInt(result.getInt("Suffix")).toString() : "",
                result.getString("PhoneNumber"),
                result.getBoolean("TwoFactorEnabled"),
                result.getString("GroupGuid") != null ? UUID.fromString(result.getString("GroupGuid")) : null,
                result.getString("GroupName"),
                UUID.fromString(result.getString("RowGuid")),
                UUID.fromString(result.getString("LoginId")),
                UUID.fromString(result.getString("OrgId")),
                result.getInt("Results"),
                UserStatus.fromInt(result.getInt("Status")),
                result.getString("Branding"),
                result.getString("SecondaryPhoneNumber"),
                UUID.fromString(result.getString("ProfileOrgGuid")),
                getOptionalBoolean(result, "CredSyncFlag"),
                getOptionalBoolean(result, "SyncFlag")
        );
    }

    private static boolean getOptionalBoolean(ResultSet rs, String columnName) throws SQLException {
        try {
            return rs.getBoolean(columnName);
        } catch (SQLException _) {
            return false;
        }
    }
}
