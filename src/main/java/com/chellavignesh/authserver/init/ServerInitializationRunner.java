package com.chellavignesh.authserver.init;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.application.dto.CreateApplicationDto;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.adminportal.externalsource.ExternalSourceService;
import com.chellavignesh.authserver.adminportal.externalsource.entity.ExternalSource;
import com.chellavignesh.authserver.adminportal.organization.OrganizationService;
import com.chellavignesh.authserver.adminportal.organization.dto.CreateOrganizationDto;
import com.chellavignesh.authserver.adminportal.organization.entity.Organization;
import com.chellavignesh.authserver.adminportal.user.UserRepository;
import com.chellavignesh.authserver.adminportal.user.UserService;
import com.chellavignesh.authserver.adminportal.user.dto.CreateUserDto;
import com.chellavignesh.authserver.adminportal.user.entity.User;
import com.chellavignesh.authserver.enums.entity.ApplicationTypeEnum;
import com.chellavignesh.authserver.enums.entity.AuthFlowEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Server initialization runner that creates the first admin user, organization, and client
 * when the INITIALIZE_SERVER environment variable is set to "true".
 */
@Component
@Slf4j
public class ServerInitializationRunner implements CommandLineRunner {

    private static final String DEFAULT_ORG_NAME = "Default Organization";
    private static final String DEFAULT_ORG_DESCRIPTION = "Default organization created during server initialization";
    private static final String DEFAULT_APP_NAME = "Default Client Application";
    private static final String DEFAULT_APP_DESCRIPTION = "Default client application created during server initialization";
    private static final String DEFAULT_USERNAME = "chella";
    private static final String DEFAULT_ADMIN_FIRSTNAME = "Admin";
    private static final String DEFAULT_ADMIN_LASTNAME = "User";
    private static final String DEFAULT_ADMIN_EMAIL = "chella@example.com";
    private static final String DEFAULT_ADMIN_PHONE = "+11234567890";
    private static final String DEFAULT_BRANDING = "default";
    private static final int DEFAULT_EXTERNAL_TYPE_ID = 1;

    @Value("${server.initialize:false}")
    private boolean initializeServer;

    @Value("${server.initialize.admin.password:Admin@123456}")
    private String defaultAdminPassword;

