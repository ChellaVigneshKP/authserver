package com.chellavignesh.authserver.adminportal.controller;

import com.chellavignesh.authserver.adminportal.application.exception.AppNotFoundException;
import com.chellavignesh.authserver.adminportal.credential.CredentialService;
import com.chellavignesh.authserver.adminportal.credential.CredentialStatus;
import com.chellavignesh.authserver.adminportal.credential.CredentialType;
import com.chellavignesh.authserver.adminportal.credential.dto.CreateCredentialRequestDto;
import com.chellavignesh.authserver.adminportal.credential.dto.CreateCredentialRequestDtoValidator;
import com.chellavignesh.authserver.adminportal.credential.dto.CredentialResponseDto;
import com.chellavignesh.authserver.adminportal.credential.dto.UpdateCredentialRequestDto;
import com.chellavignesh.authserver.adminportal.credential.entity.Credential;
import com.chellavignesh.authserver.adminportal.credential.entity.CredentialDao;
import com.chellavignesh.authserver.adminportal.credential.exception.*;
import com.chellavignesh.authserver.adminportal.organization.exception.OrgNotFoundException;
import com.chellavignesh.authserver.adminportal.util.EntityValidator;
import com.chellavignesh.authserver.enums.entity.AlgorithmEnum;
import com.chellavignesh.authserver.enums.entity.AuthFlowEnum;
import com.chellavignesh.authserver.security.PermissionsService;
import jakarta.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/organizations/{orgGuid}/applications/{appGuid}/credentials")
public class CredentialController {

    private static Logger log = LoggerFactory.getLogger(CredentialController.class);

    @Value("${server.base-path}")
    private String serverBasePath;

    private final CredentialService credentialService;
    private final EntityValidator entityValidator;
    private final CreateCredentialRequestDtoValidator credentialRequestDtoValidator;

    // This is used in Spring EL expressions. Please do not remove.
    private final PermissionsService permissionsService;

    @Autowired
    public CredentialController(CredentialService credentialService, EntityValidator entityValidator, CreateCredentialRequestDtoValidator credentialRequestDtoValidator, PermissionsService permissionsService) {
        this.credentialService = credentialService;
        this.entityValidator = entityValidator;
        this.credentialRequestDtoValidator = credentialRequestDtoValidator;
        this.permissionsService = permissionsService;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.setValidator(credentialRequestDtoValidator);
    }

    // ---------------- CREATE CREDENTIAL ----------------

