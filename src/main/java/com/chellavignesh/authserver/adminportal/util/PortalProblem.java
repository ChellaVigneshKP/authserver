package com.chellavignesh.authserver.adminportal.util;

public record PortalProblem(String code, String message, String requestId, boolean retryable) {}
