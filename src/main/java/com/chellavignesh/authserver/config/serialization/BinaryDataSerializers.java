package com.chellavignesh.authserver.config.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Base64;

@Slf4j
public class BinaryDataSerializers {


    /**
     * Custom serializer for byte arrays to Base64 strings.
     * Used to safely store binary data in Redis JSON cache.
     */
    public static class ByteArrayBase64Serializer extends JsonSerializer<byte[]> {

        @Override
        public void serialize(byte[] value, JsonGenerator gen, SerializerProvider serializers) throws IOException {

            if (value == null) {
                gen.writeNull();
            } else {
                try {
                    // Encode binary data as Base64 string for safe JSON storage
                    String base64Value = Base64.getEncoder().encodeToString(value);
                    gen.writeString(base64Value);

                    // Log performance metrics in debug mode
                    if (log.isTraceEnabled()) {
                        log.trace("🧩 Serialized byte array: {} bytes -> {} Base64 chars", value.length, base64Value.length());
                    }
                } catch (Exception e) {
                    log.error("❌ Failed to serialize byte array to Base64: {}", e.getMessage());
                    gen.writeNull();
                }
            }
        }

        @Override
        public Class<byte[]> handledType() {
            return byte[].class;
        }
    }

    /**
     * Custom deserializer for Base64 strings to byte arrays.
     */
    public static class ByteArrayBase64Deserializer extends JsonDeserializer<byte[]> {

        @Override
        public byte[] deserialize(JsonParser parser, DeserializationContext context) throws IOException {

            String base64Value = parser.getValueAsString();

            if (base64Value == null) {
                return null;
            }

            // Handle empty string as empty byte array
            if (base64Value.trim().isEmpty()) {
                return new byte[0];
            }

            try {
                // Decode Base64 string back to binary data
                byte[] decodedBytes = Base64.getDecoder().decode(base64Value);

                // Log performance metrics in debug mode
                if (log.isTraceEnabled()) {
                    log.trace("🧩 Deserialized Base64: {} chars -> {} bytes", base64Value.length(), decodedBytes.length);
                }

                return decodedBytes;
            } catch (IllegalArgumentException e) {
                log.error("❌ Failed to deserialize Base64 to byte array: {} - Value: {}", e.getMessage(), base64Value);
                return null;
            } catch (Exception e) {
                log.error("❌ Unexpected error during Base64 deserialization: {}", e.getMessage());
                return null;
            }
        }

        @Override
        public Class<byte[]> handledType() {
            return byte[].class;
        }
    }

    /**
     * Validates that binary data can be safely serialized and deserialized.
     * Used for testing and validation purposes.
     *
     * @param originalData the original byte array
     * @return true if round-trip serialization works correctly
     */
    public static boolean validateBinarySerialization(byte[] originalData) {

        if (originalData == null) {
            return true; // null handling is valid
        }

        try {
            // Test round-trip: bytes -> Base64 -> bytes
            String base64 = Base64.getEncoder().encodeToString(originalData);
            byte[] restored = Base64.getDecoder().decode(base64);

            // Verify data integrity
            if (originalData.length != restored.length) {
                log.error("❌ Binary serialization validation failed: length mismatch");
                return false;
            }

            for (int i = 0; i < originalData.length; i++) {
                if (originalData[i] != restored[i]) {
                    log.error("❌ Binary serialization validation failed: data corruption at byte {}", i);
                    return false;
                }
            }

            log.debug("✅ Binary serialization validation passed for {} bytes", originalData.length);
            return true;

        } catch (Exception e) {
            log.error("❌ Binary serialization validation error: {}", e.getMessage());
            return false;
        }
    }
}
