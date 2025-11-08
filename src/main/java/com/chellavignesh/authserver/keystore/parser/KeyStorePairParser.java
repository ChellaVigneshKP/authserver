package com.chellavignesh.authserver.keystore.parser;

import com.chellavignesh.authserver.adminportal.certificate.exception.InvalidFileException;
import com.chellavignesh.authserver.keystore.KeyStorePair;
import com.chellavignesh.authserver.keystore.exception.FailedToCreateKeyStorePairException;

import java.io.InputStream;

public interface KeyStorePairParser {
    KeyStorePair parse(InputStream file, String password) throws FailedToCreateKeyStorePairException, InvalidFileException;
}
