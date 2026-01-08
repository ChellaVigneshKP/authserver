package com.chellavignesh.authserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * CommandLineRunner to initialize the admin portal setup.
 * This runner executes when the environment variable INITIALIZE_AUTHSERVER is set to true.
 * It creates the necessary database structure for the admin portal including:
 * - Admin Organization
 * - Admin Application (OAuth2 Client)
 * - Admin Group with permissions
 * - Admin Profile structure
 */
@Component
@Slf4j
public class AdminPortalInitializer implements CommandLineRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Value("${admin.portal.initialize:false}")
    private boolean initializeAuthServer;

    @Autowired
    public AdminPortalInitializer(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        if (!initializeAuthServer) {
            log.info("Admin portal initialization skipped. Set admin.portal.initialize=true to enable.");
            return;
        }

        log.info("=".repeat(60));
        log.info("Starting Admin Portal Initialization");
        log.info("=".repeat(60));

        try {
            // Execute the initialization script
            ClassPathResource resource = new ClassPathResource("db/init/001_InitAdminPortal.sql");
            
            try (Connection connection = dataSource.getConnection()) {
                ScriptUtils.executeSqlScript(connection, resource);
                log.info("Admin portal initialization script executed successfully");
            }

            log.info("=".repeat(60));
            log.info("Admin Portal Initialization Complete");
            log.info("=".repeat(60));
            log.info("");
            log.info("Next Steps:");
            log.info("1. Generate a client secret for the Admin Portal application");
            log.info("2. Create an admin user with proper credentials");
            log.info("3. Configure your Next.js frontend with the client credentials");
            log.info("");
            log.info("For detailed instructions, refer to ADMIN_PORTAL_SETUP.md");
            log.info("=".repeat(60));

        } catch (Exception e) {
            log.error("Failed to initialize admin portal", e);
            log.error("Please check your database connection and ensure the database schema is up to date");
            // Don't fail application startup if initialization fails
            // This allows the application to start and the issue to be debugged
        }
    }
}
