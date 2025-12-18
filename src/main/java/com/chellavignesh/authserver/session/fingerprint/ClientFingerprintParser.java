package com.chellavignesh.authserver.session.fingerprint;

import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class ClientFingerprintParser {

    private ClientFingerprintParser() {
    }

    /**
     * Parses the host from the value of a Referer header.
     *
     * @param referer The URL obtained from the Referer header
     * @return The host component from the given URL, or null if the URL cannot be parsed
     */
    public static String parseRefererHost(String referer) {
        try {
            var builder = UriComponentsBuilder.fromHttpUrl(referer);
            UriComponents uriComponents = builder.build();
            return uriComponents.getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses the timezone offset from the given ISO-8601 timestamp.
     *
     * @param datetime An ISO-8601 timestamp
     * @return The timezone offset parsed from the timestamp, or null if the timestamp cannot be parsed
     */
    public static ZoneOffset parseZoneOffset(String datetime) {
        try {
            return OffsetDateTime.parse(datetime).getOffset();
        } catch (DateTimeException | NullPointerException e) {
            return null;
        }
    }
}
