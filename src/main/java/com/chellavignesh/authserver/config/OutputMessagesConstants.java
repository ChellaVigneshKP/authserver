package com.chellavignesh.authserver.config;

public class OutputMessagesConstants {

    private OutputMessagesConstants() {
        /*
         * Private constructor to prevent instantiation of this class.
         */
    }
    // Constants for password service validations
    public static final String INVALID_PASSWORD_USAGE = "Password cannot include first name, last name, username or email.";
    public static final String PASSWORD_BLACKLISTED = "This password cannot be used.";
    public static final String PASSWORD_RECENT_USAGE = "Password cannot be one of your previous 12 passwords.";
    public static final String CONFIRM_PASSWORD_NOT_MATCH = "Entered passwords do not match.";

    // Constants for invalid syntax
    public static final String INVALID_PASSWORD_SYNTAX = "Password must be a minimum of 8 characters long and include 3 of the following: lower characters, upper characters, special characters, numbers.";
    public static final String INVALID_PHONE_SYNTAX = "Incorrect phone number format.";
    public static final String INVALID_EMAIL_SYNTAX = "Incorrect email format.";

    // Constants for basic annotation validations
    public static final String REQUIRED_FIRSTNAME = "First name is required.";
    public static final String REQUIRED_LASTNAME = "Last name is required.";
    public static final String REQUIRED_USERNAME = "Username is required.";
    public static final String REQUIRED_PASSWORD = "Password is required.";
    public static final String REQUIRED_CONFIRM_PASSWORD = "Confirmation Password is required.";
    public static final String REQUIRED_EMAIL = "Email is required.";
    public static final String REQUIRED_PHONE_NUMBER = "Phone number is required.";
    public static final String REQUIRED_ORG_ID = "Org Id is required.";
    public static final String REQUIRED_BRANDING = "Branding is required.";
    public static final String REQUIRED_VALIDATION_TYPE = "Validation type is required.";
    public static final String REQUIRED_INTENT = "Intent is required.";
    public static final String REQUIRED_METADATA = "Metadata is required.";
    public static final String INVALID_VALIDATION_TYPE = "Validation type must be syntactic or semantic.";
}
