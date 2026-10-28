package com.chellavignesh.authserver.database;

import com.chellavignesh.authserver.support.ManagementHttpTestSupport;
import com.chellavignesh.authserver.adminportal.user.dto.CreateUserDto;
import com.chellavignesh.authserver.adminportal.user.entity.User;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import java.util.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/**
 * Truthfulness of the pre-existing provider sync branch on the management email route.
 * The seeded local "idp" external type has no SyncUserSchema, which is the real
 * unconfigured-provider condition; no provider response is intercepted or simulated.
 */
@EnabledIfEnvironmentVariable(named="AUTH_APP_IT", matches="true")
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "app.local.bootstrap.enabled=false", "toggles.signature.required=true",
        "toggles.account.sync.type=sync",
        "logging.level.root=WARN", "logback.access.enabled=false"})
class UserEmailSyncFailureContractTest extends ManagementHttpTestSupport {
    static final OwnedDatabase database=new OwnedDatabase();
    @DynamicPropertySource static void isolate(DynamicPropertyRegistry registry) { database.register(registry); }
    Fixture platform;
    @BeforeAll void fixtures() throws Exception {
        orgA=organization("Sync failure A"); app=application(orgA);
        redirects.createRedirectUris(orgA.getId(),app.getId(),List.of("http://localhost/callback"));
        platform=token(user(organizations.getById(1).orElseThrow(),1,"sync-platform"));
    }
    @AfterAll void cleanup() { cleanup(database); }

    User syncProfile(String name) throws Exception {
        var dto=new CreateUserDto();
        dto.setOrgId(orgA.getId()); dto.setFirstName("Sync"); dto.setLastName(name);
        dto.setUsername(name); dto.setEmail(name+"@example.test"); dto.setPhoneNumber("+19999999999");
        dto.setSyncFlag(true); dto.setMetaData(Map.of("marker",name));
        return users.create(dto,"0011223344556677",1,0,UUID.fromString("11111111-1111-1111-1111-111111111111"),12);
    }
    String email(User user) {
        return sql.queryForObject("SELECT Email FROM Person.Profile WHERE RowGuid=?",String.class,user.getRowGuid());
    }
    String checksum(User user) {
        return sql.queryForObject("SELECT CAST(CHECKSUM_AGG(BINARY_CHECKSUM(*)) AS VARCHAR(32)) FROM Person.Profile WHERE RowGuid=?",String.class,user.getRowGuid());
    }
    String body(String email,String branding) {
        return "{\"email\":\""+email+"\",\"branding\":\""+branding+"\",\"intent\":\"idp\",\"metadata\":{}}";
    }

    @Test void unconfiguredSyncProviderIsReportedAsUpstreamFailureWithoutProfileWrite() throws Exception {
        var target=syncProfile("sync-"+UUID.randomUUID());
        String beforeEmail=email(target), beforeChecksum=checksum(target);
        var response=call(platform,"PUT","/api/v1/users/"+target.getRowGuid()+"/email",body("sync-failure@example.test","idp"));
        assertThat(response.statusCode()).isEqualTo(502);
        var problem=mapper.readTree(response.body());
        assertThat(problem.path("code").asText()).isEqualTo("UPSTREAM_FAILURE");
        assertThat(problem.path("retryable").asBoolean()).isFalse();
        assertThat(problem.path("requestId").asText()).isNotBlank();
        assertThat(response.body()).doesNotContain("Invalid Sync schema");
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(email(target)).isEqualTo(beforeEmail);
        assertThat(checksum(target)).isEqualTo(beforeChecksum);
    }

    @Test void unknownBrandingStaysAClientValidationRejection() throws Exception {
        var target=syncProfile("unknown-"+UUID.randomUUID());
        String beforeEmail=email(target);
        var response=call(platform,"PUT","/api/v1/users/"+target.getRowGuid()+"/email",body("unknown-brand@example.test","absent-"+UUID.randomUUID()));
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(email(target)).isEqualTo(beforeEmail);
    }
}
