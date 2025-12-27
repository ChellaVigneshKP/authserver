package com.chellavignesh.authserver.controller;


import java.util.Map;
import java.util.UUID;

import com.chellavignesh.authserver.adminportal.organization.exception.OrgNotFoundException;
import com.chellavignesh.authserver.jwk.JWKService;
import com.nimbusds.jose.jwk.JWKSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@Controller
public class JWKSetController {

    private final JWKService jwkService;

    @Autowired
    public JWKSetController(JWKService jwkService) {
        this.jwkService = jwkService;
    }

    @GetMapping("/oauth2/.well-known/organizations/{orgId}/jwks.json")
    public ResponseEntity<Map<String, Object>> getOrgJwks(@PathVariable UUID orgId) throws OrgNotFoundException {

        try {
            return ResponseEntity.ok(new JWKSet(jwkService.getByOrgGuid(orgId)).toJSONObject());
        } catch (OrgNotFoundException e) {
            log.error("Org %s not found {}", orgId);
            throw e;
        }
    }

    @ExceptionHandler({OrgNotFoundException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> handleOrgNotFoundException() {
        return ResponseEntity.badRequest().build();
    }
}
