package com.chellavignesh.authserver.adminportal.organization.entity;

import com.chellavignesh.authserver.adminportal.organization.OrganizationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Organization {
    private Integer id;
    private UUID rowGuid;
    private String name;
    private String description;
    private OrganizationStatus status;
    private Contact primaryContact;
    private Contact secondaryContact;

    public static Organization fromResult(ResultSet result) throws SQLException {
        Organization organization = new Organization();
        try {
            organization.setId(result.getInt("OrganizationId"));
            organization.setRowGuid(UUID.fromString(result.getString("RowGuid")));
            organization.setName(result.getString("Name"));
            organization.setDescription(result.getString("Note"));
            organization.setStatus(OrganizationStatus.fromByte(result.getByte("Status")));
            organization.setPrimaryContact(createContact(
                    result.getString("PrimaryContactName"),
                    result.getString("PrimaryContactEmail"),
                    result.getString("PrimaryContactPhoneNumber")
            ));
            organization.setSecondaryContact(createContact(
                    result.getString("SecondaryContactName"),
                    result.getString("SecondaryContactEmail"),
                    result.getString("SecondaryContactPhoneNumber")
            ));
            return organization;
        } catch (SQLException e) {
            return null;
        }
    }

    public static final Contact createContact(String name, String email, String phone) {
        return isEmptyContact(name, email, phone) ? null : new Contact(name, email, phone);
    }

    public static boolean isEmptyContact(String name, String email, String phone) {
        return name == null && email == null && phone == null;
    }
}
