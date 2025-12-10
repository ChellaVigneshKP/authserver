package com.chellavignesh.authserver.adminportal.util;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class ResponseBuilder {

    public static ResponseEntity<?> buildErrorResponse(String message, HttpStatus status) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("ok", false);
        errorResponse.put("error", message);
        return new ResponseEntity<>(errorResponse, status);
    }
}
