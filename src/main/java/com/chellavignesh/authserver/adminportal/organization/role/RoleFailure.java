package com.chellavignesh.authserver.adminportal.organization.role;

/** Safe HTTP status for the shared portal error boundary; internal SQL details never escape. */
public class RoleFailure extends RuntimeException {
    private final int status;
    private final String code;

    public RoleFailure(int status) { this(status, null, "Role request rejected"); }

    private RoleFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static RoleFailure assigned(int count) {
        return new RoleFailure(409, "ROLE_ASSIGNED", "Role has " + count
                + " assigned members. Remove assignments before deactivating.");
    }

    public static RoleFailure legacyPlatformRole() {
        return new RoleFailure(409, "ROLE_REQUIRES_REVIEW",
                "Legacy role contains platform-reserved permissions and requires review before modification.");
    }

    public String code() { return code; }
    public int status() { return status; }
}
