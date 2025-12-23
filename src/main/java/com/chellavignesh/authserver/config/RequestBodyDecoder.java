package com.chellavignesh.authserver.config;

import com.chellavignesh.authserver.config.exception.RequestBodyDecodeFailureException;
import org.apache.commons.lang3.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class RequestBodyDecoder {

    private RequestBodyDecoder() {
        // private constructor to prevent instantiation
    }

    static final String RES_URN = "res_urn";

    public static Map<String, String> decode(String encodedData) throws RequestBodyDecodeFailureException {

        var ret = new HashMap<String, String>();

        if (StringUtils.isEmpty(encodedData)) {
            return ret;
        }

        String[] entries = encodedData.split("&");

        for (String entry : entries) {

            String[] parts = entry.split("=");

            if (parts.length != 2) {
                if (parts[0].equals(RES_URN)) {
                    String[] extendedArray = Arrays.copyOf(parts, parts.length + 1);
                    extendedArray[1] = "";

                    // overwrite the parts array with the extended array
                    parts = extendedArray;
                } else {
                    throw new RequestBodyDecodeFailureException("Failed to decode URL encoded request body.");
                }
            }

            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);

            ret.put(key, value);
        }

        return ret;
    }
}