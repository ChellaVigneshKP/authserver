package com.chellavignesh.authserver.adminportal.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.Base64;

public class Base64StringDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String value = parser.getText();

        if (value == null) return null;

        try {
            return new String(Base64.getDecoder().decode(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalBase64StringException(parser.getCurrentName() + " is Invalid Base64 String");
        }
    }
}
