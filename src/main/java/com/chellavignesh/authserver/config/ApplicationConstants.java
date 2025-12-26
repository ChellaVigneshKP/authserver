package com.chellavignesh.authserver.config;

public class ApplicationConstants {
    private  ApplicationConstants() {
        // prevent instantiation
    }
    public static final String AUTH_SESSION_ID = "AUTH_SESSION_ID";
    public static final String CLIENT_FINGERPRINT = "client_fingerprint";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String REQUEST_SIGNATURE_HEADER = "x-signature";
    public static final String REQUEST_VERB_HEADER = "x-request-verb";
    public static final String REQUEST_DATETIME_HEADER = "x-request-datetime";
    public static final String REQUEST_ID_HEADER = "x-request-id";
    public static final String HEX_EXTERNAL_ID = "hex-external-id";
    public static final String BRANDING_INFO = "branding";
    public static final String CLIENT_ID = "CLIENT_ID";
    public static final String SSO_COOKIE_NAME = "ags-idp-session";
    public static final String USERNAME_LOOKUP_PRIORITY = "username-lookup-priority";
    public static final String LAST_LOGIN = "last-login";
    public static final String SERVLET_CONTEXT_PATH = "server.servlet.context-path";
    public static final String BIOMETRIC_TYPE = "biometricType";
    public static final String BIOMETRIC_TOKEN = "biometricToken";
    public static final String BIOMETRIC_DEV_ID = "deviceUuid";
    public static final String BIOMETRIC_AUTH_ERROR = "biometricAuthError";
    public static final String BIOMETRIC_GENERIC_ERROR = "biometricGenericError";
    public static final String BIOMETRIC_INVALID_CREDENTIALS = "biometricInvalidCredentials";
    public static final String P_VALUE = "p";
    public static final String LOGIN_PAGE_PATH = "/login";
    public static final String LOGIN_FORM_USERNAME_PARAMETER = "username";
    public static final String SOURCE_RELATIVE_PATH_PARAMETER = "surl";
    public static final String METADATA_PARAMETER = "mt";
    public static final String CLIENT_ID_SHORT_PARAMETER = "cid";
    public static final String EXTERNAL_ID_SHORT_PARAMETER = "eid";
    public static final String ERROR_CODE_PARAMETER = "error_code";
    public static final String EXTERNAL_AUTH_ERROR_CODE = "externalAuthErrorCode";
}
