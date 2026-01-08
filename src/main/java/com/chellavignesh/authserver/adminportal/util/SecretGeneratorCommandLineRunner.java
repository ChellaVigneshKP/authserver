package com.chellavignesh.authserver.adminportal.util;

import com.chellavignesh.authserver.adminportal.application.ApplicationRepository;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.adminportal.credential.CredentialService;
import com.chellavignesh.authserver.adminportal.credential.CredentialStatus;
import com.chellavignesh.authserver.adminportal.credential.dto.CreateCredentialRequestDto;
import com.chellavignesh.authserver.adminportal.credential.entity.CredentialDao;
import com.chellavignesh.authserver.enums.entity.AlgorithmEnum;
import com.chellavignesh.authserver.enums.entity.AuthFlowEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
import java.util.Scanner;

/**
 * Command Line Runner for generating secrets for OAuth applications.
 * This runner allows administrators to generate secrets for applications without requiring API authentication.
 * 
 * To enable this runner, add the following property to your application.properties:
 * secret.generator.enabled=true
 * 
 * Usage:
 * When the application starts, the runner will prompt for a client ID.
 * Enter the client ID of the application for which you want to generate a secret.
 * The runner will generate a secret and display it on the console.
 */
@Component
@ConditionalOnProperty(name = "secret.generator.enabled", havingValue = "true", matchIfMissing = false)
public class SecretGeneratorCommandLineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SecretGeneratorCommandLineRunner.class);

    private final ApplicationRepository applicationRepository;
    private final CredentialService credentialService;

    public SecretGeneratorCommandLineRunner(ApplicationRepository applicationRepository, CredentialService credentialService) {
        this.applicationRepository = applicationRepository;
        this.credentialService = credentialService;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("==========================================================");
        log.info("Secret Generator Command Line Runner is enabled");
        log.info("==========================================================");

        Scanner scanner = new Scanner(System.in);
        
        log.info("Enter the Client ID of the application (or 'exit' to quit): ");
        String clientId = scanner.nextLine().trim();

        if ("exit".equalsIgnoreCase(clientId)) {
            log.info("Exiting Secret Generator...");
            return;
        }

        if (clientId.isEmpty()) {
            log.error("Client ID cannot be empty. Exiting...");
            return;
        }

        try {
            // Find the application by client ID
            Optional<Application> applicationOpt = applicationRepository.getByClientId(clientId);

            if (applicationOpt.isEmpty()) {
                log.error("Application with Client ID '{}' not found.", clientId);
                log.error("Please verify the Client ID and try again.");
                return;
            }

            Application application = applicationOpt.get();
            log.info("Found application: {}", application.getName());
            log.info("Organization ID: {}", application.getOrgId());
            log.info("Application ID: {}", application.getId());
            log.info("Auth Flow: {}", application.getAuthFlow());

            // Verify the application supports CLIENT_SECRET_JWT
            if (application.getAuthFlow() != AuthFlowEnum.CLIENT_SECRET_JWT) {
                log.error("Application does not support shared secret authentication.");
                log.error("Application Auth Flow: {}", application.getAuthFlow());
                log.error("Only applications with CLIENT_SECRET_JWT auth flow can have shared secrets.");
                return;
            }

            log.info("Enter a name for the credential (default: 'Generated Secret'): ");
            String credentialName = scanner.nextLine().trim();
            if (credentialName.isEmpty()) {
                credentialName = "Generated Secret";
            }

            log.info("Enter a description for the credential (optional): ");
            String description = scanner.nextLine().trim();
            if (description.isEmpty()) {
                description = "Generated via Command Line Runner";
            }

            // Create credential request DTO
            CreateCredentialRequestDto dto = new CreateCredentialRequestDto();
            dto.setOrgId(application.getOrgId());
            dto.setAppId(application.getId());
            dto.setName(credentialName);
            dto.setDescription(description);
            dto.setType("SHARED_SECRET");
            dto.setAlgorithm("ES256");
            dto.setAlgorithmEnum(AlgorithmEnum.ES256);
            dto.setAuthFlow(AuthFlowEnum.CLIENT_SECRET_JWT);
            dto.setCredentialStatus(CredentialStatus.Active);

            // Set expiration to 1 year from now
            Calendar c = Calendar.getInstance();
            c.setTime(new Date());
            c.add(Calendar.YEAR, 1);
            dto.setExpireOn(c.getTime());

            log.info("Generating secret for application: {}", application.getName());

            // Generate the secret
            CredentialDao credentialDao = credentialService.createSharedSecretCredential(dto);

            log.info("==========================================================");
            log.info("Secret generated successfully!");
            log.info("==========================================================");
            log.info("Application Name: {}", application.getName());
            log.info("Client ID: {}", clientId);
            log.info("Credential ID: {}", credentialDao.getCredential().getRowGuid());
            log.info("Credential Name: {}", credentialName);
            log.info("Expires On: {}", dto.getExpireOn());
            log.info("==========================================================");
            log.info("SECRET VALUE (save this securely, it will not be shown again):");
            log.info("{}", credentialDao.getSecretValue());
            log.info("==========================================================");

        } catch (Exception e) {
            log.error("Error generating secret: {}", e.getMessage(), e);
            log.error("Please check the logs for more details.");
        } finally {
            scanner.close();
        }
    }
}
