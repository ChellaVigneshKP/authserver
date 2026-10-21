package com.chellavignesh.authserver.adminportal.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/** Shared response boundary for portal routes, including failures before MVC dispatch. */
public final class PortalResponses {
    private static final Pattern REQUEST_ID=Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final String ATTRIBUTE=PortalResponses.class.getName()+".requestId";
    private static final ObjectMapper JSON=new ObjectMapper();
    private PortalResponses() {}

    public static boolean isPortalRequest(HttpServletRequest request) {
        var path=request.getServletPath();
        return path.equals("/api/v1/portal") || path.startsWith("/api/v1/portal/");
    }

    public static String requestId(HttpServletRequest request) {
        if (request.getAttribute(ATTRIBUTE) instanceof String id) return id;
        String supplied=request.getHeader("x-request-id");
        String id=supplied!=null && REQUEST_ID.matcher(supplied).matches() ? supplied : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE,id);
        return id;
    }

    public static void writeProblem(HttpServletRequest request,HttpServletResponse response,int status) throws IOException {
        String code=switch(status) {
            case 400 -> "INVALID_REQUEST"; case 401 -> "UNAUTHORIZED"; case 403 -> "ACCESS_DENIED";
            case 404 -> "NOT_FOUND"; case 405 -> "METHOD_NOT_ALLOWED"; case 503 -> "SERVICE_UNAVAILABLE";
            default -> "REQUEST_FAILED";
        };
        String message=switch(status) {
            case 400 -> "Missing or invalid request parameters or proof";
            case 401 -> "A valid bearer token is required"; case 403 -> "Access denied";
            case 404 -> "Not found"; case 503 -> "Service temporarily unavailable. Please try again shortly.";
            default -> "The request could not be completed";
        };
        response.resetBuffer();
        response.setStatus(status);
        response.setHeader("Cache-Control","no-store");
        response.setHeader("x-request-id",requestId(request));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        if(status==503) response.setHeader("Retry-After","5");
        JSON.writeValue(response.getOutputStream(),new PortalProblem(code,message,requestId(request),status==503));
    }
}
