package com.chellavignesh.authserver.exception;

import com.chellavignesh.authserver.adminportal.application.exception.*;
import com.chellavignesh.authserver.adminportal.certificate.exception.CertificateNotFoundException;
import com.chellavignesh.authserver.adminportal.credential.exception.CredentialNotExpiredException;
import com.chellavignesh.authserver.adminportal.credential.exception.CredentialNotFoundException;
import com.chellavignesh.authserver.adminportal.forgotusername.exception.InvalidUserSessionException;
import com.chellavignesh.authserver.adminportal.organization.exception.*;
import com.chellavignesh.authserver.adminportal.resource.exception.ResourceLibraryCreationFailedException;
import com.chellavignesh.authserver.adminportal.user.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class RestExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(DataIntegrityViolationException.class)
    protected ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        logger.error("Data integrity violation: {}", ex.getMessage());
        ApiError apiError = new ApiError(HttpStatus.CONFLICT, ex.getLocalizedMessage());
        return new ResponseEntity<>(apiError, new HttpHeaders(), apiError.getStatus());
    }

    @ExceptionHandler(OrgNotFoundException.class)
    public ResponseEntity<?> handleOrgNotFoundException(OrgNotFoundException ex) {
        logger.error("Organization not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(OrgGroupNotFoundException.class)
    public ResponseEntity<?> handleOrgGroupNotFoundException(OrgGroupNotFoundException ex) {
        logger.error("Organization group not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(CertificateNotFoundException.class)
    public ResponseEntity<?> handleCertificateNotFoundException(CertificateNotFoundException ex) {
        logger.error("Certificate not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(OrgCreationFailedException.class)
    public ResponseEntity<?> handleOrgCreationFailedException(OrgCreationFailedException ex) {
        logger.error("Organization creation failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
    }

    @ExceptionHandler(OrgCreationBadRequestException.class)
    public ResponseEntity<?> handleOrgCreationBadRequestException(OrgCreationBadRequestException ex) {
        logger.error("Organization creation failed due to bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(AppNotFoundException.class)
    public ResponseEntity<?> handleAppNotFoundException(AppNotFoundException ex) {
        logger.error("Application not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(AppCreationBadRequestException.class)
    public ResponseEntity<?> handleAppCreationBadRequestException(AppCreationBadRequestException ex) {
        logger.error("Application creation failed due to bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(UserCreationBadRequestException.class)
    public ResponseEntity<?> handleUserCreationBadRequestException(UserCreationBadRequestException ex) {
        logger.error("User creation failed due to bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(AppCreationFailedException.class)
    public ResponseEntity<?> handleAppCreationFailedException(AppCreationFailedException ex) {
        logger.error("Application creation failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
    }

    @ExceptionHandler(ResourceLibraryCreationFailedException.class)
    public ResponseEntity<?> handleResourceLibraryCreationFailedException(ResourceLibraryCreationFailedException ex) {
        logger.error("Resource library creation failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
    }

    @ExceptionHandler(AppResourceNotFoundException.class)
    public ResponseEntity<?> handleAppResourceNotFoundException(AppResourceNotFoundException ex) {
        logger.error("Application resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(UserCreationFailedException.class)
    public ResponseEntity<?> handleUserCreationFailedException(UserCreationFailedException ex) {
        logger.error("User creation failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
    }

    @ExceptionHandler(UserUpdateBadRequestException.class)
    public ResponseEntity<?> handleUserUpdateBadRequestException(UserUpdateBadRequestException ex) {
        logger.error("User update failed due to bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(UserUpdateFailedException.class)
    public ResponseEntity<?> handleUserUpdateFailedException(UserUpdateFailedException ex) {
        logger.error("User update failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<?> handleUserNotFoundException(UserNotFoundException ex) {
        logger.error("User not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(UsernameExistsException.class)
    public ResponseEntity<?> handleUsernameExistsException(UsernameExistsException ex) {
        logger.error("Username already exists: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(UserDeleteFailedException.class)
    public ResponseEntity<?> handleUserDeleteFailedException(UserDeleteFailedException ex) {
        logger.error("User deletion failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
    }

    @ExceptionHandler(ResourceLibraryNotFoundException.class)
    public ResponseEntity<?> handleResourceLibraryNotFoundException(ResourceLibraryNotFoundException ex) {
        logger.error("Resource library not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(CredentialNotExpiredException.class)
    public ResponseEntity<?> handleCredentialNotExpiredException(CredentialNotExpiredException ex) {
        logger.error("Credential not expired: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(CredentialNotFoundException.class)
    public ResponseEntity<?> handleCredentialNotFoundException(CredentialNotFoundException ex) {
        logger.error("Credential not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<Object> handleValidationExceptions(MethodArgumentNotValidException ex) {
        var messages = ex.getFieldErrors().stream().map(FieldError::getDefaultMessage).toList();
        return ResponseEntity.badRequest().body(messages.stream().reduce((a, b) -> a + ", " + b).orElse(""));
    }

    @ExceptionHandler(AccessDeniedException.class)
    protected ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex) {
        logger.error("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
    }

    @ExceptionHandler({org.springframework.web.bind.ServletRequestBindingException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    protected ResponseEntity<Object> handleMalformedRequest(Exception ex) {
        return ResponseEntity.badRequest().body("Missing or invalid request parameters");
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    protected ResponseEntity<Object> handleMissingRoute(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not found");
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotAcceptableException.class)
    protected ResponseEntity<Object> handleUnsupportedResponseType(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    @ExceptionHandler({org.springframework.dao.DataAccessResourceFailureException.class,
            org.springframework.dao.QueryTimeoutException.class})
    protected ResponseEntity<Object> handleUnavailableInfrastructure(Exception ex) {
        logger.warn("Infrastructure unavailable in request handler: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "5").header("Cache-Control", "no-store")
                .body(com.chellavignesh.authserver.filter.InfrastructureAvailabilityFilter.MESSAGE);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    protected ResponseEntity<Object> handleUploadTooLarge(Exception ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .header("Cache-Control", "no-store").body(new com.chellavignesh.authserver.adminportal.util.PortalProblem(
                        "UPLOAD_TOO_LARGE", "Certificate upload exceeds the 1 MiB limit", java.util.UUID.randomUUID().toString(), false));
    }

    /** Downstream provider sync is a dependency failure, never a client input rejection. */
    @ExceptionHandler(AccountSyncException.class)
    protected ResponseEntity<Object> handleAccountSyncException(AccountSyncException ex, HttpServletRequest request) {
        logger.error("Account sync dependency unavailable in request handler", ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .header("Cache-Control", "no-store")
                .body(new com.chellavignesh.authserver.adminportal.util.PortalProblem("UPSTREAM_FAILURE",
                        "The identity provider could not complete the account sync.",
                        com.chellavignesh.authserver.adminportal.util.PortalResponses.requestId(request), false));
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<Object> handleOtherException(Exception ex) {
        logger.error("Unhandled request exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("The request could not be completed");
    }

    @ExceptionHandler(value = InvalidUserSessionException.class)
    public String handleInvalidUserSessionException(InvalidUserSessionException ex) {
        logger.error("Invalid user session: {}", ex.getMessage());
        return "pages/404";
    }

}
