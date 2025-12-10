package com.chellavignesh.authserver.adminportal.util;


import com.chellavignesh.libcrypto.dto.BaseRequestObject;
import com.chellavignesh.libcrypto.exception.BadRequestException;
import com.chellavignesh.libcrypto.service.impl.SchemaParserService;
import org.springframework.validation.FieldError;

public class BaseRequestPayloadParser {

    /**
     * @param <T>         - Type class for the payload object.
     * @param requestJson - BaseRequestObject JSON for this request (the request body).
     * @param objectClass - The payload object class to parse and return.
     * @return The payload for the schema within the given BaseRequestObject JSON.
     */
    public static <T> BaseRequestObject parsePayload(String requestJson, Class<T> objectClass) {
        BaseRequestObject base;
        try {
            base = SchemaParserService.parseBaseRequestWithPayload(requestJson, objectClass).orElseThrow();
        } catch (Exception e) {
            throw new BadRequestException(new FieldError(BaseRequestObject.class.getSimpleName(),
                    "REQUEST_BODY",
                    "Request JSON is not a valid BaseRequestObject"));
        }

        @SuppressWarnings("unchecked")
        T payload = (T) base.getPayload();
        if (payload == null) {
            throw new BadRequestException(new FieldError(objectClass.getSimpleName(), base.getSchema() + "_PAYLOAD",
                    "Request JSON is missing a payload for schema: " + base.getSchema()));
        }

        return base;
    }
}
