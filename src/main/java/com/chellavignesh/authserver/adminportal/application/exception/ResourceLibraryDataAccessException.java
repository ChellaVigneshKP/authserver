package com.chellavignesh.authserver.adminportal.application.exception;

public class ResourceLibraryDataAccessException extends Exception {
    public ResourceLibraryDataAccessException(String message, Exception ex) {
        super(message, ex);
    }
}
