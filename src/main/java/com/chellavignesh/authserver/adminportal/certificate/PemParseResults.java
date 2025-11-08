package com.chellavignesh.authserver.adminportal.certificate;

import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.List;

public record PemParseResults(List<Certificate> certificateList, PrivateKey privateKey) {
}
