package com.chellavignesh.authserver.adminportal.user;

import com.chellavignesh.authserver.adminportal.metadata.dto.OutgoingMetadataDto;
import com.chellavignesh.authserver.adminportal.user.dto.UserPasswordSyncPayload;
import com.chellavignesh.authserver.adminportal.user.entity.CredentialIdentity;
import com.chellavignesh.authserver.adminportal.user.entity.UserDetails;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The downstream password sync payload dereferences the target organization, so the management
 * identity must supply one. This pins the dereference site directly; it does not stand in for a
 * SYNC-configured provider round trip.
 */
class OnPremAccountSyncPayloadTest {

    private static final UUID PROFILE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORGANIZATION = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID LOGIN = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void passwordSyncPayloadCarriesTheIdentityOrganizationForBothStatuses() throws Exception {
        assertThat(passwordSyncPayload(UserStatus.Active).getOrgId()).isEqualTo(ORGANIZATION.toString());
        assertThat(passwordSyncPayload(UserStatus.Inactive).getOrgId()).isEqualTo(ORGANIZATION.toString());
    }

    @Test
    void passwordSyncPayloadCarriesLoginAndMemberIdentity() throws Exception {
        var payload = passwordSyncPayload(UserStatus.Inactive);

        assertThat(payload.getCredential().getLoginId()).isEqualTo(LOGIN);
        assertThat(payload.getProfile().getMemberId()).isEqualTo(PROFILE);
        assertThat(payload.getBranding()).isEqualTo("idp");
    }

    @Test
    void passwordSyncPayloadFailsClosedWithoutAnOrganization() {
        var details = new UserDetails(1, "First", "Last", "user", PROFILE, "user@example.test", null, null, "",
                null, false, null, null, PROFILE, LOGIN, null, 1, UserStatus.Active, "idp", null, null, true, false);

        assertThatThrownBy(() -> buildPayload(details)).isInstanceOf(NullPointerException.class);
    }

    private static UserPasswordSyncPayload passwordSyncPayload(UserStatus status) throws Exception {
        return buildPayload(identity(status).toUserDetails());
    }

    private static CredentialIdentity identity(UserStatus status) {
        return new CredentialIdentity(1, PROFILE, ORGANIZATION, LOGIN, "user", "idp", "First", "Last",
                "user@example.test", status, true);
    }

    private static UserPasswordSyncPayload buildPayload(UserDetails details) {
        OnPremAccountServiceClient client = new OnPremAccountServiceClient(null, null, null);
        try {
            Method builder = OnPremAccountServiceClient.class.getDeclaredMethod("createPasswordSyncPayload",
                    UserDetails.class, String.class, String.class, OutgoingMetadataDto.class);
            builder.setAccessible(true);
            return (UserPasswordSyncPayload) builder.invoke(client, details, "synthetic-password", "idp", null);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(ex);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
