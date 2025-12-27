package com.chellavignesh.authserver.controller;


import com.chellavignesh.authserver.token.SignatureService;
import com.chellavignesh.authserver.token.exception.SignatureVerificationFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Controller
public class SignatureController {

    private final SignatureService signatureService;

    @Autowired
    public SignatureController(SignatureService signatureService) {
        this.signatureService = signatureService;
    }

    @PostMapping(value = "/oauth2/signature/verify", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Boolean> validateBody(@RequestParam final String clientId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final Date requestTimeDate, @RequestParam final String signature, @RequestParam final String body) {

        log.debug("ClientID: {}, signature: {}, requestTimeDate: {}", clientId, signature, requestTimeDate);
        log.trace("Decoded body is: {}", body);

        final var decodedBody = body.getBytes(StandardCharsets.UTF_8);
        var result = false;

        try {
            result = signatureService.verifySignatureForClientId(clientId, requestTimeDate, signature, decodedBody);
        } catch (SignatureVerificationFailedException svfe) {
            log.error("Unable to verify signature for clientId: {}, requestDateTime: {}, signature: {}, exception occurred", clientId, requestTimeDate, signature, svfe);
        }

        log.debug("For ClientID: {}, signature: {}, requestDateTime: {}, result is: {}", clientId, signature, requestTimeDate, result);

        return ResponseEntity.ok(result);
    }
}