    @PostMapping
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) and hasPermission('idp-admin-app', 'update')")
    public ResponseEntity<?> createCredential(@Valid @PathVariable UUID orgGuid, @Valid @PathVariable UUID appGuid, @Valid @RequestBody CreateCredentialRequestDto dto, BindingResult bindingResult) throws CredentialCreationFailedException, TooManyCredentialsException, OrgNotFoundException, AppNotFoundException, CreateCredentialBadRequestException, CredentialDuplicateNameException, CertificateDoesNotExistException, NoActiveCertificateException {

        if (bindingResult.hasErrors()) {
            String message = "Error in field " + bindingResult.getFieldError().getField() + ": " + bindingResult.getFieldError().getDefaultMessage();
            throw new CreateCredentialBadRequestException(message);
        }

        dto.setOrgId(entityValidator.validateOrganizationId(orgGuid));
        dto.setAppId(entityValidator.validateApplicationId(dto.getOrgId(), appGuid));
        dto.setAlgorithmEnum(AlgorithmEnum.fromString(dto.getAlgorithm().toUpperCase()));
        dto.setCredentialStatus(CredentialStatus.fromString(StringUtils.defaultIfBlank(dto.getStatus(), "Active")));

        CredentialType credentialType = CredentialType.valueOf(dto.getType().toUpperCase());

        switch (credentialType) {
            case SHARED_SECRET -> dto.setAuthFlow(AuthFlowEnum.CLIENT_SECRET_JWT);
            case PRIVATE_KEY -> dto.setAuthFlow(AuthFlowEnum.PRIVATE_KEY_JWT);
        }

        CredentialDao credential;

        if (credentialType == CredentialType.SHARED_SECRET) {
            credential = credentialService.createSharedSecretCredential(dto);
        } else {
            credential = credentialService.createPrivateKeyCredential(dto);
        }

        if (credential == null) {
            throw new CredentialCreationFailedException("Secret creation failed.");
        }

        var response = ResponseEntity.status(HttpStatus.CREATED).header("Location", serverBasePath + "/api/v1/organizations/" + orgGuid + "/applications/" + appGuid + "/credentials/" + credential.getCredential().getRowGuid());

        var data = new HashMap<String, String>();
        data.put("id", credential.getCredential().getRowGuid().toString());

        if (credentialType == CredentialType.SHARED_SECRET) {
            data.put("secretValue", credential.getSecretValue());
        }

        return response.body(data);
    }

    // ---------------- UPDATE CREDENTIAL ----------------

    @PutMapping("/{credGuid}")
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) and hasPermission('idp-admin-app', 'update')")
    public ResponseEntity<?> updateCredential(@Valid @PathVariable UUID orgGuid, @Valid @PathVariable UUID appGuid, @Valid @PathVariable UUID credGuid, @Valid @RequestBody UpdateCredentialRequestDto dto, BindingResult bindingResult) throws CredentialUpdateFailedException, TooManyCredentialsException, OrgNotFoundException, AppNotFoundException, CreateCredentialBadRequestException, CredentialExpiredException, CredentialNotFoundException, CredentialDuplicateNameException {

        if (bindingResult.hasErrors()) {
            String message = "Error in field " + bindingResult.getFieldError().getField() + ": " + bindingResult.getFieldError().getDefaultMessage();
            throw new CreateCredentialBadRequestException(message);
        }

        Integer orgId = entityValidator.validateOrganizationId(orgGuid);

        credentialService.updateCredential(dto, orgId, entityValidator.validateApplicationId(orgId, appGuid), credGuid);

        return ResponseEntity.ok(dto);
    }

    // ---------------- GET CREDENTIALS ----------------

    @GetMapping
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) and hasPermission('idp-admin-app', 'read')")
    public ResponseEntity<?> getCredentials(@Valid @PathVariable UUID orgGuid, @Valid @PathVariable UUID appGuid) throws OrgNotFoundException, AppNotFoundException {

        Integer orgId = entityValidator.validateOrganizationId(orgGuid);

        List<Credential> credentials = credentialService.getAll(orgId, entityValidator.validateApplicationId(orgId, appGuid));

        List<CredentialResponseDto> response = credentials.stream().map(CredentialResponseDto::fromCredential).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // ---------------- DELETE CREDENTIAL ----------------

    @DeleteMapping("/{credGuid}")
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) and hasPermission('idp-admin-app', 'update')")
    public ResponseEntity<?> deleteCredential(@Valid @PathVariable UUID orgGuid, @Valid @PathVariable UUID appGuid, @Valid @PathVariable UUID credGuid) throws OrgNotFoundException, AppNotFoundException, CredentialNotFoundException, CredentialUpdateFailedException, CredentialNotExpiredException, ParseException {

        Integer orgId = entityValidator.validateOrganizationId(orgGuid);
        Integer appId = entityValidator.validateApplicationId(orgId, appGuid);

        boolean wasDeleted = credentialService.deleteCredential(orgId, appId, credGuid);

        if (!wasDeleted) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Credential not found");
        }

        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
    }

    // ---------------- EXCEPTION HANDLERS ----------------

    @ExceptionHandler({OrgNotFoundException.class, AppNotFoundException.class})
    public ResponseEntity<String> handleNotFoundException(Exception e) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler({CreateCredentialBadRequestException.class, CredentialExpiredException.class, CertificateDoesNotExistException.class, NoActiveCertificateException.class})
    public ResponseEntity<String> handleBadRequestException(Exception e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler({TooManyCredentialsException.class, CredentialDuplicateNameException.class})
    public ResponseEntity<String> handleTooManyCredentialsException(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    @ExceptionHandler(CredentialCreationFailedException.class)
    public ResponseEntity<String> handleCredentialCreationFailedException(Exception e) {
        return ResponseEntity.internalServerError().body(e.getMessage());
    }
}