    private final OrganizationService organizationService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final ApplicationService applicationService;
    private final ExternalSourceService externalSourceService;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    public ServerInitializationRunner(
            OrganizationService organizationService,
            UserService userService,
            UserRepository userRepository,
            ApplicationService applicationService,
            ExternalSourceService externalSourceService,
            NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.organizationService = organizationService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.applicationService = applicationService;
        this.externalSourceService = externalSourceService;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    @Override
    public void run(String... args) {
        // Check if initialization is requested via environment variable or property
        String envInitialize = System.getenv("INITIALIZE_SERVER");
        boolean shouldInitialize = "true".equalsIgnoreCase(envInitialize) || initializeServer;

        if (!shouldInitialize) {
            log.info("Server initialization skipped. Set INITIALIZE_SERVER=true to initialize.");
            return;
        }

        log.info("Starting server initialization...");

        try {
            // Check if already initialized
            if (isAlreadyInitialized()) {
                log.info("Server already initialized. Skipping initialization.");
                return;
            }

            // Step 1: Ensure external source (branding) exists
            ExternalSource externalSource = ensureExternalSource();
            log.info("External source ensured: {}", externalSource.getSourceCode());

            // Step 2: Create organization
            Organization organization = createOrganization();
            log.info("Organization created: {} (ID: {})", organization.getName(), organization.getId());

            // Step 3: Create admin user
            User adminUser = createAdminUser(organization, externalSource);
            log.info("Admin user created: {} (ID: {})", adminUser.getUsername(), adminUser.getId());

            // Step 4: Create default application/client
            Application application = createApplication(organization);
            log.info("Application created: {} (ID: {})", application.getName(), application.getId());

            log.info("Server initialization completed successfully!");
            log.info("=================================================");
            log.info("Default Admin User Details:");
            log.info("  Username: {}", DEFAULT_USERNAME);
            log.info("  Email: {}", DEFAULT_ADMIN_EMAIL);
            log.info("=================================================");
            log.warn("IMPORTANT: Please change the default admin password immediately!");
            log.warn("For security reasons, the password is not logged. Check your configuration or documentation for the default password.");

        } catch (Exception e) {
            log.error("Server initialization failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize server", e);
        }
    }

    /**
     * Check if the server has already been initialized by checking for the default admin user.
     */
    private boolean isAlreadyInitialized() {
        try {
            Optional<User> existingUser = userRepository.getUserByUsername(DEFAULT_USERNAME);
            return existingUser.isPresent();
        } catch (Exception e) {
            log.debug("Error checking initialization status: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Ensure external source exists or create one if necessary.
     * In production, external sources should be pre-configured in the database.
     * This method checks if the default branding exists.
     */
    private ExternalSource ensureExternalSource() throws Exception {
        Optional<ExternalSource> existingSource = externalSourceService.findBySourceCode(DEFAULT_BRANDING);
        
        if (existingSource.isPresent()) {
            log.info("External source '{}' already exists", DEFAULT_BRANDING);
            return existingSource.get();
        }

        // If no default branding exists, try to create one via direct SQL
        // Note: This assumes the ExternalSource table structure exists
        log.info("Creating default external source '{}'", DEFAULT_BRANDING);
        
        UUID sourceId = UUID.randomUUID();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("SourceId", sourceId)
                .addValue("SourceCode", DEFAULT_BRANDING)
                .addValue("SyncFlag", false)
                .addValue("ExternalTypeId", DEFAULT_EXTERNAL_TYPE_ID);

        try {
            namedParameterJdbcTemplate.update(
                    "INSERT INTO dbo.ExternalSource (SourceId, SourceCode, SyncFlag, ExternalTypeId) " +
                    "VALUES (:SourceId, :SourceCode, :SyncFlag, :ExternalTypeId)",
                    parameters
            );
            
            // Verify creation
            Optional<ExternalSource> newSource = externalSourceService.findBySourceCode(DEFAULT_BRANDING);
            if (newSource.isPresent()) {
                return newSource.get();
            }
        } catch (Exception e) {
            log.warn("Could not create external source via SQL: {}", e.getMessage());
        }

        throw new Exception("Failed to ensure external source exists. Please configure branding in the database.");
    }

    /**
     * Create the default organization.
     */
    private Organization createOrganization() throws Exception {
        CreateOrganizationDto orgDto = new CreateOrganizationDto();
        orgDto.setName(DEFAULT_ORG_NAME);
        orgDto.setDescription(DEFAULT_ORG_DESCRIPTION);

        try {
            return organizationService.create(orgDto);
        } catch (Exception e) {
            log.error("Failed to create organization: {}", e.getMessage(), e);
            throw new Exception("Failed to create default organization", e);
        }
    }

    /**
     * Create the admin user with username "chella".
     */
    private User createAdminUser(Organization organization, ExternalSource externalSource) throws Exception {
        CreateUserDto userDto = new CreateUserDto();
        userDto.setFirstName(DEFAULT_ADMIN_FIRSTNAME);
        userDto.setLastName(DEFAULT_ADMIN_LASTNAME);
        userDto.setUsername(DEFAULT_USERNAME);
        userDto.setEmail(DEFAULT_ADMIN_EMAIL);
        userDto.setPhoneNumber(DEFAULT_ADMIN_PHONE);
        userDto.setPassword(defaultAdminPassword);
        userDto.setOrgGuid(organization.getGuid());
        userDto.setOrgId(organization.getId());
        userDto.setBranding(externalSource.getSourceCode());
        userDto.setSyncFlag(false);

        try {
            // Create user with group ID 0 (assuming it's admin group or will be assigned)
            // hashedPassword = false means the service will hash it
            return userService.create(userDto, 0, false);
        } catch (Exception e) {
            log.error("Failed to create admin user: {}", e.getMessage(), e);
            throw new Exception("Failed to create admin user", e);
        }
    }

    /**
     * Create the default application/client.
     */
    private Application createApplication(Organization organization) throws Exception {
        CreateApplicationDto appDto = new CreateApplicationDto();
        appDto.setName(DEFAULT_APP_NAME);
        appDto.setDescription(DEFAULT_APP_DESCRIPTION);
        appDto.setType(ApplicationTypeEnum.WEB);
        appDto.setAuthMethod(AuthFlowEnum.PKCE);
        appDto.setAllowForgotUsername(false);

        try {
            return applicationService.create(organization.getId(), appDto);
        } catch (Exception e) {
            log.error("Failed to create application: {}", e.getMessage(), e);
            throw new Exception("Failed to create default application", e);
        }
    }
}
