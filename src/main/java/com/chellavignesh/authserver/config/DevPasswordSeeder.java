package com.chellavignesh.authserver.config;

import com.chellavignesh.authserver.adminportal.util.KPCVEncoder;
import com.chellavignesh.authserver.session.HasherConfig;
import com.chellavignesh.authserver.session.KPCVPasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Types;

/**
 * Seeds the admin user password on startup for local development.
 * Username: acsauthadmin / Password: Admin@1234
 */
@Component
public class DevPasswordSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevPasswordSeeder.class);
    private static final String ADMIN_USERNAME = "acsauthadmin";
    private static final String ADMIN_PASSWORD = "Admin@1234";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final HasherConfig hasherConfig;

    public DevPasswordSeeder(NamedParameterJdbcTemplate jdbcTemplate, HasherConfig hasherConfig) {
        this.jdbcTemplate = jdbcTemplate;
        this.hasherConfig = hasherConfig;
    }

    @Override
    public void run(String... args) {
        try {
            KPCVEncoder encoder = new KPCVEncoder(hasherConfig);
            byte[] hashedPassword = encoder.getHashPassword(ADMIN_PASSWORD);

            int updated = jdbcTemplate.update(
                    "UPDATE [Person].[Credential] SET [Password] = :password, [Version] = :version WHERE [UserName] = :username",
                    new MapSqlParameterSource()
                            .addValue("password", hashedPassword, Types.VARBINARY)
                            .addValue("version", KPCVPasswordEncoder.ENCODER_ID)
                            .addValue("username", ADMIN_USERNAME)
            );

            if (updated > 0) {
                log.info("Admin password seeded successfully for user '{}'", ADMIN_USERNAME);
            } else {
                log.warn("No rows updated — user '{}' may not exist in Person.Credential", ADMIN_USERNAME);
            }
        } catch (Exception e) {
            log.error("Failed to seed admin password", e);
        }
    }
}
